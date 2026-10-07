package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Correcting query words against the words the documents contain. */
class QueryCorrectorTest {

    /** Word to the number of documents containing it. */
    private static final Map<String, Integer> VOCABULARY = Map.of(
            "dynamo", 8, "laptops", 15, "phishing", 15, "everest", 15, "standard", 60,
            "policy", 90, "polity", 1, "budget", 15, "remote", 15, "working", 15);

    private static QueryCorrector.Correction correct(String query) {
        return QueryCorrector.correct(LexicalScorer.normalise(query), VOCABULARY);
    }

    @Test
    void correctsMisspelledNamesAndWords() {
        assertEquals("dynamo", correct("Dynmo").query());
        assertEquals("laptops", correct("laptps").query());
        assertEquals("everest", correct("Evrest").query());
        assertEquals(LexicalScorer.ONE_EDIT_CREDIT, correct("Dynmo").credit());
    }

    @Test
    void longWordsMayBeTwoEditsAwayAndCostMore() {
        QueryCorrector.Correction c = correct("phshng");   // too short for two edits
        assertNull(c);
        c = correct("remte workng standrd");
        assertEquals("remote working standard", c.query());
        assertEquals(LexicalScorer.ONE_EDIT_CREDIT, c.credit());
        c = correct("stnadrad");                           // 8 letters, two edits
        assertEquals("standard", c.query());
        assertEquals(LexicalScorer.TWO_EDIT_CREDIT, c.credit());
    }

    @Test
    void leavesKnownShortStopAndNumericWordsAlone() {
        assertNull(correct("phishing budget"));            // already in the documents
        assertNull(correct("nba"));                        // three letters: no typo tolerance
        assertNull(correct("2024"));                       // a year, not a typo
        assertNull(correct("kubernetes"));                 // nothing close enough
        // Stopwords and punctuation survive a correction elsewhere in the query.
        assertEquals("what is the dynamo?", correct("What is the dynmo?").query());
    }

    @Test
    void prefersTheWordInMoreDocuments() {
        // "polcy" is one edit from both "policy" (90 documents) and "polity" (1).
        assertEquals("policy", correct("polcy").query());
    }
}
