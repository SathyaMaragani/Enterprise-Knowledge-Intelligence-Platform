package com.eip.backend.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(Integer id) {
        super("Document " + id + " does not exist");
    }
}
