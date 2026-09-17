package com.eip.backend.service;

import com.eip.backend.dto.qdrant.VectorSearchRequest;
import com.eip.backend.dto.qdrant.VectorSearchResponse;
import com.eip.backend.dto.qdrant.VectorSearchResultItem;
import com.eip.backend.dto.search.SearchHit;
import com.eip.backend.dto.search.SearchMode;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.entity.Document;
import com.eip.backend.exception.QdrantUnavailableException;
import com.eip.backend.exception.ServiceUnavailableException;
import com.eip.backend.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fusion, permission filtering, ranking and paging for Phase 1.7A -- exercised
 * without Postgres, Mongo or Qdrant running. The end-to-end path over the real
 * databases lives in EipApplicationTests.
 *
 * Stubs are hand-rolled rather than Mockito: Mockito's inline mock maker cannot
 * instrument classes on the JDK 25 this project builds with, and a JDK-neutral
 * test is worth more here than a mocking framework.
 */
class SearchServiceTest {

    private List<Document> keywordResults = List.of();
    /** What the Phase 1.7C TextHack scan sees; empty unless a test sets it. */
    private List<Document> scanResults = List.of();
    private VectorSearchResponse vectorResults = new VectorSearchResponse(List.of());
    private RuntimeException qdrantFailure;
    /** Ids the current user may read; null means "all of them". */
    private Set<Integer> readable;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        DocumentRepository repository = stubRepository();

        QdrantService qdrant = new QdrantService(null) {
            @Override
            public VectorSearchResponse search(VectorSearchRequest request) {
                if (qdrantFailure != null) {
                    throw qdrantFailure;
                }
                return vectorResults;
            }
        };

        DocumentAccessService access = new DocumentAccessService(repository) {
            @Override
            public Set<Integer> readableIds(Collection<Integer> documentIds) {
                if (readable == null) {
                    return Set.copyOf(documentIds);
                }
                Set<Integer> visible = new LinkedHashSet<>(documentIds);
                visible.retainAll(readable);
                return visible;
            }
        };

        EmbeddingService embeddingService = new EmbeddingService(null) {
            @Override
            public List<Float> embedQuery(String query) {
                return null;
            }
        };

        searchService = new SearchService(repository, qdrant, access, embeddingService,
                                          new LexicalScorer());
    }

    /**
     * DocumentRepository is a Spring Data interface with ~25 inherited methods;
     * a dynamic proxy answers the three this service actually calls and returns
     * empty for the rest, instead of 25 lines of unimplemented stubs.
     */
    private DocumentRepository stubRepository() {
        return (DocumentRepository) Proxy.newProxyInstance(
                DocumentRepository.class.getClassLoader(),
                new Class<?>[]{DocumentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "searchByKeyword" -> keywordResults;
                    case "findForLexicalScan" -> scanResults;
                    case "findAllById" -> {
                        Set<Integer> wanted = new LinkedHashSet<>();
                        ((Iterable<?>) args[0]).forEach(id -> wanted.add((Integer) id));
                        yield known.stream()
                                .filter(d -> wanted.contains(d.getId()))
                                .toList();
                    }
                    case "toString" -> "stubDocumentRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Every document the stubs know about, for hydration lookups. */
    private final List<Document> known = new ArrayList<>();

    private Document doc(int id, String title, String description) {
        Document d = new Document();
        d.setId(id);
        d.setTitle(title);
        d.setDescription(description);
        d.setStatus("PUBLISHED");
        known.add(d);
        return d;
    }

    private static VectorSearchResultItem vectorItem(int documentId, float score, String chunkId) {
        VectorSearchResultItem item = new VectorSearchResultItem();
        item.setPostgresDocumentId(documentId);
        item.setScore(score);
        item.setChunkId(chunkId);
        return item;
    }

    private void keywordReturns(Document... documents) {
        keywordResults = List.of(documents);
    }

    private void scanReturns(Document... documents) {
        scanResults = List.of(documents);
    }

    private void vectorReturns(VectorSearchResultItem... items) {
        vectorResults = new VectorSearchResponse(new ArrayList<>(List.of(items)));
    }

    private static SearchRequest request(String query, List<Float> vector) {
        SearchRequest r = new SearchRequest();
        r.setQuery(query);
        r.setVector(vector);
        return r;
    }

    @Test
    void requiresQueryOrVector() {
        assertThrows(IllegalArgumentException.class, () -> searchService.search(new SearchRequest()));
    }

    @Test
    void rejectsOutOfRangePaging() {
        SearchRequest tooBig = request("policy", null);
        tooBig.setSize(1000);
        assertThrows(IllegalArgumentException.class, () -> searchService.search(tooBig));

        SearchRequest negative = request("policy", null);
        negative.setPage(-1);
        assertThrows(IllegalArgumentException.class, () -> searchService.search(negative));
    }

    @Test
    void titleMatchOutranksDescriptionOnlyMatch() {
        Document inTitle = doc(1, "Leave Policy", "unrelated");
        Document inBody = doc(2, "Onboarding", "mentions the leave policy");
        keywordReturns(inTitle, inBody);

        SearchResponse response = searchService.search(request("leave policy", null));

        assertEquals(List.of("KEYWORD"), response.getSources());
        assertEquals(2, response.getTotalHits());
        assertEquals(1, response.getHits().get(0).getDocumentId());
        assertTrue(response.getHits().get(0).getScore() > response.getHits().get(1).getScore());
    }

    @Test
    void fusesKeywordAndVectorAndKeepsBestChunkPerDocument() {
        Document both = doc(1, "Leave Policy", "x");
        doc(2, "Sabbaticals", "y");
        keywordReturns(both);
        // Two chunks of document 1; the stronger one should win.
        vectorReturns(vectorItem(1, 0.6f, "chunk-a"),
                      vectorItem(1, 0.9f, "chunk-b"),
                      vectorItem(2, 0.8f, "chunk-c"));

        SearchResponse response = searchService.search(request("leave policy", List.of(0.1f, 0.2f)));

        assertEquals(List.of("KEYWORD", "VECTOR"), response.getSources());
        assertEquals(2, response.getTotalHits());

        SearchHit top = response.getHits().get(0);
        assertEquals(1, top.getDocumentId());
        assertEquals(Set.of("KEYWORD", "VECTOR"), top.getMatchedBy());
        assertEquals("chunk-b", top.getChunkId());
        // vectorScore stays the raw cosine Qdrant reported...
        assertEquals(0.9, top.getVectorScore(), 1e-6);
        // ...while fusion uses it normalised: 0.4*1.0 + 0.6*((0.9+1)/2).
        assertEquals(0.97, top.getScore(), 1e-6);

        SearchHit second = response.getHits().get(1);
        assertEquals(2, second.getDocumentId());
        assertEquals(Set.of("VECTOR"), second.getMatchedBy());
        assertEquals(0.54, second.getScore(), 1e-6); // 0.6*((0.8+1)/2)
    }

    @Test
    void negativeCosineSimilarityStillProducesANormalisedScore() {
        // Cosine distance runs -1..1, so Qdrant legitimately returns negatives
        // for chunks pointing away from the query. The fused score must stay
        // inside 0..1 and must not reorder the vector results.
        Document opposed = doc(1, "Opposed", "x");
        Document orthogonal = doc(2, "Orthogonal", "y");
        vectorReturns(vectorItem(1, -0.5f, "chunk-a"), vectorItem(2, 0.0f, "chunk-b"));

        SearchResponse response = searchService.search(request(null, List.of(0.1f, 0.2f)));

        assertEquals(2, response.getTotalHits());
        for (SearchHit hit : response.getHits()) {
            assertTrue(hit.getScore() >= 0.0 && hit.getScore() <= 1.0,
                    "Score must stay normalised but was " + hit.getScore());
        }
        // Orthogonal (0.0) still outranks opposed (-0.5).
        assertEquals(orthogonal.getId(), response.getHits().get(0).getDocumentId());
        assertEquals(opposed.getId(), response.getHits().get(1).getDocumentId());
        // Vector-only, so the weight normalises over VECTOR_WEIGHT alone:
        // (0.6 * normalised) / 0.6 == normalised.
        assertEquals(0.5, response.getHits().get(0).getScore(), 1e-6);  // (0.0+1)/2
        assertEquals(0.25, response.getHits().get(1).getScore(), 1e-6); // (-0.5+1)/2
    }

    @Test
    void keywordOnlySearchIsNotPenalisedForHavingNoVectorScore() {
        Document only = doc(1, "Leave Policy", "x");
        keywordReturns(only);

        SearchResponse response = searchService.search(request("leave policy", null));

        // Weight normalises over the backends that ran, so a perfect keyword
        // match scores 1.0 rather than 0.4.
        assertEquals(1.0, response.getHits().get(0).getScore(), 1e-6);
    }

    @Test
    void dropsDocumentsTheUserMayNotRead() {
        Document allowed = doc(1, "Leave Policy", "x");
        Document forbidden = doc(2, "Leave Policy Exec Addendum", "x");
        keywordReturns(allowed, forbidden);
        readable = Set.of(1);

        SearchResponse response = searchService.search(request("leave policy", null));

        assertEquals(1, response.getTotalHits());
        assertEquals(1, response.getHits().get(0).getDocumentId());
    }

    @Test
    void hidesVectorOnlyHitsTheUserMayNotRead() {
        doc(1, "Exec Comp Review", "x");
        vectorReturns(vectorItem(1, 0.99f, "chunk-a"));
        readable = Set.of();

        SearchResponse response = searchService.search(request(null, List.of(0.1f, 0.2f)));

        assertEquals(0, response.getTotalHits());
        assertTrue(response.getHits().isEmpty());
    }

    @Test
    void totalHitsCountsVisibleMatchesNotJustThePage() {
        Document[] all = new Document[5];
        for (int i = 0; i < 5; i++) {
            all[i] = doc(i + 1, "Policy " + i, "x");
        }
        keywordReturns(all);

        SearchRequest req = request("policy", null);
        req.setPage(1);
        req.setSize(2);
        SearchResponse response = searchService.search(req);

        assertEquals(5, response.getTotalHits());
        assertEquals(2, response.getHits().size());
        assertEquals(1, response.getPage());
    }

    @Test
    void statusFilterAlsoAppliesToVectorOnlyHits() {
        // Qdrant has no status payload filter, so without a check after hydration a
        // status-filtered search would return vector hits of any status.
        doc(1, "Draft Handbook", "x");                  // PUBLISHED
        doc(2, "Indexed Handbook", "y").setStatus("INDEXED");
        vectorReturns(vectorItem(1, 0.9f, "chunk-a"), vectorItem(2, 0.8f, "chunk-b"));

        SearchRequest req = request(null, List.of(0.1f, 0.2f));
        req.setStatus("INDEXED");
        SearchResponse response = searchService.search(req);

        assertEquals(1, response.getTotalHits());
        assertEquals(2, response.getHits().get(0).getDocumentId());
    }

    @Test
    void dropsVectorHitsForDocumentsMissingFromPostgres() {
        // A chunk can outlive its document, for example after a failed cleanup.
        // It has no title or owner to show, so it is not a result.
        doc(1, "Still Here", "x");
        vectorReturns(vectorItem(1, 0.7f, "chunk-a"), vectorItem(42, 0.95f, "chunk-orphan"));

        SearchResponse response = searchService.search(request(null, List.of(0.1f, 0.2f)));

        assertEquals(1, response.getTotalHits());
        assertEquals(1, response.getHits().get(0).getDocumentId());
    }

    @Test
    void pageBeyondTheEndIsEmptyRatherThanAnError() {
        Document only = doc(1, "Policy", "x");
        keywordReturns(only);

        SearchRequest req = request("policy", null);
        req.setPage(99);
        req.setSize(10);
        SearchResponse response = searchService.search(req);

        assertTrue(response.getHits().isEmpty());
        assertEquals(1, response.getTotalHits());
    }

    @Test
    void degradesToKeywordResultsWhenQdrantIsDown() {
        Document found = doc(1, "Leave Policy", "x");
        keywordReturns(found);
        qdrantFailure = new QdrantUnavailableException("connection refused");

        SearchResponse response = searchService.search(request("leave policy", List.of(0.1f, 0.2f)));

        assertEquals(List.of("KEYWORD"), response.getSources());
        assertEquals(1, response.getTotalHits());
    }

    @Test
    void surfacesQdrantOutageWhenVectorSearchWasTheOnlyRequest() {
        qdrantFailure = new QdrantUnavailableException("connection refused");

        assertThrows(QdrantUnavailableException.class,
                () -> searchService.search(request(null, List.of(0.1f, 0.2f))));
    }

    // ------------------------------------------------------------ Phase 1.7C

    @Test
    void typoQueryIsRecoveredByTheTextHackScan() {
        // The SQL phrase match finds nothing for a misspelling; the scan does.
        Document policy = doc(9, "Leave Policy Update", "Changes to leave policy");
        scanReturns(policy);

        SearchResponse response = searchService.search(request("levae policy", null));

        assertEquals(List.of("KEYWORD"), response.getSources());
        assertEquals(1, response.getTotalHits());
        SearchHit hit = response.getHits().get(0);
        assertEquals(9, hit.getDocumentId());
        assertEquals(Set.of("KEYWORD", "FUZZY"), hit.getMatchedBy());
        assertTrue(hit.getKeywordScore() >= LexicalScorer.MIN_SCAN_SCORE);
    }

    @Test
    void reorderedTermsAreRecoveredWithoutBeingMarkedFuzzy() {
        Document policy = doc(9, "Leave Policy Update", "Changes to leave policy");
        scanReturns(policy);

        SearchResponse response = searchService.search(request("policy leave", null));

        SearchHit hit = response.getHits().get(0);
        assertEquals(Set.of("KEYWORD"), hit.getMatchedBy());
        assertEquals(LexicalScorer.COVERAGE_CEILING, hit.getKeywordScore(), 1e-9);
    }

    @Test
    void documentFoundByBothKeywordPathsIsCountedOnceAtItsPhraseScore() {
        Document policy = doc(9, "Leave Policy", "x");
        keywordReturns(policy);
        scanReturns(policy);

        SearchResponse response = searchService.search(request("leave policy", null));

        assertEquals(1, response.getTotalHits());
        assertEquals(1.0, response.getHits().get(0).getScore(), 1e-9);
    }

    @Test
    void weakScanMatchesAreNotHits() {
        // One of three terms is below the inclusion threshold.
        Document notes = doc(3, "Update Notes", "general notes");
        scanReturns(notes);

        SearchResponse response = searchService.search(request("leave policy update", null));

        assertEquals(0, response.getTotalHits());
    }

    @Test
    void scanHitsAreStillPermissionFiltered() {
        Document policy = doc(9, "Leave Policy Update", "x");
        scanReturns(policy);
        readable = Set.of();

        SearchResponse response = searchService.search(request("levae policy", null));

        assertEquals(0, response.getTotalHits());
    }

    // ------------------------------------------------------------ search modes

    private SearchRequest request(String query, List<Float> vector, SearchMode mode) {
        SearchRequest r = request(query, vector);
        r.setMode(mode);
        return r;
    }

    @Test
    void keywordModeRequiresExactTermsWhileFuzzyModeToleratesTypos() {
        Document report = doc(2, "Q1 Financial Report", "x");
        scanReturns(report);

        assertEquals(0, searchService.search(request("finacial", null, SearchMode.KEYWORD)).getTotalHits());

        SearchResponse fuzzy = searchService.search(request("finacial", null, SearchMode.FUZZY));
        assertEquals(List.of("KEYWORD"), fuzzy.getSources());
        assertEquals(Set.of("KEYWORD", "FUZZY"), fuzzy.getHits().get(0).getMatchedBy());

        // Exact terms still match in keyword mode, reordered or not.
        assertEquals(1, searchService.search(request("report financial", null, SearchMode.KEYWORD)).getTotalHits());
    }

    @Test
    void keywordModesSkipTheVectorLeg() {
        Document report = doc(1, "Leave Policy", "x");
        doc(2, "Sabbaticals", "y");
        keywordReturns(report);
        vectorReturns(vectorItem(2, 0.9f, "chunk-a"));

        for (SearchMode mode : List.of(SearchMode.KEYWORD, SearchMode.FUZZY)) {
            SearchResponse response = searchService.search(request("leave policy", List.of(0.1f), mode));
            assertEquals(List.of("KEYWORD"), response.getSources(), mode.name());
            assertEquals(List.of(1), response.getHits().stream().map(SearchHit::getDocumentId).toList(), mode.name());
        }
    }

    @Test
    void semanticModeSkipsTheKeywordLeg() {
        Document report = doc(1, "Leave Policy", "x");
        doc(2, "Sabbaticals", "y");
        keywordReturns(report);
        vectorReturns(vectorItem(2, 0.9f, "chunk-a"));

        SearchResponse response = searchService.search(request("leave policy", List.of(0.1f), SearchMode.SEMANTIC));

        assertEquals(List.of("VECTOR"), response.getSources());
        assertEquals(List.of(2), response.getHits().stream().map(SearchHit::getDocumentId).toList());
        assertEquals(Set.of("VECTOR"), response.getHits().get(0).getMatchedBy());
    }

    @Test
    void semanticModeWithoutAnEmbeddingIsUnavailableRatherThanEmpty() {
        // The stub embedding service loads no model, as when embedding is disabled.
        doc(1, "Leave Policy", "x");
        keywordReturns(known.get(0));

        assertThrows(ServiceUnavailableException.class,
                     () -> searchService.search(request("leave policy", null, SearchMode.SEMANTIC)));
        // Hybrid, by contrast, answers from keywords alone.
        assertEquals(List.of("KEYWORD"), searchService.search(request("leave policy", null)).getSources());
    }

    @Test
    void keywordModesNeedAQuery() {
        assertThrows(IllegalArgumentException.class,
                     () -> searchService.search(request(null, List.of(0.1f), SearchMode.KEYWORD)));
        assertThrows(IllegalArgumentException.class,
                     () -> searchService.search(request(" ", List.of(0.1f), SearchMode.FUZZY)));
    }

    @Test
    void missingModeMeansHybrid() {
        SearchRequest r = request("policy", null);
        r.setMode(null);
        assertEquals(SearchMode.HYBRID, r.getMode());
    }
}
