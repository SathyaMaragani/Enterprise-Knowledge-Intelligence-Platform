package com.eip.backend.exception;

public class QdrantUnavailableException extends ServiceUnavailableException {
    public QdrantUnavailableException(String message) {
        super(message);
    }

    public QdrantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
