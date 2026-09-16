package com.eip.backend.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping word windows for embedding and display.
 *
 * <p>The defaults match the ML demo corpus pipeline ({@code subjects/ML/src/demo/emit.py}:
 * 180 words with a 40-word overlap), so uploaded documents are chunked the same
 * way as the ingested corpus. The overlap keeps a sentence that straddles a
 * boundary whole in at least one chunk.
 *
 * <p>Whitespace inside a chunk is collapsed to single spaces; the document's
 * original text is stored separately and untouched.
 */
public final class TextChunker {

    public static final int DEFAULT_CHUNK_WORDS = 180;
    public static final int DEFAULT_OVERLAP_WORDS = 40;
    public static final String VERSION = "words-180-40";

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_WORDS, DEFAULT_OVERLAP_WORDS);
    }

    /**
     * @param chunkWords   words per chunk, at least 1
     * @param overlapWords words shared with the previous chunk, 0 to chunkWords - 1
     */
    public static List<String> chunk(String text, int chunkWords, int overlapWords) {
        if (chunkWords < 1 || overlapWords < 0 || overlapWords >= chunkWords) {
            throw new IllegalArgumentException("need chunkWords >= 1 and 0 <= overlapWords < chunkWords");
        }
        List<String> words = words(text);
        List<String> chunks = new ArrayList<>();
        if (words.isEmpty()) {
            return chunks;
        }
        int step = chunkWords - overlapWords;
        for (int start = 0; ; start += step) {
            int end = Math.min(start + chunkWords, words.size());
            chunks.add(String.join(" ", words.subList(start, end)));
            if (end == words.size()) {
                return chunks;
            }
        }
    }

    public static int wordCount(String text) {
        return words(text).size();
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                words.add(token);
            }
        }
        return words;
    }
}
