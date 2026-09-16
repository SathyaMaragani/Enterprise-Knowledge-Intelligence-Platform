package com.eip.backend.exception;

public class DocumentNotFoundException extends ResourceNotFoundException {
    public DocumentNotFoundException(Integer id) {
        super("Document " + id + " does not exist");
    }
}
