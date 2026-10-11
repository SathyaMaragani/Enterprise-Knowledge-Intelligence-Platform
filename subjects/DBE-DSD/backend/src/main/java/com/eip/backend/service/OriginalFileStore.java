package com.eip.backend.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.gridfs.GridFsCriteria.whereMetaData;

/**
 * Keeps each uploaded file exactly as it was uploaded, so it can be downloaded
 * again. Search and the viewer work from the extracted text; this is the file
 * itself.
 *
 * <p>Files live in GridFS in the application's MongoDB database, keyed by the
 * PostgreSQL document id, so no further service is needed.
 */
// ponytail: GridFS shares the free Atlas cluster's 512 MB with the extracted text.
// At the 10 MB upload limit that is about fifty full-size files; move originals
// to object storage when the cluster fills.
@Component
public class OriginalFileStore {

    /** A stored file: what to call it, what it is, and its bytes. */
    public record Original(String filename, String contentType, long length, Resource resource) {
    }

    private static final String DOCUMENT_ID = "postgres_document_id";

    private final GridFsTemplate gridFs;

    public OriginalFileStore(GridFsTemplate gridFs) {
        this.gridFs = gridFs;
    }

    /** @return the stored file's GridFS id */
    public String store(Integer documentId, String filename, String contentType, byte[] bytes) {
        return gridFs.store(new ByteArrayInputStream(bytes), filename, contentType,
                            new Document(DOCUMENT_ID, documentId)).toHexString();
    }

    public Optional<Original> find(Integer documentId) {
        GridFSFile file = gridFs.findOne(query(whereMetaData(DOCUMENT_ID).is(documentId)));
        if (file == null) {
            return Optional.empty();
        }
        Document metadata = file.getMetadata();
        Object contentType = metadata == null ? null : metadata.get("_contentType");
        return Optional.of(new Original(file.getFilename(),
                contentType instanceof String type ? type : "application/octet-stream",
                file.getLength(), gridFs.getResource(file)));
    }

    /** Removes a document's file; does nothing if it has none. */
    public void delete(Integer documentId) {
        gridFs.delete(query(whereMetaData(DOCUMENT_ID).is(documentId)));
    }
}
