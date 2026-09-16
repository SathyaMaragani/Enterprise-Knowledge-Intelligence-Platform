package com.eip.backend.exception;

/** The request would duplicate something that must be unique; mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
