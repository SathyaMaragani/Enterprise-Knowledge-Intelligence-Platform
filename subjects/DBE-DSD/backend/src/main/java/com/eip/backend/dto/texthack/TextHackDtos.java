package com.eip.backend.dto.texthack;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request and response shapes for the TextHack demonstrations.
 *
 * <p>The limits bound the work one request can cause: alignment is O(n*m) in
 * time and memory, so its inputs are the tightest.
 */
public final class TextHackDtos {

    private TextHackDtos() {
    }

    public record PatternRequest(
            @NotNull(message = "Text is required")
            @Size(max = 20000, message = "Text must be at most 20000 characters")
            String text,

            @NotEmpty(message = "At least one pattern is required")
            @Size(max = 20, message = "At most 20 patterns")
            List<@NotEmpty(message = "Patterns must not be empty")
                 @Size(max = 200, message = "Patterns must be at most 200 characters") String> patterns) {
    }

    public record PatternMatch(String pattern, int start, int end) {
    }

    /** {@code longestRepeated} comes from the suffix and LCP arrays of the text. */
    public record PatternResponse(String algorithm, List<PatternMatch> matches, String longestRepeated) {
    }

    public record SimilarityRequest(
            @NotNull(message = "First text is required")
            @Size(max = 1000, message = "First text must be at most 1000 characters")
            String first,

            @NotNull(message = "Second text is required")
            @Size(max = 1000, message = "Second text must be at most 1000 characters")
            String second) {
    }

    public record AlignmentView(String alignedFirst, String alignedSecond, int score, double identity,
                                int firstStart, int firstEnd, int secondStart, int secondEnd) {
    }

    public record SimilarityResponse(int levenshteinDistance, int damerauDistance, double similarity,
                                     AlignmentView global, AlignmentView local) {
    }

    public record Citation(int from, int to) {
    }

    public record CitationRequest(
            @Min(value = 2, message = "At least 2 documents")
            @Max(value = 100, message = "At most 100 documents")
            int documents,

            @NotNull(message = "Citations are required")
            @Size(max = 1000, message = "At most 1000 citations")
            List<@NotNull(message = "Citations must not be null") @Valid Citation> citations,

            int source,
            int sink) {
    }

    /**
     * {@code influence} is the Dinic max flow, i.e. the number of edge-disjoint
     * citation paths. {@code bottleneck} is the minimum cut found separately by
     * Edmonds-Karp; its size equals the influence.
     */
    public record CitationResponse(long influence, List<Integer> sourceSide, List<Citation> bottleneck) {
    }

    public record ComplexityEntry(String name, String category, String time, String space, String note) {
    }
}
