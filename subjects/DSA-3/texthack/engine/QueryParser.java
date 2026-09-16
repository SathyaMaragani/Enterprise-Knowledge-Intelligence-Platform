package texthack.engine;

import texthack.core.IntList;

/**
 * Parser for the small TextHack query language.
 *
 * <pre>
 *   find "needle"                  exact search for one pattern
 *   findall "he" "she" "hers"      all patterns in a single pass
 *   fuzzy "recieve" ~2             within edit distance 2
 *   similar "colour" "color"       normalised similarity
 *   prime 7919                     primality
 * </pre>
 *
 * <h2>Why a hand-written tokenizer</h2>
 * The grammar is small enough that a regex would be shorter, but quoted strings
 * containing spaces are exactly where regex-based splitting quietly goes wrong.
 * Scanning character by character keeps the quoting rule explicit: inside
 * quotes, whitespace is data.
 *
 * <p>Errors are reported with the offending position rather than a bare "parse
 * error", since a query language nobody can debug is worse than no query
 * language.
 */
public final class QueryParser {

    private QueryParser() {
    }

    /**
     * Parses one query.
     *
     * @throws IllegalArgumentException with a specific message on any
     *         malformed input
     */
    public static Query parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String[] tokens = tokenize(input);
        if (tokens.length == 0) {
            throw new IllegalArgumentException("empty query");
        }

        String command = tokens[0].toLowerCase();

        if (command.equals("find")) {
            requireTermCount(tokens, 1, "find expects exactly one quoted pattern");
            return new Query(Query.Kind.FIND, rest(tokens, 1, tokens.length), 0, 0L);
        }

        if (command.equals("findall")) {
            if (tokens.length < 2) {
                throw new IllegalArgumentException("findall expects at least one quoted pattern");
            }
            return new Query(Query.Kind.FIND_ALL, rest(tokens, 1, tokens.length), 0, 0L);
        }

        if (command.equals("fuzzy")) {
            // Trailing ~N is optional and defaults to 1.
            int threshold = 1;
            int end = tokens.length;
            if (tokens.length >= 2 && tokens[tokens.length - 1].startsWith("~")) {
                threshold = parseThreshold(tokens[tokens.length - 1]);
                end = tokens.length - 1;
            }
            if (end - 1 != 1) {
                throw new IllegalArgumentException("fuzzy expects exactly one quoted pattern");
            }
            return new Query(Query.Kind.FUZZY, rest(tokens, 1, end), threshold, 0L);
        }

        if (command.equals("similar")) {
            requireTermCount(tokens, 2, "similar expects exactly two quoted strings");
            return new Query(Query.Kind.SIMILAR, rest(tokens, 1, tokens.length), 0, 0L);
        }

        if (command.equals("prime")) {
            if (tokens.length != 2) {
                throw new IllegalArgumentException("prime expects exactly one integer");
            }
            long value;
            try {
                value = Long.parseLong(tokens[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("prime expects an integer, got: " + tokens[1]);
            }
            return new Query(Query.Kind.PRIME, new String[0], 0, value);
        }

        throw new IllegalArgumentException(
            "unknown command '" + tokens[0] + "'; expected find, findall, fuzzy, similar or prime");
    }

    private static void requireTermCount(String[] tokens, int expected, String message) {
        if (tokens.length - 1 != expected) {
            throw new IllegalArgumentException(message + ", got " + (tokens.length - 1));
        }
    }

    private static int parseThreshold(String token) {
        String digits = token.substring(1);
        int value;
        try {
            value = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed threshold '" + token + "'");
        }
        if (value < 0) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        return value;
    }

    private static String[] rest(String[] tokens, int from, int to) {
        String[] out = new String[to - from];
        for (int i = from; i < to; i++) {
            out[i - from] = tokens[i];
        }
        return out;
    }

    /**
     * Splits on whitespace, except inside double quotes.
     *
     * <p>Quotes are stripped from the result, so a quoted empty string produces
     * an empty token rather than disappearing -- the caller decides whether that
     * is legal, which keeps the tokenizer free of command-specific rules.
     *
     * <p>Public because splitting a query into terms is useful independently of
     * parsing one: a caller can inspect or pre-validate input without committing
     * to a command, and the quoting rule is the part worth testing directly.
     */
    public static String[] tokenize(String input) {
        StringBuilder collected = new StringBuilder();
        IntList tokenStart = new IntList();
        IntList tokenEnd = new IntList();

        int i = 0;
        int length = input.length();

        while (i < length) {
            char c = input.charAt(i);

            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
                continue;
            }

            int begin = collected.length();

            if (c == '"') {
                i++; // opening quote
                boolean closed = false;
                while (i < length) {
                    char inner = input.charAt(i);
                    if (inner == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    collected.append(inner);
                    i++;
                }
                if (!closed) {
                    throw new IllegalArgumentException(
                        "unterminated quote starting at position " + (begin));
                }
            } else {
                while (i < length) {
                    char inner = input.charAt(i);
                    if (inner == ' ' || inner == '\t' || inner == '\r' || inner == '\n') {
                        break;
                    }
                    if (inner == '"') {
                        throw new IllegalArgumentException(
                            "unexpected quote inside a bare word at position " + i);
                    }
                    collected.append(inner);
                    i++;
                }
            }

            tokenStart.add(begin);
            tokenEnd.add(collected.length());
        }

        String joined = collected.toString();
        String[] tokens = new String[tokenStart.size()];
        for (int t = 0; t < tokens.length; t++) {
            tokens[t] = joined.substring(tokenStart.get(t), tokenEnd.get(t));
        }
        return tokens;
    }
}
