package com.eip.backend.dto;

/**
 * The outcome of an upload.
 *
 * @param status        INDEXED when chunk embeddings were stored, UPLOADED when the
 *                      document is stored and keyword-searchable but has no vectors
 * @param vectorsStored whether semantic search can find this document
 */
public record DocumentUploadResponse(Integer id, String title, String status, int chunkCount, boolean vectorsStored) {
}
