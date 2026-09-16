package com.eip.backend.exception;

/** Something the request names does not exist; mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
