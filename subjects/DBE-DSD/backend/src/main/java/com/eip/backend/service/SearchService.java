package com.eip.backend.service;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.dto.search.SearchHit;
import com.eip.backend.dto.search.SearchMode;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.exception.QdrantUnavailableException;
import com.eip.backend.exception.ServiceUnavailableException;
import com.eip.backend.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified search across PostgreSQL, MongoDB and Qdrant.
 *
 * <p>Phase 1.7A built the fusion: keyword (PostgreSQL) and vector (Qdrant)
 * candidates fused on document id, filtered to what the caller may see, ranked
 * and paged. Phase 1.7B added server-side query embedding. Phase 1.7C upgrades
 * the keyword leg with the DSA-3 TextHack engine:
 *
 * <ul>
 *   <li>Keyword candidates are scored by {@link LexicalScorer} -- phrase
 *       detection, term coverage and typo tolerance -- instead of the 1.7A
 *       placeholder.</li>
 *   <li>A TextHack term scan recovers documents the SQL phrase match cannot see:
 *       query terms in a different order, or with a typo.</li>
 * </ul>
 *
 * <p>Both keyword paths report under the single {@code KEYWORD} source, because
 * they are one lexical signal. Per-hit provenance says when typo tolerance was
 * needed: such hits carry {@code FUZZY} in {@code matchedBy}.
 *
 * <p>{@link SearchMode} selects the legs: HYBRID runs both (typo-tolerant),
 * KEYWORD runs the keyword leg on exact terms, FUZZY runs the keyword leg with
 * typo tolerance, and SEMANTIC runs the vector leg alone.
 */
@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    /** Candidates pulled from each backend before fusion. */
    private static final int CANDIDATE_LIMIT = 200;
    private static final int MAX_PAGE_SIZE = 100;

    // ponytail: the TextHack scan reads every document passing the filters, up to
    // this cap, and scores it in memory. That is milliseconds for the fixture and
    // demo corpora, but linear in corpus size. Past this scale, move candidate
    // generation into the database (pg_trgm similarity) or a token index, and keep
    // LexicalScorer for ranking only.
    private static final int SCAN_LIMIT = 5000;

    /*
     * Qdrant returns the nearest chunks for any query, related or not, so a hit
     * found by meaning alone must clear both bars below. Calibrated for MiniLM:
     * on the demo corpus relevant documents scored cosine 0.39-0.68, but against
     * long uploaded PDFs a short query on a topic the document covers can score
     * as low as 0.22 ("security" in the DB report). Nonsense stays near zero
     * ("chocolate cake recipe": 0.09 on the demo corpus, 0.05 on the PDFs), so
     * the absolute floor sits at 0.20 and the relative cut does the trimming. It
     * is relative to the best match the caller can see, so a restricted document
     * never decides what else is shown. Tune both here if the model changes.
     */
    static final double MIN_VECTOR_SIMILARITY = 0.20;
    static final double MAX_VECTOR_GAP = 0.15;

    private final DocumentRepository documentRepository;
    private final QdrantService qdrantService;
    private final DocumentAccessService documentAccessService;
    private final EmbeddingService embeddingService;
    private final LexicalScorer lexicalScorer;
    private final BodyTextMatcher bodyTextMatcher;
    private final QueryCorrector queryCorrector;

    public SearchService(DocumentRepository documentRepository,
                         QdrantService qdrantService,
                         DocumentAccessService documentAccessService,
                         EmbeddingService embeddingService,
                         LexicalScorer lexicalScorer,
                         BodyTextMatcher bodyTextMatcher,
                         QueryCorrector queryCorrector) {
        this.documentRepository = documentRepository;
        this.qdrantService = qdrantService;
        this.documentAccessService = documentAccessService;
        this.embeddingService = embeddingService;
        this.lexicalScorer = lexicalScorer;
        this.bodyTextMatcher = bodyTextMatcher;
        this.queryCorrector = queryCorrector;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(SearchRequest request) {
        validate(request);
        SearchMode mode = request.getMode();

        // Words no document contains are corrected to the nearest word one does,
        // except in KEYWORD mode, which promises exact terms.
        String query = request.getQuery();
        double typoCredit = 1.0;
        if (request.hasQuery() && mode != SearchMode.KEYWORD) {
            QueryCorrector.Correction correction = correct(query);
            if (correction != null) {
                query = correction.query();
                typoCredit = correction.credit();
            }
        }

        Map<Integer, SearchHit> hits = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();

        boolean keywordRan = false;
        if (request.hasQuery() && mode.usesKeyword()) {
            collectKeywordHits(query, request, hits, mode != SearchMode.KEYWORD, typoCredit);
            keywordRan = true;
            sources.add("KEYWORD");
        }

        // Server-side query embedding. Only these vectors have the MiniLM similarity
        // scale the relevance floor is calibrated for; a caller's own vector does not.
        boolean embeddedHere = false;
        if (request.hasQuery() && mode.usesVector() && !request.hasVector()) {
            List<Float> generatedVector = embeddingService.embedQuery(query);
            if (generatedVector != null) {
                request.setVector(generatedVector);
                embeddedHere = true;
            }
        }
        if (mode == SearchMode.SEMANTIC && !request.hasVector()) {
            // Hybrid falls back to its keyword results; semantic alone has nothing to fall back to.
            throw new ServiceUnavailableException("Semantic search is unavailable: the embedding model is not loaded");
        }

        boolean vectorRan = false;
        if (request.hasVector() && mode.usesVector()) {
            vectorRan = collectVectorHits(request, hits, keywordRan);
            if (vectorRan) {
                sources.add("VECTOR");
            }
        }

        // One permission query for the whole candidate set, not one per hit.
        Set<Integer> readable = documentAccessService.readableIds(hits.keySet());
        hits.keySet().retainAll(readable);

        // Both checks run on PostgreSQL's view of each document, so they hold for
        // hits from either leg. A hit that did not hydrate has no document behind
        // it (a chunk outliving its document); Qdrant cannot filter on status.
        Set<Integer> present = hydrate(hits);
        hits.keySet().retainAll(present);
        String status = blankIfNull(request.getStatus());
        if (!status.isEmpty()) {
            hits.values().removeIf(hit -> !status.equals(hit.getStatus()));
        }
        if (vectorRan && embeddedHere) {
            dropWeakVectorOnlyHits(hits);
        }

        List<SearchHit> ranked = new ArrayList<>(hits.values());
        for (SearchHit hit : ranked) {
            hit.setScore(combinedScore(hit.getKeywordScore(), hit.getVectorScore(), embeddedHere));
        }
        ranked.sort(Comparator.comparingDouble(SearchHit::getScore).reversed()
                .thenComparing(SearchHit::getDocumentId));

        return paginate(ranked, request, sources);
    }

    private void validate(SearchRequest request) {
        if (request == null || (!request.hasQuery() && !request.hasVector())) {
            throw new IllegalArgumentException("Provide a query, a vector, or both");
        }
        if (request.getPage() < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!request.hasQuery() && !request.getMode().usesVector()) {
            throw new IllegalArgumentException(request.getMode() + " search needs a query");
        }
    }

    /**
     * The keyword leg, in two passes feeding one signal.
     *
     * <ol>
     *   <li>PostgreSQL phrase candidates ({@code ILIKE}). Not bound by the scan
     *       cap, so phrase matches are found across the whole table.</li>
     *   <li>The TextHack scan, adding what the SQL phrase match cannot express:
     *       reordered terms, partly typed words and misspellings.</li>
     * </ol>
     *
     * <p>Either way a document is kept only when it matches enough of the query's
     * terms ({@link LexicalScorer#coversEnough}): a document sharing one incidental
     * word with a long query is not a hit, and neither is one where the query
     * appears only inside a longer word ("port" in "report").
     *
     * <p>{@code typoCredit} is below 1 when the query was corrected: every hit is
     * then a typo-level match, discounted and marked FUZZY.
     */
    private void collectKeywordHits(String query, SearchRequest request, Map<Integer, SearchHit> hits,
                                    boolean allowFuzzy, double typoCredit) {
        query = query.trim();
        String category = blankIfNull(request.getCategory());
        String status = blankIfNull(request.getStatus());
        Map<Integer, LexicalScorer.BodyEvidence> bodies = bodyMatches(query, allowFuzzy);
        Set<Integer> scored = new HashSet<>();

        for (Document document : documentRepository.searchByKeyword(
                query, category, status, PageRequest.of(0, CANDIDATE_LIMIT))) {
            scoreKeywordCandidate(hits, scored, document, query, bodies, allowFuzzy, typoCredit);
        }
        for (Document document : documentRepository.findForLexicalScan(
                category, status, PageRequest.of(0, SCAN_LIMIT))) {
            scoreKeywordCandidate(hits, scored, document, query, bodies, allowFuzzy, typoCredit);
        }
    }

    private void scoreKeywordCandidate(Map<Integer, SearchHit> hits, Set<Integer> scored, Document document,
                                       String query, Map<Integer, LexicalScorer.BodyEvidence> bodies,
                                       boolean allowFuzzy, double typoCredit) {
        if (!scored.add(document.getId())) {
            return; // already scored identically by the phrase pass
        }
        LexicalScorer.Match match = lexicalScorer.score(query, document.getTitle(), document.getDescription(),
                                                        bodies.get(document.getId()), allowFuzzy);
        if (LexicalScorer.coversEnough(match)) {
            addKeywordHit(hits, document, match, typoCredit);
        }
    }

    /** The corrected query, or null. If correction fails, search runs on what was typed. */
    private QueryCorrector.Correction correct(String query) {
        try {
            return queryCorrector.correct(query);
        } catch (RuntimeException e) {
            logger.warn("Query correction unavailable, searching for the words as typed: {}", e.getMessage());
            return null;
        }
    }

    /** Body matches from MongoDB. If MongoDB is down, search still answers from titles and descriptions. */
    private Map<Integer, LexicalScorer.BodyEvidence> bodyMatches(String query, boolean allowPrefix) {
        String phrase = LexicalScorer.normalise(query);
        try {
            return bodyTextMatcher.match(phrase, LexicalScorer.terms(phrase), allowPrefix);
        } catch (RuntimeException e) {
            logger.warn("Body text matching unavailable, searching titles and descriptions only: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Removes hits found by meaning alone that fall below the relevance floor
     * (see {@link #MIN_VECTOR_SIMILARITY}). Runs after permission filtering. Hits
     * with keyword evidence stay whatever their vector score.
     */
    private static void dropWeakVectorOnlyHits(Map<Integer, SearchHit> hits) {
        double best = hits.values().stream()
                .filter(hit -> hit.getVectorScore() != null)
                .mapToDouble(SearchHit::getVectorScore)
                .max().orElse(MIN_VECTOR_SIMILARITY);
        double floor = Math.max(MIN_VECTOR_SIMILARITY, best - MAX_VECTOR_GAP);
        hits.values().removeIf(hit -> hit.getKeywordScore() == null
                && hit.getVectorScore() != null && hit.getVectorScore() < floor);
    }

    private static void addKeywordHit(Map<Integer, SearchHit> hits, Document document,
                                      LexicalScorer.Match match, double typoCredit) {
        SearchHit hit = hits.computeIfAbsent(document.getId(), SearchService::newHit);
        hit.setKeywordScore(match.score() * typoCredit);
        hit.getMatchedBy().add("KEYWORD");
        if (match.fuzzy() || typoCredit < 1.0) {
            hit.getMatchedBy().add("FUZZY");
        }
    }

    /** @return true if Qdrant actually answered. */
    private boolean collectVectorHits(SearchRequest request, Map<Integer, SearchHit> hits, boolean keywordRan) {
        VectorSearchRequest vectorRequest = new VectorSearchRequest();
        vectorRequest.setVector(request.getVector());
        vectorRequest.setTopK(CANDIDATE_LIMIT);
        vectorRequest.setCategory(request.getCategory());
        vectorRequest.setDepartment(request.getDepartment());

        List<VectorSearchResultItem> items;
        try {
            items = qdrantService.search(vectorRequest).getResults();
        } catch (QdrantUnavailableException e) {
            // Vector search is one of several signals. If keyword results are
            // already in hand, degrade to those and say so in `sources` rather
            // than failing a search that can still answer usefully.
            if (keywordRan) {
                logger.warn("Vector search unavailable, returning keyword results only: {}", e.getMessage());
                return false;
            }
            throw e;
        }

        for (VectorSearchResultItem item : items) {
            Integer documentId = item.getPostgresDocumentId();
            if (documentId == null || item.getScore() == null) {
                continue;
            }
            SearchHit hit = hits.computeIfAbsent(documentId, SearchService::newHit);
            // Chunks of the same document arrive separately; the document scores
            // as its single best chunk.
            if (hit.getVectorScore() == null || item.getScore() > hit.getVectorScore()) {
                hit.setVectorScore(item.getScore().doubleValue());
                hit.setChunkId(item.getChunkId());
            }
            hit.getMatchedBy().add("VECTOR");
        }
        return true;
    }

    /**
     * Fills in the PostgreSQL columns for every hit.
     *
     * @return the ids that exist in PostgreSQL
     */
    private Set<Integer> hydrate(Map<Integer, SearchHit> hits) {
        Set<Integer> present = new HashSet<>();
        for (Document document : documentRepository.findAllById(hits.keySet())) {
            present.add(document.getId());
            SearchHit hit = hits.get(document.getId());
            hit.setTitle(document.getTitle());
            hit.setDescription(document.getDescription());
            hit.setStatus(document.getStatus());
            if (document.getCategory() != null) {
                hit.setCategory(document.getCategory().getName());
            }
            if (document.getOwner() != null) {
                hit.setOwner(document.getOwner().getUsername());
            }
        }
        return present;
    }

    /**
     * The score a user sees, 0..1. Each leg's score already reads as "how well this
     * matches": keyword is how completely the document contains what was typed,
     * meaning is the calibrated similarity. The result is the stronger of the two,
     * plus half the other's share of what remains, so an exact word in a document
     * shows 90-100% whatever its meaning score, and agreement between the legs
     * lifts a result without ever passing a perfect match.
     */
    static double combinedScore(Double keywordScore, Double vectorScore, boolean calibrated) {
        double keyword = keywordScore == null ? 0.0 : keywordScore;
        double meaning = vectorScore == null ? 0.0 : semanticScore(vectorScore, calibrated);
        double strong = Math.max(keyword, meaning);
        double weak = Math.min(keyword, meaning);
        return strong + 0.5 * weak * (1.0 - strong);
    }

    /**
     * Turns a cosine similarity into a 0..1 score. For the server's own MiniLM
     * embeddings the useful range is narrow: unrelated text sits near 0, relevant
     * documents at 0.3-0.7. That range is stretched to 0-0.95; meaning alone never
     * reaches 100%, which is kept for literal matches. A caller's own vector has an
     * unknown scale, so it is only mapped from [-1, 1] onto [0, 1]. Both transforms
     * are monotonic, so they never reorder vector results; `vectorScore` on the
     * response stays the raw value Qdrant reported.
     */
    static double semanticScore(double cosine, boolean calibrated) {
        double score = calibrated ? (cosine - 0.10) / 0.60 : (cosine + 1.0) / 2.0;
        return Math.max(0.0, Math.min(calibrated ? 0.95 : 1.0, score));
    }

    // ponytail: paging over the fused list in memory, capped at CANDIDATE_LIMIT
    // per backend. Deep pages past that cap are simply empty. Push ranking into
    // Postgres (tsvector + ts_rank) when the corpus outgrows one page of fusion.
    private SearchResponse paginate(List<SearchHit> ranked, SearchRequest request, List<String> sources) {
        int from = Math.min(request.getPage() * request.getSize(), ranked.size());
        int to = Math.min(from + request.getSize(), ranked.size());

        SearchResponse response = new SearchResponse();
        response.setHits(new ArrayList<>(ranked.subList(from, to)));
        response.setPage(request.getPage());
        response.setSize(request.getSize());
        response.setTotalHits(ranked.size());
        response.setSources(sources);
        return response;
    }

    private static SearchHit newHit(Integer documentId) {
        SearchHit hit = new SearchHit();
        hit.setDocumentId(documentId);
        return hit;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
