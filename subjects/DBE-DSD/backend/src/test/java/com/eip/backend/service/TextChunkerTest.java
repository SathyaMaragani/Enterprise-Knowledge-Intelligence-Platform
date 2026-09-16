package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    private static String words(int from, int toInclusive) {
        return IntStream.rangeClosed(from, toInclusive).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    @Test
    void shortTextIsOneChunk() {
        assertEquals(List.of("alpha beta gamma"), TextChunker.chunk("  alpha\n\tbeta   gamma \n"));
    }

    @Test
    void blankTextHasNoChunks() {
        assertEquals(List.of(), TextChunker.chunk("   \n\t "));
        assertEquals(List.of(), TextChunker.chunk(null));
    }

    @Test
    void windowsOverlapByTheRequestedWordCount() {
        // 10 words, windows of 4 with overlap 1: step 3 -> 1-4, 4-7, 7-10.
        assertEquals(List.of(words(1, 4), words(4, 7), words(7, 10)), TextChunker.chunk(words(1, 10), 4, 1));
    }

    @Test
    void lastWindowStopsAtTheEndWithoutAnEmptyTail() {
        // 9 words, windows of 4 with overlap 1: 1-4, 4-7, 7-9. No fourth chunk.
        assertEquals(List.of(words(1, 4), words(4, 7), words(7, 9)), TextChunker.chunk(words(1, 9), 4, 1));
    }

    @Test
    void defaultWindowsMatchTheMlPipeline() {
        List<String> chunks = TextChunker.chunk(words(1, 400));

        // step 140: 1-180, 141-320, 281-400
        assertEquals(3, chunks.size());
        assertEquals(words(1, 180), chunks.get(0));
        assertEquals(words(141, 320), chunks.get(1));
        assertEquals(words(281, 400), chunks.get(2));
    }

    @Test
    void everyWordAppearsInSomeChunk() {
        String text = words(1, 1000);
        List<String> chunks = TextChunker.chunk(text, 50, 10);
        for (int i = 1; i <= 1000; i++) {
            String word = "w" + i;
            assertTrue(chunks.stream().anyMatch(chunk -> (" " + chunk + " ").contains(" " + word + " ")), word);
        }
    }

    @Test
    void rejectsImpossibleWindows() {
        assertThrows(IllegalArgumentException.class, () -> TextChunker.chunk("a", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TextChunker.chunk("a", 5, 5));
        assertThrows(IllegalArgumentException.class, () -> TextChunker.chunk("a", 5, -1));
    }

    @Test
    void countsWords() {
        assertEquals(3, TextChunker.wordCount(" one\ttwo\nthree "));
        assertEquals(0, TextChunker.wordCount(""));
    }
}
