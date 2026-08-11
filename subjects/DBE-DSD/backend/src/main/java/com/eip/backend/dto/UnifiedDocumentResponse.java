package com.eip.backend.dto;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class UnifiedDocumentResponse {
    // PostgreSQL fields
    private Integer id;
    private String title;
    private String description;
    private String category;
    private String owner;
    private String status;
    private String documentType;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    
    // MongoDB fields
    private Object content;
    private Object source;
    private Map<String, Object> metadata;
    private List<?> chunks;
    private List<?> references;
    private Object processing;
    private Object version;

    // Postgres getters and setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
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
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Mongo getters and setters
    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }
    public Object getSource() { return source; }
    public void setSource(Object source) { this.source = source; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public List<?> getChunks() { return chunks; }
    public void setChunks(List<?> chunks) { this.chunks = chunks; }
    public List<?> getReferences() { return references; }
    public void setReferences(List<?> references) { this.references = references; }
    public Object getProcessing() { return processing; }
    public void setProcessing(Object processing) { this.processing = processing; }
    public Object getVersion() { return version; }
    public void setVersion(Object version) { this.version = version; }
}
