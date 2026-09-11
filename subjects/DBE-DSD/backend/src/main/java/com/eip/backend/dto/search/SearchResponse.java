package com.eip.backend.dto.search;

import java.util.List;

public class SearchResponse {
    private List<SearchHit> hits;
    private int page;
    private int size;
    /** Hits the caller is allowed to see, across all pages. */
    private long totalHits;
    /** Which backends actually ran, e.g. ["KEYWORD","VECTOR"]. */
    private List<String> sources;

    public List<SearchHit> getHits() { return hits; }
    public void setHits(List<SearchHit> hits) { this.hits = hits; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalHits() { return totalHits; }
    public void setTotalHits(long totalHits) { this.totalHits = totalHits; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
}
