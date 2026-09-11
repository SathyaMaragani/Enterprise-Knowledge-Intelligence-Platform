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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1.7A -- unified search.
 *
 * Runs the keyword (PostgreSQL) and vector (Qdrant) primitives, fuses them on
 * document id, drops what the caller may not see, ranks, and pages. Fuzzy search
 * (1.7C / TextHack) and server-side embeddings (1.7B) plug in as extra candidate
 * sources; nothing else here has to move when they do.
 */
@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    /** Candidates pulled from each backend before fusion. */
    private static final int CANDIDATE_LIMIT = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private static final double KEYWORD_WEIGHT = 0.4;
    private static final double VECTOR_WEIGHT = 0.6;

    private final DocumentRepository documentRepository;
    private final QdrantService qdrantService;
    private final DocumentAccessService documentAccessService;

    public SearchService(DocumentRepository documentRepository,
                         QdrantService qdrantService,
                         DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.qdrantService = qdrantService;
        this.documentAccessService = documentAccessService;
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

        hydrate(hits);

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

    private void collectKeywordHits(SearchRequest request, Map<Integer, SearchHit> hits) {
        List<Document> matches = documentRepository.searchByKeyword(
                request.getQuery().trim(),
                blankIfNull(request.getCategory()),
                blankIfNull(request.getStatus()),
                PageRequest.of(0, CANDIDATE_LIMIT));

        String needle = request.getQuery().trim().toLowerCase();
        for (Document document : matches) {
            SearchHit hit = hits.computeIfAbsent(document.getId(), SearchService::newHit);
            hit.setKeywordScore(keywordScore(document, needle));
            hit.getMatchedBy().add("KEYWORD");
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

    /** Fills in the PostgreSQL columns for hits that only vector search found. */
    private void hydrate(Map<Integer, SearchHit> hits) {
        for (Document document : documentRepository.findAllById(hits.keySet())) {
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
    }

    // ponytail: substring presence, not relevance -- no term frequency, no field
    // length normalisation, no ranking of multi-term queries. Phase 1.7C replaces
    // this with the TextHack scorers; the shape (0..1 per document) stays.
    private double keywordScore(Document document, String needle) {
        String title = document.getTitle() == null ? "" : document.getTitle().toLowerCase();
        return title.contains(needle) ? 1.0 : 0.5;
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
