package com.eip.backend.dto;
import java.util.List;

public class SemanticSearchRequest {
    private List<Float> vector;
    private String category;
    private String department;
    private int limit = 5;

    public List<Float> getVector() { return vector; }
    public void setVector(List<Float> vector) { this.vector = vector; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
