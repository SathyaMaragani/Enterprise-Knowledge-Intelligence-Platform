package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Correcting query words against the words the documents contain. */
class QueryCorrectorTest {

    /** Word to the number of documents containing it. */
    private static final Map<String, Integer> VOCABULARY = Map.of(
            "dynamo", 8, "laptops", 15, "phishing", 15, "everest", 15, "standard", 60,
            "policy", 90, "police", 1, "security", 15, "awareness", 15, "aware", 40);

    private static QueryCorrector.Correction correct(String query) {
        return QueryCorrector.correct(LexicalScorer.normalise(query), VOCABULARY);
    }

    @Test
    void correctsMisspelledNamesAndWords() {
        assertEquals("dynamo", correct("Dynmo").query());
        assertEquals("laptops", correct("laptps").query());
        assertEquals("everest", correct("Evrest").query());
        assertEquals(LexicalScorer.typoCredit(1, 5, 6), correct("Dynmo").credit(), 1e-9);
    }

    @Test
    void wordsOfSixLettersOrMoreMayHaveTwoMistakes() {
        QueryCorrector.Correction c = correct("secrity awarnes");
        assertEquals("security awareness", c.query());
        // "awarnes" is two edits from both "aware" and "awareness", but gets more of
        // "awareness" right. The query's credit is its worst word's.
        assertEquals(LexicalScorer.typoCredit(2, 7, 9), c.credit(), 1e-9);
        assertEquals("phishing", correct("phshng").query());
    }

    @Test
    void aWordStillBeingTypedIsNotATypo() {
        // "secur" and "awar" begin document words; the scorer matches them as prefixes.
        assertNull(correct("secur"));
        assertNull(correct("awar"));
    }

    @Test
    void leavesKnownShortStopAndNumericWordsAlone() {
        assertNull(correct("phishing standard"));           // already in the documents
        assertNull(correct("nba"));                        // three letters: no typo tolerance
        assertNull(correct("2024"));                       // a year, not a typo
        assertNull(correct("kubernetes"));                 // nothing close enough
        // Stopwords and punctuation survive a correction elsewhere in the query.
        assertEquals("what is the dynamo?", correct("What is the dynmo?").query());
    }

    @Test
    void prefersTheWordInMoreDocuments() {
        // "polica" is one edit from both "policy" (90 documents) and "police" (1).
        assertEquals("policy", correct("polica").query());
    }
}
