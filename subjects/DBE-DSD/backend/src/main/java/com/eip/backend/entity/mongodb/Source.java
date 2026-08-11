package com.eip.backend.entity.mongodb;
import org.springframework.data.mongodb.core.mapping.Field;
public class Source {
    private String filename;
    @Field("mime_type") private String mimeType;
    @Field("storage_type") private String storageType;
    @Field("storage_reference") private String storageReference;
    // Getters and Setters
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getStorageReference() { return storageReference; }
    public void setStorageReference(String storageReference) { this.storageReference = storageReference; }
}
