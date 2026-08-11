package com.eip.backend.entity.mongodb;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.Date;
public class Processing {
    private String status;
    @Field("processed_at") private Date processedAt;
    @Field("extractor_version") private String extractorVersion;
    @Field("chunker_version") private String chunkerVersion;
    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getProcessedAt() { return processedAt; }
    public void setProcessedAt(Date processedAt) { this.processedAt = processedAt; }
    public String getExtractorVersion() { return extractorVersion; }
    public void setExtractorVersion(String extractorVersion) { this.extractorVersion = extractorVersion; }
    public String getChunkerVersion() { return chunkerVersion; }
    public void setChunkerVersion(String chunkerVersion) { this.chunkerVersion = chunkerVersion; }
}
