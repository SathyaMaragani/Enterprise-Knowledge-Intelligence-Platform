package com.eip.backend.dto.qdrant;

import java.util.List;

public class VectorSearchResponse {
    private List<VectorSearchResultItem> results;
    private int totalResults;

    public VectorSearchResponse() {}

    public VectorSearchResponse(List<VectorSearchResultItem> results) {
        this.results = results;
        this.totalResults = (results != null) ? results.size() : 0;
    }

    public List<VectorSearchResultItem> getResults() { return results; }
    public void setResults(List<VectorSearchResultItem> results) {
        this.results = results;
        this.totalResults = (results != null) ? results.size() : 0;
    }

    public int getTotalResults() { return totalResults; }
    public void setTotalResults(int totalResults) { this.totalResults = totalResults; }
}
