package com.eip.backend.dto.search;

import java.util.LinkedHashSet;
import java.util.Set;

/** One document in a search result, with the evidence for why it ranked where it did. */
public class SearchHit {
    private Integer documentId;
    private String title;
    private String description;
    private String category;
    private String owner;
    private String status;

    private double score;
    private Double keywordScore;
    private Double vectorScore;
    private String chunkId;
    private final Set<String> matchedBy = new LinkedHashSet<>();

    public Integer getDocumentId() { return documentId; }
    public void setDocumentId(Integer documentId) { this.documentId = documentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public Double getKeywordScore() { return keywordScore; }
    public void setKeywordScore(Double keywordScore) { this.keywordScore = keywordScore; }
    public Double getVectorScore() { return vectorScore; }
    public void setVectorScore(Double vectorScore) { this.vectorScore = vectorScore; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public Set<String> getMatchedBy() { return matchedBy; }
}
