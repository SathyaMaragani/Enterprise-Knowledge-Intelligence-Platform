package com.eip.backend.service;

import com.eip.backend.dto.DocumentUploadResponse;
import com.eip.backend.entity.Category;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.DocumentVersion;
import com.eip.backend.entity.User;
import com.eip.backend.entity.mongodb.Chunk;
import com.eip.backend.entity.mongodb.Content;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.entity.mongodb.Processing;
import com.eip.backend.entity.mongodb.Source;
import com.eip.backend.entity.mongodb.Version;
import com.eip.backend.exception.DocumentNotFoundException;
import com.eip.backend.repository.CategoryRepository;
import com.eip.backend.repository.DocumentRepository;
import com.eip.backend.repository.DocumentVersionRepository;
import com.eip.backend.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stores an uploaded document across all three databases, and removes one.
 *
 * <p>Upload order: PostgreSQL row (status PROCESSING), MongoDB content and chunks,
 * a version-1 history row, chunk embeddings in Qdrant, then the final status.
 * There is no transaction spanning three databases, so each later failure undoes
 * the earlier writes before the error is returned: a failed upload leaves nothing
 * behind.
 *
 * <p>Vectors are optional. With the embedding model disabled, or Qdrant down, the
 * document is still stored and keyword-searchable with status UPLOADED; with
 * vectors it is INDEXED.
 */
@Service
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** Extension -> {document type, MIME type}. Plain text only: no parser dependency is needed. */
    private static final Map<String, String[]> SUPPORTED_TYPES = Map.of(
            "txt", new String[]{"TXT", "text/plain"},
            "md", new String[]{"MD", "text/markdown"},
            "markdown", new String[]{"MD", "text/markdown"});

    public static final long MAX_BYTES = 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 255;
    static final String EXTRACTOR_VERSION = "utf8-text-1.0";

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;
    private final DocumentAccessService documentAccessService;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    CategoryRepository categoryRepository,
                                    DocumentVersionRepository documentVersionRepository,
                                    KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    QdrantService qdrantService,
                                    EmbeddingService embeddingService,
                                    DocumentAccessService documentAccessService) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.qdrantService = qdrantService;
        this.embeddingService = embeddingService;
        this.documentAccessService = documentAccessService;
    }

    // ponytail: embeddings are computed inside the request, one chunk at a time.
    // A 1 MB file is roughly a thousand chunks, which takes tens of seconds on CPU.
    // For larger files, accept the upload with status PROCESSING and embed in a
    // background job.
    public DocumentUploadResponse upload(MultipartFile file, String title, String description,
                                         String categoryName, String department) {
        User owner = documentAccessService.currentUser();
        if (owner == null) {
            throw new org.springframework.security.access.AccessDeniedException("Sign in to upload documents");
        }

        String filename = requireFilename(file);
        String[] type = requireSupportedType(filename);
        String text = requireText(file);
        Category category = requireCategory(categoryName);
        String finalTitle = resolveTitle(title, filename);
        String finalDepartment = isBlank(department) ? category.getName() : department.trim();

        Document document = new Document();
        document.setTitle(finalTitle);
        document.setDescription(isBlank(description) ? null : description.trim());
        document.setCategory(category);
        document.setOwner(owner);
        document.setDocumentType(type[0]);
        document.setStatus("PROCESSING");
        document.setStorageReference("pending");
        document = documentRepository.save(document);
        Integer id = document.getId();

        boolean mongoSaved = false;
        boolean vectorsStored = false;
        try {
            List<String> chunkTexts = TextChunker.chunk(text);
            KnowledgeDocument knowledge = buildKnowledgeDocument(id, finalTitle, text, filename, type[1],
                                                                 finalDepartment, owner.getUsername(), chunkTexts);
            knowledge = knowledgeDocumentRepository.save(knowledge);
            mongoSaved = true;

            String storageReference = "mongodb:knowledge_documents/" + knowledge.getId();
            document.setStorageReference(storageReference);

            DocumentVersion version = new DocumentVersion();
            version.setDocument(document);
            version.setVersionNumber(1);
            version.setStorageReference(storageReference);
            version.setUploadedBy(owner);
            version.setChangeSummary("Initial upload");
            documentVersionRepository.save(version);

            vectorsStored = storeVectors(id, finalTitle, category.getName(), finalDepartment, chunkTexts);

            document.setStatus(vectorsStored ? "INDEXED" : "UPLOADED");
            document.setUpdatedAt(ZonedDateTime.now());
            documentRepository.save(document);

            return new DocumentUploadResponse(id, finalTitle, document.getStatus(), chunkTexts.size(), vectorsStored);
        } catch (RuntimeException e) {
            undoUpload(id, mongoSaved, vectorsStored);
            throw e;
        }
    }

    /**
     * Removes a document from Qdrant, MongoDB and PostgreSQL, in that order.
     *
     * <p>Qdrant goes first: if it is unreachable the request fails with 503 before
     * anything is deleted, so the document is never left half-removed and the
     * caller can simply retry. PostgreSQL cascades versions, grants and tags.
     */
    public void delete(Integer id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        documentAccessService.requireRead(document);

        qdrantService.deleteDocumentChunks(id);
        knowledgeDocumentRepository.deleteByPostgresDocumentId(id);
        documentRepository.delete(document);
    }

    private boolean storeVectors(Integer id, String title, String category, String department, List<String> chunkTexts) {
        if (!embeddingService.isAvailable() || chunkTexts.isEmpty()) {
            return false;
        }
        List<QdrantService.ChunkVector> vectors = new ArrayList<>(chunkTexts.size());
        for (int position = 0; position < chunkTexts.size(); position++) {
            vectors.add(new QdrantService.ChunkVector(chunkId(id, position), position,
                                                      embeddingService.embedQuery(chunkTexts.get(position))));
        }
        try {
            qdrantService.upsertDocumentChunks(id, title, category, department, vectors);
            return true;
        } catch (RuntimeException e) {
            // Content is safely stored; only semantic search is missing. Report it
            // through the status rather than failing the whole upload.
            logger.warn("Stored document {} without vectors: {}", id, e.getMessage());
            return false;
        }
    }

    private void undoUpload(Integer id, boolean mongoSaved, boolean vectorsStored) {
        try {
            if (vectorsStored) {
                qdrantService.deleteDocumentChunks(id);
            }
            if (mongoSaved) {
                knowledgeDocumentRepository.deleteByPostgresDocumentId(id);
            }
            documentRepository.deleteById(id);
        } catch (RuntimeException cleanup) {
            logger.error("Could not fully undo failed upload of document {}", id, cleanup);
        }
    }

    private static KnowledgeDocument buildKnowledgeDocument(Integer id, String title, String text, String filename,
                                                            String mimeType, String department, String uploadedBy,
                                                            List<String> chunkTexts) {
        Date now = new Date();

        Content content = new Content();
        content.setRawText(text);
        content.setWordCount(TextChunker.wordCount(text));
        content.setCharacterCount(text.length());

        Source source = new Source();
        source.setFilename(filename);
        source.setMimeType(mimeType);
        source.setStorageType("MONGODB");
        source.setStorageReference("upload:" + filename);

        List<Chunk> chunks = new ArrayList<>(chunkTexts.size());
        for (int position = 0; position < chunkTexts.size(); position++) {
            Chunk chunk = new Chunk();
            chunk.setChunkId(chunkId(id, position));
            chunk.setText(chunkTexts.get(position));
            chunk.setPosition(position);
            chunks.add(chunk);
        }

        Processing processing = new Processing();
        processing.setStatus("COMPLETED");
        processing.setProcessedAt(now);
        processing.setExtractorVersion(EXTRACTOR_VERSION);
        processing.setChunkerVersion(TextChunker.VERSION);

        Version version = new Version();
        version.setNumber(1);
        version.setChangeSummary("Initial upload");
        version.setCreatedAt(now);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("department", department);
        metadata.put("uploaded_by", uploadedBy);

        KnowledgeDocument knowledge = new KnowledgeDocument();
        knowledge.setPostgresDocumentId(id);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setSource(source);
        knowledge.setMetadata(metadata);
        knowledge.setChunks(chunks);
        knowledge.setReferences(List.of());
        knowledge.setProcessing(processing);
        knowledge.setVersion(version);
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        return knowledge;
    }

    static String chunkId(Integer documentId, int position) {
        return "doc" + documentId + "-chunk" + (position + 1);
    }

    private static String requireFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty file to upload");
        }
        String name = file.getOriginalFilename();
        if (isBlank(name)) {
            throw new IllegalArgumentException("The uploaded file has no name");
        }
        // Browsers may send a full client path; keep only the file name.
        return name.replace('\\', '/').substring(name.replace('\\', '/').lastIndexOf('/') + 1).trim();
    }

    private static String[] requireSupportedType(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        String[] type = SUPPORTED_TYPES.get(extension);
        if (type == null) {
            throw new IllegalArgumentException("Only .txt and .md files can be uploaded");
        }
        return type;
    }

    private static String requireText(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Files can be at most 1 MB");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("The file must be UTF-8 text");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The file must be UTF-8 text");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("The file has no text");
        }
        return text;
    }

    private Category requireCategory(String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("Choose a category");
        }
        return categoryRepository.findByName(name.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + name.trim()));
    }

    private static String resolveTitle(String title, String filename) {
        String resolved = isBlank(title) ? stripExtension(filename) : title.trim();
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("Give the document a title");
        }
        if (resolved.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Titles can be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return resolved;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
