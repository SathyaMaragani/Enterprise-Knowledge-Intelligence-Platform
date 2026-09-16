package com.eip.backend.dto;

import java.util.List;

/** One page of document metadata, with totals for the caller's filtered view. */
public record DocumentPageResponse(
        List<DocumentResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
