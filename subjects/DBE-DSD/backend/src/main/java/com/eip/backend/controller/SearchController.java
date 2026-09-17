package com.eip.backend.controller;

import com.eip.backend.dto.search.SearchActivity;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.service.SearchActivityService;
import com.eip.backend.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The one search entry point. SecurityConfig already requires authentication on
 * /api/**, and SearchService filters results per user on top of that.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchActivityService searchActivityService;

    public SearchController(SearchService searchService, SearchActivityService searchActivityService) {
        this.searchService = searchService;
        this.searchActivityService = searchActivityService;
    }

    @PostMapping
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        SearchResponse response = searchService.search(request);
        searchActivityService.record(request, response);
        return ResponseEntity.ok(response);
    }

    /** The caller's own recent searches, newest first. */
    @GetMapping("/history")
    public List<SearchActivity> history(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit must be between 1 and 20");
        }
        return searchActivityService.recent(limit);
    }
}
