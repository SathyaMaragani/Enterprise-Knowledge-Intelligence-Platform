package com.eip.backend.service;

import com.eip.backend.dto.search.SearchActivity;
import com.eip.backend.dto.search.SearchMode;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.entity.SearchHistory;
import com.eip.backend.entity.User;
import com.eip.backend.repository.SearchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchActivityServiceTest {

    private final List<SearchHistory> saved = new ArrayList<>();
    private List<SearchHistory> stored = List.of();
    private boolean saveFails;
    private User currentUser;
    private SearchActivityService service;

    @BeforeEach
    void setUp() {
        SearchHistoryRepository repository = (SearchHistoryRepository) Proxy.newProxyInstance(
                SearchHistoryRepository.class.getClassLoader(), new Class<?>[]{SearchHistoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        if (saveFails) {
                            throw new DataAccessResourceFailureException("database down");
                        }
                        saved.add((SearchHistory) args[0]);
                        yield args[0];
                    }
                    case "findTop50ByUser_IdOrderByCreatedAtDescIdDesc" -> stored;
                    case "toString" -> "stubSearchHistoryRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        DocumentAccessService access = new DocumentAccessService(null) {
            @Override
            public User currentUser() {
                return currentUser;
            }
        };
        service = new SearchActivityService(repository, access);

        currentUser = new User();
        currentUser.setId(7);
    }

    private static SearchRequest search(String query, SearchMode mode, int page) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setMode(mode);
        request.setPage(page);
        return request;
    }

    private static SearchResponse hits(long total) {
        SearchResponse response = new SearchResponse();
        response.setTotalHits(total);
        return response;
    }

    private static SearchHistory entry(String query, String mode, int minutesAgo) {
        SearchHistory history = new SearchHistory();
        history.setQueryText(query);
        history.setSearchType(mode);
        history.setResultCount(minutesAgo);
        history.setCreatedAt(ZonedDateTime.now().minusMinutes(minutesAgo));
        return history;
    }

    @Test
    void recordsAFirstPageSearchWithItsModeAndResultCount() {
        service.record(search("  vendor contract ", SearchMode.FUZZY, 0), hits(4));

        SearchHistory entry = saved.get(0);
        assertSame(currentUser, entry.getUser());
        assertEquals("vendor contract", entry.getQueryText());
        assertEquals("FUZZY", entry.getSearchType());
        assertEquals(4, entry.getResultCount());
    }

    @Test
    void skipsLaterPagesVectorOnlySearchesAndAnonymousCallers() {
        service.record(search("vendor", SearchMode.HYBRID, 1), hits(4));

        SearchRequest vectorOnly = new SearchRequest();
        vectorOnly.setVector(List.of(0.1f));
        service.record(vectorOnly, hits(4));

        currentUser = null;
        service.record(search("vendor", SearchMode.HYBRID, 0), hits(4));

        assertTrue(saved.isEmpty());
    }

    @Test
    void truncatesVeryLongQueries() {
        service.record(search("x".repeat(2000), SearchMode.HYBRID, 0), hits(0));
        assertEquals(SearchActivityService.MAX_QUERY_LENGTH, saved.get(0).getQueryText().length());
    }

    @Test
    void aFailureToRecordNeverFailsTheSearch() {
        saveFails = true;
        assertDoesNotThrow(() -> service.record(search("vendor", SearchMode.HYBRID, 0), hits(1)));
    }

    @Test
    void recentCollapsesRepeatsIgnoringCaseButKeepsDifferentModes() {
        stored = List.of(
                entry("Budget", "HYBRID", 1),
                entry("budget", "HYBRID", 2),
                entry("budget", "FUZZY", 3),
                entry("leave policy", "HYBRID", 4),
                entry("Budget", "HYBRID", 5));

        List<SearchActivity> recent = service.recent(10);

        assertEquals(List.of("Budget/HYBRID", "budget/FUZZY", "leave policy/HYBRID"),
                     recent.stream().map(a -> a.query() + "/" + a.mode()).toList());
        assertEquals(1, recent.get(0).resultCount(), "the latest repeat is the one shown");
        assertEquals(2, service.recent(2).size());
    }

    @Test
    void recentIsEmptyWithoutAUser() {
        currentUser = null;
        assertEquals(List.of(), service.recent(5));
    }
}
