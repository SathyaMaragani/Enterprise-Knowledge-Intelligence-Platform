package com.eip.backend.dto.qdrant;

import java.util.List;

public class VectorSearchRequest {
    private List<Float> vector;
    private Integer topK = 5;
    private String category;
    private String department;
    private String processingStatus;
    private Integer postgresDocumentId;

    public VectorSearchRequest() {}

    public VectorSearchRequest(List<Float> vector, Integer topK) {
        this.vector = vector;
        this.topK = topK;
    }

    public List<Float> getVector() { return vector; }
    public void setVector(List<Float> vector) { this.vector = vector; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public Integer getPostgresDocumentId() { return postgresDocumentId; }
    public void setPostgresDocumentId(Integer postgresDocumentId) { this.postgresDocumentId = postgresDocumentId; }
}
