package tests;

import texthack.engine.CitationFlow;
import texthack.engine.ComplexityRegistry;
import texthack.engine.Query;
import texthack.engine.QueryParser;
import texthack.engine.TextHack;

/**
 * Self-checking suite for the TextHack engine layer: the public API, the query
 * parser, citation-flow analysis and the complexity registry.
 *
 * <p>The parser gets the most attention, because a query language is the part
 * users touch directly and the part where malformed input is routine rather
 * than exceptional.
 */
public final class EngineTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testTokenizer();
        testParserAcceptsValidQueries();
        testParserRejectsMalformedQueries();
        testFacadeSearch();
        testFacadeFuzzy();
        testFacadeSimilarityAndAlignment();
        testLongestRepeatedSubstring();
        testQueryExecution();
        testCitationFlow();
        testComplexityRegistry();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- parsing

    private static void testTokenizer() {
        section("Tokenizer");

        check("splits on whitespace",
              sameArray(QueryParser.tokenize("find needle"), new String[] {"find", "needle"}));
        check("quotes protect spaces",
              sameArray(QueryParser.tokenize("find \"two words\""),
                        new String[] {"find", "two words"}));
        check("multiple quoted terms",
              sameArray(QueryParser.tokenize("findall \"a b\" \"c d\""),
                        new String[] {"findall", "a b", "c d"}));
        check("repeated whitespace collapses",
              sameArray(QueryParser.tokenize("  find    x  "), new String[] {"find", "x"}));
        check("empty input yields no tokens", QueryParser.tokenize("").length == 0);
        check("quoted empty string is preserved",
              sameArray(QueryParser.tokenize("find \"\""), new String[] {"find", ""}));
        check("tabs are whitespace",
              sameArray(QueryParser.tokenize("find\tx"), new String[] {"find", "x"}));
    }

    private static void testParserAcceptsValidQueries() {
        section("Parser: valid queries");

        Query find = QueryParser.parse("find \"needle\"");
        check("find kind", find.kind() == Query.Kind.FIND);
        check("find term", find.term(0).equals("needle"));

        Query findAll = QueryParser.parse("findall \"he\" \"she\" \"hers\"");
        check("findall kind", findAll.kind() == Query.Kind.FIND_ALL);
        check("findall term count", findAll.termCount() == 3);

        Query fuzzy = QueryParser.parse("fuzzy \"recieve\" ~2");
        check("fuzzy kind", fuzzy.kind() == Query.Kind.FUZZY);
        check("fuzzy threshold", fuzzy.threshold() == 2);
        check("fuzzy term", fuzzy.term(0).equals("recieve"));

        Query defaulted = QueryParser.parse("fuzzy \"teh\"");
        check("fuzzy threshold defaults to 1", defaulted.threshold() == 1);

        Query similar = QueryParser.parse("similar \"colour\" \"color\"");
        check("similar kind", similar.kind() == Query.Kind.SIMILAR);
        check("similar terms", similar.termCount() == 2);

        Query prime = QueryParser.parse("prime 7919");
        check("prime kind", prime.kind() == Query.Kind.PRIME);
        check("prime number", prime.number() == 7919L);

        check("commands are case insensitive",
              QueryParser.parse("FIND \"x\"").kind() == Query.Kind.FIND);
        check("toString round-trips the shape",
              QueryParser.parse("find \"x\"").toString().contains("FIND"));
    }

    private static void testParserRejectsMalformedQueries() {
        section("Parser: malformed queries");

        check("null rejected", throwsIllegalArgument(() -> QueryParser.parse(null)));
        check("empty rejected", throwsIllegalArgument(() -> QueryParser.parse("")));
        check("unknown command rejected",
              throwsIllegalArgument(() -> QueryParser.parse("frobnicate \"x\"")));
        check("find with no term rejected",
              throwsIllegalArgument(() -> QueryParser.parse("find")));
        check("find with two terms rejected",
              throwsIllegalArgument(() -> QueryParser.parse("find \"a\" \"b\"")));
        check("findall with no terms rejected",
              throwsIllegalArgument(() -> QueryParser.parse("findall")));
        check("similar with one term rejected",
              throwsIllegalArgument(() -> QueryParser.parse("similar \"a\"")));
        check("prime without a number rejected",
              throwsIllegalArgument(() -> QueryParser.parse("prime")));
        check("prime with a non-number rejected",
              throwsIllegalArgument(() -> QueryParser.parse("prime abc")));
        check("unterminated quote rejected",
              throwsIllegalArgument(() -> QueryParser.parse("find \"unclosed")));
        check("malformed threshold rejected",
              throwsIllegalArgument(() -> QueryParser.parse("fuzzy \"x\" ~abc")));
    }

    // -------------------------------------------------------------- facade

    private static void testFacadeSearch() {
        section("Facade: pattern search");

        String text = "the quick brown fox jumps over the lazy dog";
        check("exact search finds both occurrences",
              sameArray(TextHack.search(text, "the"), new int[] {0, 31}));
        check("absent pattern finds nothing", TextHack.search(text, "zebra").length == 0);

        check("multi-pattern search finds all",
              TextHack.searchAll("ushers", new String[] {"he", "she", "hers"}).length == 3);

        TextHack engine = new TextHack(new String[] {"alpha beta", "beta gamma", "delta"});
        check("documentsContaining finds the right documents",
              sameArray(engine.documentsContaining("beta"), new int[] {0, 1}));
        check("documentsContaining with no hits",
              engine.documentsContaining("omega").length == 0);
        check("document count", engine.documentCount() == 3);

        check("null corpus rejected", throwsIllegalArgument(() -> new TextHack(null)));
        check("null document rejected",
              throwsIllegalArgument(() -> new TextHack(new String[] {"ok", null})));
    }

    private static void testFacadeFuzzy() {
        section("Facade: fuzzy matching");

        String[] vocabulary = {"receive", "believe", "retrieve", "deceive", "relieve"};
        TextHack.FuzzyHit[] hits = TextHack.fuzzy(vocabulary, "recieve", 2);

        check("fuzzy finds at least the intended word", hits.length >= 1);

        // Measured, not assumed. "relieve" differs from "recieve" by a single
        // substitution (l vs c) and so is distance 1, while "receive" is a
        // transposition and therefore 2 under Levenshtein. An earlier version of
        // this test asserted "receive" came first, which was wrong about the
        // data rather than about the algorithm.
        check("nearest hit is 'relieve' at distance 1",
              hits[0].value().equals("relieve") && hits[0].distance() == 1);

        // The property actually worth pinning: the intended correction is found,
        // at the distance a transposition costs without transposition support.
        boolean receiveFound = false;
        for (TextHack.FuzzyHit hit : hits) {
            if (hit.value().equals("receive")) {
                receiveFound = hit.distance() == 2;
            }
        }
        check("'receive' is found at distance 2", receiveFound);

        boolean ascending = true;
        for (int i = 1; i < hits.length; i++) {
            if (hits[i - 1].distance() > hits[i].distance()) {
                ascending = false;
            }
        }
        check("hits are ordered nearest first", ascending);

        check("threshold 0 only matches exactly",
              TextHack.fuzzy(vocabulary, "receive", 0).length == 1);
        check("no candidates within threshold",
              TextHack.fuzzy(vocabulary, "zzzzzzzz", 1).length == 0);
        check("similarity is reported", hits[0].similarity() > 0.5);

        check("negative threshold rejected",
              throwsIllegalArgument(() -> TextHack.fuzzy(vocabulary, "x", -1)));
        check("null candidates rejected",
              throwsIllegalArgument(() -> TextHack.fuzzy(null, "x", 1)));
    }

    private static void testFacadeSimilarityAndAlignment() {
        section("Facade: similarity and alignment");

        check("identical strings score 1.0", TextHack.similarity("abc", "abc") == 1.0);
        check("similar strings score high", TextHack.similarity("colour", "color") > 0.8);

        check("global alignment spans both",
              TextHack.align("GATTACA", "GATTACA").identity() == 1.0);
        check("local alignment isolates the shared region",
              TextHack.localAlign("xxxSHAREDxxx", "yyySHAREDyyy").score() > 0);

        check("suffix array length matches", TextHack.suffixArray("banana").length == 6);
        check("lcp length matches", TextHack.lcp("banana").length == 6);
        check("prime check via facade", TextHack.isPrime(7919L));
        check("composite check via facade", !TextHack.isPrime(7917L));
    }

    private static void testLongestRepeatedSubstring() {
        section("Facade: longest repeated substring");

        check("banana repeats 'ana'",
              TextHack.longestRepeatedSubstring("banana").equals("ana"));
        check("no repeat yields empty",
              TextHack.longestRepeatedSubstring("abcdef").isEmpty());
        check("full repeat", TextHack.longestRepeatedSubstring("abcabc").equals("abc"));
        check("empty text yields empty",
              TextHack.longestRepeatedSubstring("").isEmpty());
        check("null rejected",
              throwsIllegalArgument(() -> TextHack.longestRepeatedSubstring(null)));
    }

    private static void testQueryExecution() {
        section("Query execution");

        String[] corpus = {
            "network flow algorithms",
            "string matching algorithms",
            "randomized primality"
        };
        TextHack engine = new TextHack(corpus);

        String[] find = engine.run("find \"algorithms\"");
        check("find reports two documents", find[0].contains("2 document"));

        String[] findAll = engine.run("findall \"flow\" \"matching\"");
        check("findall reports a total", findAll[0].contains("match"));
        check("findall lists every document", findAll.length == corpus.length + 1);

        String[] similar = engine.run("similar \"colour\" \"color\"");
        check("similar returns one line", similar.length == 1);

        String[] prime = engine.run("prime 7919");
        check("prime reports prime", prime[0].contains("is prime"));

        String[] composite = engine.run("prime 7917");
        check("composite reports composite", composite[0].contains("composite"));

        check("null query rejected", throwsIllegalArgument(() -> engine.execute(null)));
    }

    // -------------------------------------------------------- citation flow

    private static void testCitationFlow() {
        section("Citation flow");

        // Two edge-disjoint paths from 0 to 3.
        CitationFlow citations = new CitationFlow(4);
        citations.addCitation(0, 1);
        citations.addCitation(1, 3);
        citations.addCitation(0, 2);
        citations.addCitation(2, 3);

        check("two edge-disjoint paths give influence 2", citations.influence(0, 3) == 2L);
        check("min cut weight matches the flow",
              citations.bottleneckWeight(0, 3) == citations.influence(0, 3));

        boolean[] sourceSide = citations.bottleneck(0, 3);
        check("source is on the source side", sourceSide[0]);
        check("sink is not on the source side", !sourceSide[3]);

        // A single shared reference is the bottleneck.
        CitationFlow funnel = new CitationFlow(4);
        funnel.addCitation(0, 1);
        funnel.addCitation(0, 2);
        funnel.addCitation(1, 3);
        check("a single outgoing citation caps the flow", funnel.influence(0, 3) == 1L);

        CitationFlow disconnected = new CitationFlow(3);
        disconnected.addCitation(0, 1);
        check("unreachable document has zero influence", disconnected.influence(0, 2) == 0L);

        check("self-citation rejected",
              throwsIllegalArgument(() -> new CitationFlow(3).addCitation(1, 1)));
        check("zero documents rejected", throwsIllegalArgument(() -> new CitationFlow(0)));
    }

    // --------------------------------------------------- complexity registry

    private static void testComplexityRegistry() {
        section("Complexity registry");

        check("registry is populated", ComplexityRegistry.size() >= 20);
        check("all() matches size", ComplexityRegistry.all().length == ComplexityRegistry.size());

        ComplexityRegistry.Entry kmp = ComplexityRegistry.byName("KMP");
        check("KMP is registered", kmp != null);
        check("KMP time bound", kmp != null && kmp.time().equals("O(n+m)"));
        check("KMP category", kmp != null && kmp.category().equals("String"));

        check("unknown name yields null", ComplexityRegistry.byName("Nonexistent") == null);

        check("string category is populated",
              ComplexityRegistry.byCategory("String").length >= 7);
        check("DP category is populated",
              ComplexityRegistry.byCategory("DP").length >= 5);
        check("graph category is populated",
              ComplexityRegistry.byCategory("Graph").length >= 4);
        check("randomized category is populated",
              ComplexityRegistry.byCategory("Randomized").length >= 3);
        check("unknown category yields empty",
              ComplexityRegistry.byCategory("Imaginary").length == 0);

        // Every entry must actually carry its bounds, or the registry is
        // decorative rather than useful.
        boolean complete = true;
        for (ComplexityRegistry.Entry entry : ComplexityRegistry.all()) {
            if (entry.name().isEmpty() || entry.time().isEmpty() || entry.space().isEmpty()) {
                complete = false;
            }
        }
        check("every entry carries name, time and space", complete);
    }

    // --------------------------------------------------------------- helpers

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean sameArray(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameArray(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static void section(String title) {
        System.out.println("--- " + title + " ---");
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + description);
        } else {
            failed++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
