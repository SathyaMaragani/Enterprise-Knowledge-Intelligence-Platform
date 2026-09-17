package com.eip.backend.dto.search;

import java.util.List;

/**
 * One search across all three stores. Supply {@code query}; the server embeds it
 * for semantic search. A caller-supplied {@code vector} is still accepted.
 * {@code mode} picks the signals and defaults to {@link SearchMode#HYBRID}.
 */
public class SearchRequest {
    private String query;
    private List<Float> vector;
    private String category;
    private String department;
    private String status;
    private int page = 0;
    private int size = 10;
    private SearchMode mode = SearchMode.HYBRID;

    public boolean hasQuery() { return query != null && !query.isBlank(); }
    public boolean hasVector() { return vector != null && !vector.isEmpty(); }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<Float> getVector() { return vector; }
    public void setVector(List<Float> vector) { this.vector = vector; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public SearchMode getMode() { return mode; }
    public void setMode(SearchMode mode) { this.mode = mode == null ? SearchMode.HYBRID : mode; }
}
