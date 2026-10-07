package com.eip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The TextHack-backed lexical scorer, exercised without any database. Expected
 * values are derived from the documented constants and credit formulas rather
 * than hand-typed decimals, so the tests state the scoring rule instead of a
 * number that happens to satisfy it.
 */
class LexicalScorerTest {

    private static final double EPS = 1e-9;
    private static final double CEIL = LexicalScorer.COVERAGE_CEILING;

    private final LexicalScorer scorer = new LexicalScorer();

    private static LexicalScorer.BodyEvidence body(boolean phrase, double... credits) {
        return new LexicalScorer.BodyEvidence(phrase, credits);
    }

    // ---------------------------------------------------------------- exact matches

    @Test
    void wholeQueryInTitleScoresTheTopOfTheRange() {
        LexicalScorer.Match match = scorer.score("Leave Policy", "Leave Policy Update", "x");
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE, match.score(), EPS);
        assertFalse(match.fuzzy());
    }

    @Test
    void wholeQueryInDescriptionScoresJustBelowTitle() {
        LexicalScorer.Match match = scorer.score("leave policy", "Onboarding", "mentions the leave policy");
        assertEquals(LexicalScorer.DESCRIPTION_PHRASE_SCORE, match.score(), EPS);
        assertTrue(match.score() < LexicalScorer.TITLE_PHRASE_SCORE);
    }

    @Test
    void anExactWordInTheTextScoresHighNotTwoThirds() {
        // The reason for this scale: an exact word in a document used to read as 67%.
        LexicalScorer.Match match = scorer.score("laptops", "Security Standard", "x", body(true, 1.0), true);
        assertEquals(LexicalScorer.BODY_PHRASE_SCORE, match.score(), EPS);
        assertTrue(match.score() >= 0.9);
        assertFalse(match.fuzzy());
    }

    @Test
    void reorderedTermsScoreTheCoverageCeilingWithoutFuzz() {
        LexicalScorer.Match match = scorer.score("policy leave", "Leave Policy Update", "x");
        assertEquals(CEIL, match.score(), EPS);
        assertFalse(match.fuzzy());
        assertEquals(2, match.matchedTerms());
        // Reordered terms must never tie with the exact phrase.
        assertTrue(match.score() < LexicalScorer.TITLE_PHRASE_SCORE);
    }

    @Test
    void stopwordsDoNotDiluteCoverage() {
        assertEquals(CEIL, scorer.score("the leave policy", "Leave Policy", "").score(), EPS);
    }

    @Test
    void descriptionAndTextTermsCountSlightlyLess() {
        assertEquals(CEIL * LexicalScorer.DESCRIPTION_WEIGHT,
                scorer.score("budget draft", "Unrelated", "draft of the budget").score(), EPS);
        LexicalScorer.Match inText = scorer.score("leave policy", "Onboarding", "x", body(false, 1.0, 1.0), true);
        assertEquals(CEIL * LexicalScorer.BODY_WEIGHT, inText.score(), EPS);
        assertTrue(LexicalScorer.coversEnough(inText));
        // The title and description still rank first.
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE,
                scorer.score("leave policy", "Leave Policy", "x", body(true, 1.0, 1.0), true).score(), EPS);
        assertEquals(LexicalScorer.DESCRIPTION_PHRASE_SCORE,
                scorer.score("leave policy", "x", "the leave policy", body(true, 1.0, 1.0), true).score(), EPS);
    }

    @Test
    void aWordInsideAnotherWordIsNotThatWord() {
        // "port" occurs inside "report" but is not that word, nor its beginning.
        LexicalScorer.Match match = scorer.score("port", "Annual Report", "");
        assertEquals(0.0, match.score(), EPS);
    }

    // ---------------------------------------------------------------- partly typed words

    @Test
    void aPartlyTypedWordMatchesTheWordItBegins() {
        // "secur" begins "security": the phrase ends inside a word.
        LexicalScorer.Match match = scorer.score("secur", "Information Security Awareness", "");
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE * LexicalScorer.PARTIAL_PHRASE_FACTOR, match.score(), EPS);
        assertTrue(match.fuzzy());
    }

    @Test
    void severalPartlyTypedWordsMatchTogether() {
        LexicalScorer.Match match = scorer.score("quart fin", "Quarterly Financial Report Q3", "");
        double expected = CEIL * (LexicalScorer.prefixCredit(5, 9) + LexicalScorer.prefixCredit(3, 9)) / 2;
        assertEquals(expected, match.score(), EPS);
        assertEquals(2, match.matchedTerms());
        assertTrue(match.fuzzy());
        assertTrue(LexicalScorer.coversEnough(match));
    }

    @Test
    void theMoreOfAWordTypedTheHigherItsCredit() {
        assertTrue(LexicalScorer.prefixCredit(3, 8) < LexicalScorer.prefixCredit(5, 8));
        assertTrue(LexicalScorer.prefixCredit(7, 8) < 1.0);
        // A word begun is worth less than the word itself, here "policy" in "policyholder".
        LexicalScorer.Match match = scorer.score("policy terms", "Policyholder Terms", "");
        assertEquals(CEIL * (LexicalScorer.prefixCredit(6, 12) + 1.0) / 2, match.score(), EPS);
    }

    @Test
    void twoLettersAreTooFewToMatchAsAPrefix() {
        assertEquals(0.0, scorer.score("qu fi", "Quarterly Financial Report", "").score(), EPS);
    }

    // ---------------------------------------------------------------- misspellings

    @Test
    void transposedLettersCountAsOneEdit() {
        // "recieve" -> "receive" is one edit under OSA, two under plain Levenshtein.
        LexicalScorer.Match match = scorer.score("recieve", "How to receive goods", "");
        assertEquals(CEIL * LexicalScorer.typoCredit(1, 7, 7), match.score(), EPS);
        assertTrue(match.fuzzy());
    }

    @Test
    void wordsOfSixLettersOrMoreMayHaveTwoMistakes() {
        // "secrty" is two edits from "security": allowed at six letters.
        LexicalScorer.Match six = scorer.score("secrty", "Security Standard", "");
        assertEquals(CEIL * LexicalScorer.typoCredit(2, 6, 8), six.score(), EPS);
        assertTrue(six.fuzzy());
        // "archtcture" is two deletions from "architecture".
        assertEquals(CEIL * LexicalScorer.typoCredit(2, 10, 12),
                scorer.score("archtcture", "System Architecture", "").score(), EPS);
        // "plnx" is two edits from "plan" but only four letters: one edit allowed.
        assertEquals(0.0, scorer.score("plnx", "Office Plan", "").score(), EPS);
    }

    @Test
    void closerMisspellingsEarnMore() {
        assertTrue(LexicalScorer.typoCredit(1, 8, 8) > LexicalScorer.typoCredit(2, 8, 8));
        assertTrue(LexicalScorer.typoCredit(1, 12, 12) > LexicalScorer.typoCredit(1, 5, 5));
    }

    @Test
    void termsOfThreeCharactersOrFewerAreNeverFuzzed() {
        // "nba" is one edit from "nda", but at three letters that edit is a third
        // of the word: a different query, not a typo.
        LexicalScorer.Match match = scorer.score("nba", "NDA Template", "Standard NDA");
        assertEquals(0.0, match.score(), EPS);
        assertFalse(match.fuzzy());
    }

    @Test
    void fuzzyThresholdScalesWithTermLength() {
        assertEquals(0, LexicalScorer.fuzzyThreshold(3));
        assertEquals(1, LexicalScorer.fuzzyThreshold(4));
        assertEquals(1, LexicalScorer.fuzzyThreshold(5));
        assertEquals(2, LexicalScorer.fuzzyThreshold(6));
        assertEquals(2, LexicalScorer.fuzzyThreshold(12));
    }

    @Test
    void exactOnlyScoringGivesTyposAndPartWordsNoCredit() {
        assertTrue(scorer.score("levae policy", "Leave Policy Update", "x").fuzzy());

        LexicalScorer.Match exact = scorer.score("levae policy", "Leave Policy Update", "x", false);
        assertFalse(exact.fuzzy());
        assertEquals(1, exact.matchedTerms());
        assertEquals(CEIL / 2, exact.score(), EPS);
        assertEquals(0.0, scorer.score("secur", "Information Security", "", false).score(), EPS);
    }

    @Test
    void anExactWordInTheTextBeatsATypoInTheDescription() {
        LexicalScorer.Match match = scorer.score("policy", "Notes", "the polcy notes", body(false, 1.0), true);
        assertFalse(match.fuzzy());
        assertEquals(CEIL * LexicalScorer.BODY_WEIGHT, match.score(), EPS);
    }

    // ---------------------------------------------------------------- hits and housekeeping

    @Test
    void enoughTermsDecidesAHitNotWhereTheyMatched() {
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
    void blankAndMissingInputsScoreZero() {
        assertEquals(0.0, scorer.score(null, "Leave Policy", "x").score(), EPS);
        assertEquals(0.0, scorer.score("   ", "Leave Policy", "x").score(), EPS);
        assertEquals(0.0, scorer.score("leave", null, null).score(), EPS);
    }

    @Test
    void internalWhitespaceIsCollapsedBeforePhraseMatching() {
        assertEquals(LexicalScorer.TITLE_PHRASE_SCORE, scorer.score("  leave    policy ", "Leave Policy", "").score(), EPS);
    }

    @Test
    void scoresStayInTheUnitIntervalAndAreDeterministic() {
        String[] queries = {"leave", "policy leave", "recieve", "q1 financial report", "secur", "quart fin",
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
                assertTrue(first.score() >= 0.0 && first.score() <= 1.0, query + " scored " + first.score());
                assertEquals(first, second, "scoring must be deterministic");
            }
        }
    }

    @Test
    void termsDropStopwordsSingleCharactersAndDuplicates() {
        assertEquals(List.of("leave", "policy"), LexicalScorer.terms("the leave leave a policy x"));
    }
}
