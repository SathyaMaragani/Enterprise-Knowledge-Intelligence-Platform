package com.eip.backend.dto.search;

/** Which signals a unified search uses. */
public enum SearchMode {
    /** Typo-tolerant keyword search fused with semantic search. The default. */
    HYBRID,
    /** Keyword search on exact terms: phrases and reordered words, no typo tolerance. */
    KEYWORD,
    /** Keyword search that also accepts terms within an edit or two. */
    FUZZY,
    /** Semantic search alone: the query's embedding against the vector store. */
    SEMANTIC;

    public boolean usesKeyword() {
        return this != SEMANTIC;
    }

    public boolean usesVector() {
        return this == HYBRID || this == SEMANTIC;
    }
}
