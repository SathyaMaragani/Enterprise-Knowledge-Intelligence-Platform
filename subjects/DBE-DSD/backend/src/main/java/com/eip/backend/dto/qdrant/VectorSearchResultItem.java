package com.eip.backend.dto.qdrant;

public class VectorSearchResultItem {
    private String pointId;
    private Float score;
    private Integer postgresDocumentId;
    private String chunkId;
    private String title;
    private String category;
    private String department;
    private String language;
    private Integer chunkPosition;
    private Integer pageNumber;
    private String processingStatus;

    public VectorSearchResultItem() {}

    public String getPointId() { return pointId; }
    public void setPointId(String pointId) { this.pointId = pointId; }

    public Float getScore() { return score; }
    public void setScore(Float score) { this.score = score; }

    public Integer getPostgresDocumentId() { return postgresDocumentId; }
    public void setPostgresDocumentId(Integer postgresDocumentId) { this.postgresDocumentId = postgresDocumentId; }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getChunkPosition() { return chunkPosition; }
    public void setChunkPosition(Integer chunkPosition) { this.chunkPosition = chunkPosition; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
