package com.eip.backend.service;

import com.eip.backend.dto.search.SearchActivity;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.entity.SearchHistory;
import com.eip.backend.entity.User;
import com.eip.backend.repository.SearchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Each user's own search activity. Searches are recorded per user and only ever
 * read back by that same user; nobody, administrators included, sees another
 * person's queries.
 */
@Service
public class SearchActivityService {

    private static final Logger logger = LoggerFactory.getLogger(SearchActivityService.class);

    static final int MAX_QUERY_LENGTH = 500;

    // ponytail: history is kept forever. Add a retention job (delete older than N
    // days) once the table's size or privacy policy calls for one.

    private final SearchHistoryRepository searchHistoryRepository;
    private final DocumentAccessService documentAccessService;

    public SearchActivityService(SearchHistoryRepository searchHistoryRepository,
                                 DocumentAccessService documentAccessService) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.documentAccessService = documentAccessService;
    }

    /**
     * Records a signed-in user's text search. Only the first page counts, so paging
     * through results is one search, not several. A failure to record is logged and
     * never fails the search itself.
     */
    public void record(SearchRequest request, SearchResponse response) {
        User user = documentAccessService.currentUser();
        if (user == null || !request.hasQuery() || request.getPage() != 0) {
            return;
        }
        String query = request.getQuery().trim();
        SearchHistory entry = new SearchHistory();
        entry.setUser(user);
        entry.setQueryText(query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query);
        entry.setSearchType(request.getMode().name());
        entry.setResultCount((int) Math.min(response.getTotalHits(), Integer.MAX_VALUE));
        entry.setCreatedAt(ZonedDateTime.now());
        try {
            searchHistoryRepository.save(entry);
        } catch (DataAccessException e) {
            logger.warn("Could not record search activity: {}", e.getMessage());
        }
    }

    /**
     * The current user's latest searches, newest first. Repeats of the same query
     * in the same mode (ignoring case) appear once, at their latest time.
     */
    @Transactional(readOnly = true)
    public List<SearchActivity> recent(int limit) {
        User user = documentAccessService.currentUser();
        if (user == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<SearchActivity> activity = new ArrayList<>();
        for (SearchHistory entry : searchHistoryRepository.findTop50ByUser_IdOrderByCreatedAtDescIdDesc(user.getId())) {
            String key = entry.getSearchType() + '\n' + entry.getQueryText().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                activity.add(new SearchActivity(entry.getQueryText(), entry.getSearchType(),
                                                entry.getResultCount(), entry.getCreatedAt()));
                if (activity.size() == limit) {
                    break;
                }
            }
        }
        return activity;
    }
}
