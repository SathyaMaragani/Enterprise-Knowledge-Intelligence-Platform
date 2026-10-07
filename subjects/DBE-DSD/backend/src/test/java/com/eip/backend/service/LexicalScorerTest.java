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

    @Test
    void theBodyCountsBelowTheTitleAndDescription() {
        LexicalScorer.BodyEvidence phraseInBody = new LexicalScorer.BodyEvidence(true, new boolean[]{true, true});
        LexicalScorer.BodyEvidence termsInBody = new LexicalScorer.BodyEvidence(false, new boolean[]{true, true});

        assertEquals(LexicalScorer.BODY_PHRASE_SCORE,
                scorer.score("leave policy", "Onboarding", "x", phraseInBody, true).score(), EPS);
        // Every term somewhere in the body is enough to be a hit on its own.
        LexicalScorer.Match inBody = scorer.score("leave policy", "Onboarding", "x", termsInBody, true);
        assertEquals(LexicalScorer.COVERAGE_CEILING * LexicalScorer.BODY_WEIGHT, inBody.score(), EPS);
        assertTrue(LexicalScorer.coversEnough(inBody));
        // The title and description still win.
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE,
                scorer.score("leave policy", "Leave Policy", "x", phraseInBody, true).score(), EPS);
        assertEquals(LexicalScorer.DESCRIPTION_PHRASE_SCORE,
                scorer.score("leave policy", "x", "the leave policy", phraseInBody, true).score(), EPS);
    }

    @Test
    void enoughTermsDecidesAHitNotWhereTheyMatched() {
        // A one-word typo matching only the description scores 0.9 * 0.6 * 0.7 = 0.378,
        // below the old 0.5 floor, yet the query is fully covered.
        LexicalScorer.Match typo = scorer.score("phising", "Security Standard", "phishing reporting", true);
        assertTrue(typo.fuzzy());
        assertTrue(LexicalScorer.coversEnough(typo));
        // Two-term queries need both terms; longer ones 60% of them.
        assertFalse(LexicalScorer.coversEnough(scorer.score("leave policy", "Policy Notes", "x", true)));
        assertTrue(LexicalScorer.coversEnough(
                scorer.score("annual leave days carry", "Annual Leave Policy", "days off", true)));
        assertFalse(LexicalScorer.coversEnough(
                scorer.score("annual leave days carry over", "Annual Notes", "x", true)));
        // Nothing to match is never enough.
        assertFalse(LexicalScorer.coversEnough(scorer.score("the of and", "x", "y", true)));
    }

    @Test
    void anExactWordInTheBodyBeatsATypoInTheDescription() {
        LexicalScorer.BodyEvidence inBody = new LexicalScorer.BodyEvidence(false, new boolean[]{true});
        LexicalScorer.Match match = scorer.score("policy", "Notes", "the polcy notes", inBody, true);
        assertFalse(match.fuzzy());
        assertEquals(LexicalScorer.COVERAGE_CEILING * LexicalScorer.BODY_WEIGHT, match.score(), EPS);
    }
}
