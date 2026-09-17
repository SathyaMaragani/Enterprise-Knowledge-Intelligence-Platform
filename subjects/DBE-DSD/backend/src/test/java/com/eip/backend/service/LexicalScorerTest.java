package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1.7C -- the TextHack-backed lexical scorer, exercised without any
 * database. Expected values are derived from the documented constants rather
 * than hand-typed decimals, so the tests state the scoring rule instead of a
 * number that happens to satisfy it.
 */
class LexicalScorerTest {

    private static final double EPS = 1e-9;

    private final LexicalScorer scorer = new LexicalScorer();

    @Test
    void wholeQueryInTitleScoresTheTopOfTheRange() {
        LexicalScorer.Match match = scorer.score("Leave Policy", "Leave Policy Update", "x");
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE, match.score(), EPS);
        assertFalse(match.fuzzy());
    }

    @Test
    void wholeQueryInDescriptionScoresBelowTitle() {
        LexicalScorer.Match match =
                scorer.score("leave policy", "Onboarding", "mentions the leave policy");
        assertEquals(LexicalScorer.DESCRIPTION_PHRASE_SCORE, match.score(), EPS);
        assertTrue(match.score() < LexicalScorer.TITLE_PHRASE_SCORE);
    }

    @Test
    void reorderedTermsScoreTheCoverageCeilingWithoutFuzz() {
        LexicalScorer.Match match = scorer.score("policy leave", "Leave Policy Update", "x");
        assertEquals(LexicalScorer.COVERAGE_CEILING, match.score(), EPS);
        assertFalse(match.fuzzy());
        assertEquals(2, match.matchedTerms());
        // Reordered terms must never tie with the exact phrase.
        assertTrue(match.score() < LexicalScorer.TITLE_PHRASE_SCORE);
    }

    @Test
    void transposedLettersCountAsOneEdit() {
        // "recieve" -> "receive" is a transposition: one edit under OSA, two under
        // plain Levenshtein. This is why the scorer uses the OSA variant.
        LexicalScorer.Match match = scorer.score("recieve", "How to receive goods", "");
        assertEquals(LexicalScorer.COVERAGE_CEILING * LexicalScorer.ONE_EDIT_CREDIT, match.score(), EPS);
        assertTrue(match.fuzzy());
    }

    @Test
    void twoEditsAreAllowedOnlyForLongTerms() {
        // "archtcture" is two deletions from "architecture" and 10 characters long.
        LexicalScorer.Match longTerm = scorer.score("archtcture", "System Architecture", "");
        assertEquals(LexicalScorer.COVERAGE_CEILING * LexicalScorer.TWO_EDIT_CREDIT, longTerm.score(), EPS);
        assertTrue(longTerm.fuzzy());

        // "plnx" is two edits from "plan" but only 4 characters: one edit allowed.
        LexicalScorer.Match shortTerm = scorer.score("plnx", "Office Plan", "");
        assertEquals(0.0, shortTerm.score(), EPS);
        assertFalse(shortTerm.fuzzy());
    }

    @Test
    void termsOfThreeCharactersOrFewerAreNeverFuzzed() {
        // "nba" is one edit from "nda", but at three letters that edit is a third
        // of the word -- a different query, not a typo.
        LexicalScorer.Match match = scorer.score("nba", "NDA Template", "Standard NDA");
        assertEquals(0.0, match.score(), EPS);
        assertFalse(match.fuzzy());
    }

    @Test
    void stopwordsDoNotDiluteCoverage() {
        LexicalScorer.Match match = scorer.score("the leave policy", "Leave Policy", "");
        assertEquals(LexicalScorer.COVERAGE_CEILING, match.score(), EPS);
    }

    @Test
    void exactTermMatchRequiresAWholeToken() {
        // "policy" occurs inside "policyholder" but is not that word; only "terms"
        // matches, so coverage is one of two terms.
        LexicalScorer.Match match = scorer.score("policy terms", "Policyholder Terms", "");
        assertEquals(LexicalScorer.COVERAGE_CEILING * 0.5, match.score(), EPS);
        assertEquals(1, match.matchedTerms());
    }

    @Test
    void descriptionOnlyTermsAreDiscounted() {
        LexicalScorer.Match match = scorer.score("budget draft", "Unrelated", "draft of the budget");
        assertEquals(LexicalScorer.COVERAGE_CEILING * LexicalScorer.DESCRIPTION_WEIGHT, match.score(), EPS);
    }

    @Test
    void blankAndMissingInputsScoreZero() {
        assertEquals(0.0, scorer.score(null, "Leave Policy", "x").score(), EPS);
        assertEquals(0.0, scorer.score("   ", "Leave Policy", "x").score(), EPS);
        assertEquals(0.0, scorer.score("leave", null, null).score(), EPS);
    }

    @Test
    void internalWhitespaceIsCollapsedBeforePhraseMatching() {
        LexicalScorer.Match match = scorer.score("  leave    policy ", "Leave Policy", "");
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE, match.score(), EPS);
    }

    @Test
    void scoresStayInTheUnitIntervalAndAreDeterministic() {
        String[] queries = {"leave", "policy leave", "recieve", "q1 financial report",
                            "archtcture", "the", "x", "budget draft plan"};
        String[][] documents = {
                {"Leave Policy Update", "Changes to leave policy"},
                {"Q1 Financial Report", "Q1 results"},
                {"System Architecture v2", "New microservices design"},
                {"", ""},
                {"Policyholder Terms", null},
        };
        for (String query : queries) {
            for (String[] document : documents) {
                LexicalScorer.Match first = scorer.score(query, document[0], document[1]);
                LexicalScorer.Match second = scorer.score(query, document[0], document[1]);
                assertTrue(first.score() >= 0.0 && first.score() <= 1.0,
                           query + " scored " + first.score());
                assertEquals(first, second, "scoring must be deterministic");
            }
        }
    }

    @Test
    void fuzzyThresholdScalesWithTermLength() {
        assertEquals(0, LexicalScorer.fuzzyThreshold(3));
        assertEquals(1, LexicalScorer.fuzzyThreshold(4));
        assertEquals(1, LexicalScorer.fuzzyThreshold(7));
        assertEquals(2, LexicalScorer.fuzzyThreshold(8));
    }

    @Test
    void termsDropStopwordsSingleCharactersAndDuplicates() {
        assertEquals(List.of("leave", "policy"), LexicalScorer.terms("the leave leave a policy x"));
    }

    @Test
    void exactOnlyScoringGivesTyposNoCredit() {
        assertTrue(scorer.score("levae policy", "Leave Policy Update", "x").fuzzy());

        LexicalScorer.Match exact = scorer.score("levae policy", "Leave Policy Update", "x", false);
        assertFalse(exact.fuzzy());
        assertEquals(1, exact.matchedTerms());
        assertEquals(LexicalScorer.COVERAGE_CEILING / 2, exact.score(), EPS);
    }
}
