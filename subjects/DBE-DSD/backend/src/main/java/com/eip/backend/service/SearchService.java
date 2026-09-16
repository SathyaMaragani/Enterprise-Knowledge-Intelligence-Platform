package com.eip.backend.service;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.dto.search.SearchHit;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.exception.QdrantUnavailableException;
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

    private static final double KEYWORD_WEIGHT = 0.4;
    private static final double VECTOR_WEIGHT = 0.6;

    private final DocumentRepository documentRepository;
    private final QdrantService qdrantService;
    private final DocumentAccessService documentAccessService;
    private final EmbeddingService embeddingService;
    private final LexicalScorer lexicalScorer;

    public SearchService(DocumentRepository documentRepository,
                         QdrantService qdrantService,
                         DocumentAccessService documentAccessService,
                         EmbeddingService embeddingService,
                         LexicalScorer lexicalScorer) {
        this.documentRepository = documentRepository;
        this.qdrantService = qdrantService;
        this.documentAccessService = documentAccessService;
        this.embeddingService = embeddingService;
        this.lexicalScorer = lexicalScorer;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(SearchRequest request) {
        validate(request);

        Map<Integer, SearchHit> hits = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();

        boolean keywordRan = false;
        if (request.hasQuery()) {
            collectKeywordHits(request, hits);
            keywordRan = true;
            sources.add("KEYWORD");

            // Server-side query embedding
            if (!request.hasVector()) {
                List<Float> generatedVector = embeddingService.embedQuery(request.getQuery());
                if (generatedVector != null) {
                    request.setVector(generatedVector);
                }
            }
        }

        boolean vectorRan = false;
        if (request.hasVector()) {
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

        List<SearchHit> ranked = new ArrayList<>(hits.values());
        double activeWeight = (keywordRan ? KEYWORD_WEIGHT : 0) + (vectorRan ? VECTOR_WEIGHT : 0);
        for (SearchHit hit : ranked) {
            hit.setScore(combinedScore(hit, activeWeight));
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
    }

    /**
     * The keyword leg, in two passes feeding one signal.
     *
     * <ol>
     *   <li>PostgreSQL phrase candidates ({@code ILIKE}). Not bound by the scan
     *       cap, so phrase matches are found across the whole table. Always kept:
     *       they contain the query and so score at least
     *       {@value LexicalScorer#DESCRIPTION_PHRASE_SCORE}.</li>
     *   <li>The TextHack scan, adding what the SQL phrase match cannot express --
     *       reordered terms and near-miss spellings. Kept only above
     *       {@value LexicalScorer#MIN_SCAN_SCORE}, so a document sharing one
     *       incidental word with a long query does not become a hit.</li>
     * </ol>
     */
    private void collectKeywordHits(SearchRequest request, Map<Integer, SearchHit> hits) {
        String query = request.getQuery().trim();
        String category = blankIfNull(request.getCategory());
        String status = blankIfNull(request.getStatus());

        for (Document document : documentRepository.searchByKeyword(
                query, category, status, PageRequest.of(0, CANDIDATE_LIMIT))) {
            addKeywordHit(hits, document,
                          lexicalScorer.score(query, document.getTitle(), document.getDescription()));
        }

        for (Document document : documentRepository.findForLexicalScan(
                category, status, PageRequest.of(0, SCAN_LIMIT))) {
            if (hits.containsKey(document.getId())) {
                continue; // already scored identically by the phrase pass
            }
            LexicalScorer.Match match =
                    lexicalScorer.score(query, document.getTitle(), document.getDescription());
            if (match.score() >= LexicalScorer.MIN_SCAN_SCORE) {
                addKeywordHit(hits, document, match);
            }
        }
    }

    private static void addKeywordHit(Map<Integer, SearchHit> hits, Document document,
                                      LexicalScorer.Match match) {
        SearchHit hit = hits.computeIfAbsent(document.getId(), SearchService::newHit);
        hit.setKeywordScore(match.score());
        hit.getMatchedBy().add("KEYWORD");
        if (match.fuzzy()) {
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

    private double combinedScore(SearchHit hit, double activeWeight) {
        if (activeWeight <= 0) {
            return 0;
        }
        double total = 0;
        if (hit.getKeywordScore() != null) {
            total += KEYWORD_WEIGHT * hit.getKeywordScore();
        }
        if (hit.getVectorScore() != null) {
            total += VECTOR_WEIGHT * normaliseCosine(hit.getVectorScore());
        }
        return total / activeWeight;
    }

    /**
     * The knowledge_chunks collection uses cosine distance, so Qdrant returns
     * similarities in [-1, 1] -- orthogonal chunks score 0 and opposed chunks
     * score negative. Map that onto the [0, 1] the fused score promises. The
     * transform is monotonic, so it never reorders vector results; `vectorScore`
     * on the response stays the raw value Qdrant reported.
     */
    private static double normaliseCosine(double similarity) {
        double normalised = (similarity + 1.0) / 2.0;
        return Math.max(0.0, Math.min(1.0, normalised));
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
