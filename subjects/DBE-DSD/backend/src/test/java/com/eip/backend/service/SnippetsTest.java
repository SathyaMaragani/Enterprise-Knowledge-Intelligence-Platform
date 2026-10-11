package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Choosing and highlighting the passage shown under a search hit. */
class SnippetsTest {

    private static Snippets.Snippet choose(List<String> matched, String semantic, String query, boolean allowPrefix) {
        String phrase = LexicalScorer.normalise(query);
        return Snippets.choose(matched, semantic, phrase, LexicalScorer.terms(phrase), allowPrefix);
    }

    /** The highlighted words, read back out of the snippet text. */
    private static List<String> marked(Snippets.Snippet snippet) {
        return snippet.highlights().stream().map(r -> snippet.text().substring(r.start(), r.end())).toList();
    }

    @Test
    void theChunkWithTheMostTermsWins() {
        Snippets.Snippet snippet = choose(
                List.of("Laptops are issued on the first day.", "Report a lost laptop to security within one hour."),
                null, "lost laptop", true);
        assertEquals("Report a lost laptop to security within one hour.", snippet.text());
        assertEquals(List.of("lost", "laptop"), marked(snippet));
    }

    @Test
    void theWholePhraseBeatsTheSameWordsScattered() {
        Snippets.Snippet snippet = choose(
                List.of("Leave is a policy matter; the policy on leave is strict about leave.",
                        "See the leave policy."),
                null, "leave policy", true);
        assertEquals("See the leave policy.", snippet.text());
    }

    @Test
    void aPartlyTypedTermHighlightsTheWholeWord() {
        Snippets.Snippet snippet = choose(List.of("Information security is everyone's job."), null, "secur", true);
        assertEquals(List.of("security"), marked(snippet));
    }

    @Test
    void exactModeHighlightsWholeWordsOnly() {
        assertEquals(List.of("policy"),
                marked(choose(List.of("The policy covers policyholders."), null, "policy", false)));
        // "port" is inside "report", not a word of its own.
        assertNull(choose(List.of(), null, "port", false));
        assertNull(choose(List.of("The annual report."), null, "port", true));
    }

    @Test
    void matchingIgnoresCase() {
        assertEquals(List.of("Kafka"), marked(choose(List.of("Services communicate via Kafka."), null, "kafka", true)));
    }

    @Test
    void aLongChunkIsCutToAWindowAroundTheMatch() {
        String filler = "word ".repeat(120);
        Snippets.Snippet snippet = choose(List.of(filler + "the dynamo project begins " + filler), null, "dynamo", true);
        assertTrue(snippet.text().startsWith("…"));
        assertTrue(snippet.text().endsWith("…"));
        assertTrue(snippet.text().length() <= Snippets.WINDOW + 2, "length " + snippet.text().length());
        // The ranges index the text that is sent, ellipsis included.
        assertEquals(List.of("dynamo"), marked(snippet));
        assertFalse(snippet.text().substring(1).startsWith(" "), "starts at a word");
    }

    @Test
    void aMatchNearTheStartKeepsTheStart() {
        Snippets.Snippet snippet = choose(List.of("Dynamo begins in June. " + "word ".repeat(120)), null, "dynamo", true);
        assertTrue(snippet.text().startsWith("Dynamo begins"));
        assertTrue(snippet.text().endsWith("…"));
    }

    @Test
    void withNoKeywordPassageTheSemanticChunkIsShown() {
        Snippets.Snippet snippet = choose(List.of(), "Employees may work from home two days a week.",
                "remote working rules", true);
        assertEquals("Employees may work from home two days a week.", snippet.text());
        assertTrue(snippet.highlights().isEmpty());
        // A vector-only search has no terms at all.
        assertEquals("Chunk text.", Snippets.choose(List.of(), "Chunk text.", "", List.of(), true).text());
    }

    @Test
    void termsFoundInTheSemanticChunkAreStillHighlighted() {
        assertEquals(List.of("working"),
                marked(choose(List.of(), "Hybrid working is allowed.", "remote working rules", true)));
    }

    @Test
    void nothingToShowIsNull() {
        assertNull(choose(List.of(), null, "anything", true));
        assertNull(choose(List.of(), "   ", "anything", true));
    }

    @Test
    void overlappingMatchesMergeIntoOneHighlight() {
        // "work" (as a prefix) and "working" both match the same word.
        assertEquals(List.of("working"), marked(choose(List.of("Remote working."), null, "work working", true)));
    }
}
