package com.eip.backend.dto;

public class SemanticSearchResponse {
    private Float score;
    private String chunkId;
    private UnifiedDocumentResponse document;

    public Float getScore() { return score; }
    public void setScore(Float score) { this.score = score; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public UnifiedDocumentResponse getDocument() { return document; }
    public void setDocument(UnifiedDocumentResponse document) { this.document = document; }
}
