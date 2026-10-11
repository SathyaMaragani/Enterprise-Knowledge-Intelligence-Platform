package com.eip.backend.service;

import com.eip.backend.dto.DocumentUploadResponse;
import com.eip.backend.entity.Category;
import com.eip.backend.entity.Document;
import com.eip.backend.entity.User;
import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.exception.QdrantUnavailableException;
import com.eip.backend.repository.CategoryRepository;
import com.eip.backend.repository.DocumentRepository;
import com.eip.backend.repository.DocumentVersionRepository;
import com.eip.backend.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cross-database undo paths of an upload, which a live stack cannot easily
 * be made to fail on cue. Stubs record every call so each test can state exactly
 * what was written and what was removed.
 */
class DocumentIngestionServiceTest {

    private static final int ID = 77;

    private final List<String> calls = new ArrayList<>();
    private int documentSaves;
    private Function<Integer, RuntimeException> failDocumentSave = n -> null;
    private RuntimeException mongoFailure;
    private RuntimeException qdrantFailure;
    private boolean embeddingAvailable = true;
    private final List<QdrantService.ChunkVector> upserted = new ArrayList<>();
    private String upsertedCategory;
    private String upsertedDepartment;
    private Document lastSavedDocument;
    private KnowledgeDocument lastSavedKnowledge;
    private RuntimeException fileFailure;
    private Document existing;
    private boolean admin;
    private boolean mayUpdate = true;
    private final User alice = new User();

    private DocumentIngestionService service;

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> handler.invoke(proxy, method, args);
                });
    }

    @BeforeEach
    void setUp() {
        DocumentRepository documents = stub(DocumentRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "save" -> {
                documentSaves++;
                RuntimeException failure = failDocumentSave.apply(documentSaves);
                if (failure != null) {
                    throw failure;
                }
                Document document = (Document) args[0];
                document.setId(ID);
                lastSavedDocument = document;
                calls.add("postgres.save:" + document.getStatus());
                yield document;
            }
            case "deleteById" -> {
                calls.add("postgres.deleteById:" + args[0]);
                yield null;
            }
            case "findById" -> Optional.ofNullable(existing);
            default -> throw new UnsupportedOperationException(method.getName());
        });

        Category finance = new Category();
        finance.setId(2);
        finance.setName("Finance");
        Category legal = new Category();
        legal.setId(5);
        legal.setName("Legal");
        CategoryRepository categories = stub(CategoryRepository.class, (proxy, method, args) ->
                "Finance".equals(args[0]) ? Optional.of(finance)
                        : "Legal".equals(args[0]) ? Optional.of(legal) : Optional.empty());

        DocumentVersionRepository versions = stub(DocumentVersionRepository.class, (proxy, method, args) -> {
            calls.add("versions.save");
            return args[0];
        });

        KnowledgeDocumentRepository knowledge = stub(KnowledgeDocumentRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "save" -> {
                        if (mongoFailure != null) {
                            throw mongoFailure;
                        }
                        KnowledgeDocument saved = (KnowledgeDocument) args[0];
                        saved.setId("mongo-1");
                        lastSavedKnowledge = saved;
                        calls.add("mongo.save");
                        yield saved;
                    }
                    case "updateTitle" -> {
                        calls.add("mongo.updateTitle:" + args[1]);
                        yield null;
                    }
                    case "deleteByPostgresDocumentId" -> {
                        calls.add("mongo.delete:" + args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        QdrantService qdrant = new QdrantService(null) {
            @Override
            public void upsertDocumentChunks(Integer documentId, String title, String category, String department,
                                             List<ChunkVector> chunks) {
                if (qdrantFailure != null) {
                    throw qdrantFailure;
                }
                upserted.addAll(chunks);
                upsertedCategory = category;
                upsertedDepartment = department;
                calls.add("qdrant.upsert:" + chunks.size());
            }

            @Override
            public void deleteDocumentChunks(Integer documentId) {
                calls.add("qdrant.delete:" + documentId);
            }

            @Override
            public void updateDocumentPayload(Integer documentId, String title, String category) {
                if (qdrantFailure != null) {
                    throw qdrantFailure;
                }
                calls.add("qdrant.payload:" + title + "/" + category);
            }
        };

        OriginalFileStore files = new OriginalFileStore(null) {
            @Override
            public String store(Integer documentId, String filename, String contentType, byte[] bytes) {
                if (fileFailure != null) {
                    throw fileFailure;
                }
                calls.add("files.store:" + filename + ":" + contentType + ":" + bytes.length);
                return "file-1";
            }

            @Override
            public void delete(Integer documentId) {
                calls.add("files.delete:" + documentId);
            }
        };

        EmbeddingService embeddings = new EmbeddingService(null) {
            @Override
            public boolean isAvailable() {
                return embeddingAvailable;
            }

            @Override
            public List<Float> embedQuery(String text) {
                return Collections.nCopies(384, 0.1f);
            }
        };

        alice.setId(2);
        alice.setUsername("alice_mgr");
        DocumentAccessService access = new DocumentAccessService(null) {
            @Override
            public User currentUser() {
                return alice;
            }

            @Override
            public boolean isAdmin() {
                return admin;
            }

            @Override
            public boolean hasAuthority(String authority) {
                return mayUpdate && "DOCUMENT_UPDATE".equals(authority);
            }

            @Override
            public boolean canRead(Document document) {
                return true;
            }
        };

        service = new DocumentIngestionService(documents, categories, versions, knowledge, qdrant, embeddings, access,
                                               files);

        existing = new Document();
        existing.setId(ID);
        existing.setTitle("Notes");
        existing.setDescription("Old description");
        existing.setCategory(finance);
        existing.setOwner(alice);
        existing.setStatus("INDEXED");
    }

    private DocumentUploadResponse upload(String text) {
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown",
                                                       text.getBytes(StandardCharsets.UTF_8));
        return service.upload(file, "Notes", null, "Finance", null);
    }

    private static String words(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            text.append("w").append(i).append(' ');
        }
        return text.toString();
    }

    @Test
    void successStoresEverythingAndIndexes() {
        DocumentUploadResponse response = upload(words(400));

        assertEquals(new DocumentUploadResponse(ID, "Notes", "INDEXED", 3, true), response);
        int bytes = words(400).getBytes(StandardCharsets.UTF_8).length;
        assertEquals(List.of("postgres.save:PROCESSING", "files.store:notes.md:text/markdown:" + bytes, "mongo.save",
                             "versions.save", "qdrant.upsert:3", "postgres.save:INDEXED"), calls);
        // The content document records where the original is, which is what makes it downloadable.
        assertEquals("GRIDFS", lastSavedKnowledge.getSource().getStorageType());
        assertEquals("gridfs:file-1", lastSavedKnowledge.getSource().getStorageReference());
        assertEquals(List.of("doc77-chunk1", "doc77-chunk2", "doc77-chunk3"),
                     upserted.stream().map(QdrantService.ChunkVector::chunkId).toList());
        assertEquals(List.of(0, 1, 2), upserted.stream().map(QdrantService.ChunkVector::position).toList());
        assertEquals("Finance", upsertedCategory);
        assertEquals("Finance", upsertedDepartment, "department defaults to the category");
        assertEquals("mongodb:knowledge_documents/mongo-1", lastSavedDocument.getStorageReference());
    }

    @Test
    void withoutTheEmbeddingModelTheDocumentIsStoredAsUploaded() {
        embeddingAvailable = false;

        DocumentUploadResponse response = upload("a few words");

        assertEquals("UPLOADED", response.status());
        assertFalse(response.vectorsStored());
        assertFalse(calls.stream().anyMatch(call -> call.startsWith("qdrant")));
    }

    @Test
    void qdrantFailureKeepsTheDocumentAsUploaded() {
        qdrantFailure = new QdrantUnavailableException("down", null);

        DocumentUploadResponse response = upload("a few words");

        assertEquals("UPLOADED", response.status());
        assertFalse(response.vectorsStored());
        assertEquals(List.of("postgres.save:PROCESSING", "files.store:notes.md:text/markdown:11", "mongo.save",
                             "versions.save", "postgres.save:UPLOADED"), calls);
    }

    @Test
    void mongoFailureRemovesThePostgresRow() {
        mongoFailure = new IllegalStateException("mongo down");

        assertThrows(IllegalStateException.class, () -> upload("a few words"));

        assertEquals(List.of("postgres.save:PROCESSING", "files.store:notes.md:text/markdown:11",
                             "files.delete:77", "postgres.deleteById:77"), calls);
    }

    @Test
    void failureAfterVectorsRemovesVectorsContentAndRow() {
        failDocumentSave = n -> n == 2 ? new IllegalStateException("final save failed") : null;

        assertThrows(IllegalStateException.class, () -> upload("a few words"));

        assertEquals(List.of("postgres.save:PROCESSING", "files.store:notes.md:text/markdown:11", "mongo.save",
                             "versions.save", "qdrant.upsert:1", "qdrant.delete:77", "mongo.delete:77",
                             "files.delete:77", "postgres.deleteById:77"), calls);
    }

    @Test
    void anUploadSurvivesTheOriginalNotBeingKept() {
        fileFailure = new IllegalStateException("GridFS is full");

        DocumentUploadResponse response = upload("a few words");

        assertEquals("INDEXED", response.status());
        assertEquals("MONGODB", lastSavedKnowledge.getSource().getStorageType());
        assertEquals("upload:notes.md", lastSavedKnowledge.getSource().getStorageReference());
    }

    // ---------------------------------------------------------------- editing

    @Test
    void anEditReachesAllThreeStoresQdrantFirst() {
        Document saved = service.update(ID, "  Budget Notes ", "New description", "Legal");

        assertEquals(List.of("qdrant.payload:Budget Notes/Legal", "mongo.updateTitle:Budget Notes",
                             "postgres.save:INDEXED"), calls);
        assertEquals("Budget Notes", saved.getTitle());
        assertEquals("New description", saved.getDescription());
        assertEquals("Legal", saved.getCategory().getName());
    }

    @Test
    void fieldsLeftOutAreNotChangedAndABlankDescriptionIsCleared() {
        Document saved = service.update(ID, null, "   ", null);

        // Title and category are unchanged, so neither Qdrant nor MongoDB is touched.
        assertEquals(List.of("postgres.save:INDEXED"), calls);
        assertEquals("Notes", saved.getTitle());
        assertNull(saved.getDescription());
        assertEquals("Finance", saved.getCategory().getName());
    }

    @Test
    void aDocumentWithoutVectorsSkipsQdrant() {
        existing.setStatus("UPLOADED");

        service.update(ID, "Renamed", null, null);

        assertEquals(List.of("mongo.updateTitle:Renamed", "postgres.save:UPLOADED"), calls);
    }

    @Test
    void ifQdrantIsDownNothingIsChanged() {
        qdrantFailure = new QdrantUnavailableException("down", null);

        assertThrows(QdrantUnavailableException.class, () -> service.update(ID, "Renamed", null, null));

        assertTrue(calls.isEmpty());
        assertEquals("Notes", existing.getTitle());
    }

    @Test
    void onlyTheOwnerWithUpdateRightsOrAnAdminMayEdit() {
        User bob = new User();
        bob.setId(3);
        existing.setOwner(bob);
        assertThrows(AccessDeniedException.class, () -> service.update(ID, "Renamed", null, null));

        existing.setOwner(alice);
        mayUpdate = false;
        assertThrows(AccessDeniedException.class, () -> service.update(ID, "Renamed", null, null));

        existing.setOwner(bob);
        admin = true;
        assertEquals("Renamed", service.update(ID, "Renamed", null, null).getTitle());
    }

    @Test
    void anEditIsValidatedLikeAnUpload() {
        assertThrows(IllegalArgumentException.class, () -> service.update(ID, "   ", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.update(ID, "x".repeat(256), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.update(ID, null, null, "Unknown"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void validationFailsBeforeAnythingIsWritten() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "x".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.upload(file, null, null, "Unknown", null));

        assertTrue(calls.isEmpty());
    }
}
