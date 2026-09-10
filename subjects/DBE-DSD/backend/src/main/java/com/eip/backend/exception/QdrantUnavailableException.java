package com.eip.backend.exception;

public class QdrantUnavailableException extends RuntimeException {
    public QdrantUnavailableException(String message) {
        super(message);
    }

    public QdrantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
