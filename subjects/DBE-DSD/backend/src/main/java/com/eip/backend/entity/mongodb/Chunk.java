package com.eip.backend.entity.mongodb;
import org.springframework.data.mongodb.core.mapping.Field;
public class Chunk {
    @Field("chunk_id") private String chunkId;
    private String text;
    private Integer position;
    @Field("page_number") private Integer pageNumber;
    @Field("token_count") private Integer tokenCount;
    // Getters and Setters
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
}
