package com.eip.backend.controller;

import com.eip.backend.dto.texthack.TextHackDtos.AlignmentView;
import com.eip.backend.dto.texthack.TextHackDtos.Citation;
import com.eip.backend.dto.texthack.TextHackDtos.CitationRequest;
import com.eip.backend.dto.texthack.TextHackDtos.CitationResponse;
import com.eip.backend.dto.texthack.TextHackDtos.ComplexityEntry;
import com.eip.backend.dto.texthack.TextHackDtos.PatternMatch;
import com.eip.backend.dto.texthack.TextHackDtos.PatternRequest;
import com.eip.backend.dto.texthack.TextHackDtos.PatternResponse;
import com.eip.backend.dto.texthack.TextHackDtos.SimilarityRequest;
import com.eip.backend.dto.texthack.TextHackDtos.SimilarityResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import texthack.dp.Alignment;
import texthack.dp.DamerauLevenshtein;
import texthack.dp.Levenshtein;
import texthack.engine.CitationFlow;
import texthack.engine.ComplexityRegistry;
import texthack.engine.TextHack;
import texthack.string.AhoCorasick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Demonstrations of the DSA-3 TextHack engine on caller-supplied input. Nothing
 * here reads documents, so any signed-in user may call it.
 *
 * <p>Deliberately thin: every result is computed by the engine, which DSA-3
 * tests on its own. Out-of-range input the engine rejects surfaces as 400.
 */
@RestController
@RequestMapping("/api/texthack")
public class TextHackController {

    /** One pattern: KMP positions. Several: one Aho-Corasick pass, which keeps pattern identity. */
    @PostMapping("/pattern")
    public PatternResponse pattern(@Valid @RequestBody PatternRequest request) {
        String text = request.text();
        List<String> patterns = request.patterns();
        List<PatternMatch> matches = new ArrayList<>();
        String algorithm;
        if (patterns.size() == 1) {
            algorithm = "KMP";
            String pattern = patterns.get(0);
            for (int start : TextHack.search(text, pattern)) {
                matches.add(new PatternMatch(pattern, start, start + pattern.length()));
            }
        } else {
            algorithm = "Aho-Corasick";
            for (AhoCorasick.Match match : TextHack.searchAll(text, patterns.toArray(String[]::new))) {
                matches.add(new PatternMatch(patterns.get(match.patternIndex()), match.start(), match.end()));
            }
        }
        return new PatternResponse(algorithm, matches, TextHack.longestRepeatedSubstring(text));
    }

    @PostMapping("/similarity")
    public SimilarityResponse similarity(@Valid @RequestBody SimilarityRequest request) {
        String first = request.first();
        String second = request.second();
        return new SimilarityResponse(
                Levenshtein.distance(first, second),
                DamerauLevenshtein.distance(first, second),
                TextHack.similarity(first, second),
                view(TextHack.align(first, second)),
                view(TextHack.localAlign(first, second)));
    }

    @PostMapping("/citations")
    public CitationResponse citations(@Valid @RequestBody CitationRequest request) {
        int documents = request.documents();
        if (request.source() < 0 || request.source() >= documents || request.sink() < 0 || request.sink() >= documents) {
            throw new IllegalArgumentException("Source and sink must be documents 0 to " + (documents - 1));
        }
        if (request.source() == request.sink()) {
            throw new IllegalArgumentException("Source and sink must be different documents");
        }

        CitationFlow flow = new CitationFlow(documents);
        for (Citation citation : request.citations()) {
            flow.addCitation(citation.from(), citation.to());
        }

        boolean[] sourceSide = flow.bottleneck(request.source(), request.sink());
        List<Integer> side = new ArrayList<>();
        for (int i = 0; i < documents; i++) {
            if (sourceSide[i]) {
                side.add(i);
            }
        }
        List<Citation> bottleneck = request.citations().stream()
                .filter(c -> sourceSide[c.from()] && !sourceSide[c.to()])
                .toList();
        return new CitationResponse(flow.influence(request.source(), request.sink()), side, bottleneck);
    }

    @GetMapping("/complexity")
    public List<ComplexityEntry> complexity() {
        return Arrays.stream(ComplexityRegistry.all())
                .map(e -> new ComplexityEntry(e.name(), e.category(), e.time(), e.space(), e.note()))
                .toList();
    }

    private static AlignmentView view(Alignment a) {
        return new AlignmentView(a.alignedFirst(), a.alignedSecond(), a.score(), a.identity(),
                a.firstStart(), a.firstEnd(), a.secondStart(), a.secondEnd());
    }
}
