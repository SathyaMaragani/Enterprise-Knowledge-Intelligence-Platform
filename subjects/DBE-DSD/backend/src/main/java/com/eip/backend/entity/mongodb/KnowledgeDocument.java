package com.eip.backend.entity.mongodb;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "knowledge_documents")
public class KnowledgeDocument {
    @Id private String id;
    @Field("postgres_document_id") private Integer postgresDocumentId;
    private String title;
    private Content content;
    private Source source;
    private Map<String, Object> metadata;
    private List<Chunk> chunks;
    private List<Reference> references;
    private Processing processing;
    private Version version;
    @Field("created_at") private Date createdAt;
    @Field("updated_at") private Date updatedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getPostgresDocumentId() { return postgresDocumentId; }
    public void setPostgresDocumentId(Integer postgresDocumentId) { this.postgresDocumentId = postgresDocumentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Content getContent() { return content; }
    public void setContent(Content content) { this.content = content; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public List<Chunk> getChunks() { return chunks; }
    public void setChunks(List<Chunk> chunks) { this.chunks = chunks; }
    public List<Reference> getReferences() { return references; }
    public void setReferences(List<Reference> references) { this.references = references; }
    public Processing getProcessing() { return processing; }
    public void setProcessing(Processing processing) { this.processing = processing; }
    public Version getVersion() { return version; }
    public void setVersion(Version version) { this.version = version; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
