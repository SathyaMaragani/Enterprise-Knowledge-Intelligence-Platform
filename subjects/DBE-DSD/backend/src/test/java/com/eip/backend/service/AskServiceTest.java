package com.eip.backend.service;

import com.eip.backend.dto.search.SearchHit;
import com.eip.backend.dto.search.SearchRequest;
import com.eip.backend.dto.search.SearchResponse;
import com.eip.backend.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eip.backend.service.LlmService.Provider.GEMINI;
import static com.eip.backend.service.LlmService.Provider.GROQ;
import static org.junit.jupiter.api.Assertions.*;

/** Turning search hits into numbered sources, and a model's reply into a cited answer. */
class AskServiceTest {

    private List<SearchHit> hits = List.of();
    private Map<Integer, BodyTextMatcher.Passages> passages = Map.of();
    private boolean mongoDown;
    private String reply = "Twenty days a year [1].";
    private String keys = "k";

    private SearchRequest searched;
    private Collection<String> chunksAskedFor;
    private String systemSeen;
    private String promptSeen;
    private LlmService.Provider providerSeen;

    private AskService service() {
        SearchService search = new SearchService(null, null, null, null, null, null, null) {
            @Override
            public SearchResponse search(SearchRequest request) {
                searched = request;
                SearchResponse response = new SearchResponse();
                response.setHits(hits);
                return response;
            }
        };
        BodyTextMatcher bodies = new BodyTextMatcher(null) {
            @Override
            public Map<Integer, Passages> passages(Collection<Integer> documentIds, List<String> terms,
                                                   boolean allowPrefix, Collection<String> chunkIds) {
                if (mongoDown) {
                    throw new IllegalStateException("MongoDB is down");
                }
                chunksAskedFor = chunkIds;
                return passages;
            }
        };
        LlmService llm = new LlmService(keys, "groq-model", "http://unused", "", "gemini-model", "http://unused") {
            @Override
            public Reply complete(String system, String user, Provider preferred) {
                systemSeen = system;
                promptSeen = user;
                providerSeen = preferred;
                return new Reply(reply, GROQ, "groq-model");
            }
        };
        return new AskService(search, bodies, llm);
    }

    private static SearchHit hit(int id, String title, String category, String description, String chunkId) {
        SearchHit hit = new SearchHit();
        hit.setDocumentId(id);
        hit.setTitle(title);
        hit.setCategory(category);
        hit.setDescription(description);
        hit.setChunkId(chunkId);
        return hit;
    }

    @Test
    void theModelGetsTheBestPassagesAsNumberedSourcesAndItsCitationsAreMarked() {
        hits = List.of(
                hit(7, "Leave Policy", "HR", "Rules for time off", "c-9"),
                hit(3, "Travel Policy", null, "Booking trips", null));
        passages = Map.of(
                7, new BodyTextMatcher.Passages(
                        List.of("Sick leave needs a note.", "Staff get 20 days of annual leave.", "Leave is unpaid."),
                        Map.of("c-9", "Unused days carry over to March.")),
                3, new BodyTextMatcher.Passages(List.of(), Map.of()));

        AskService.Answer answer = service().ask("  How many days of annual leave do I get? ", null);

        assertEquals("How many days of annual leave do I get?", searched.getQuery().strip());
        assertEquals(AskService.MAX_SOURCES, searched.getSize());
        assertEquals(List.of("c-9"), List.copyOf(chunksAskedFor));
        assertEquals(AskService.SYSTEM, systemSeen);
        assertNull(providerSeen);
        // The two chunks sharing most words with the question, then the vector search's chunk.
        // The second hit matched on its title only, so its description stands in.
        assertEquals("""
                Sources:
                [1] Leave Policy (HR)
                Staff get 20 days of annual leave.
                …
                Sick leave needs a note.
                …
                Unused days carry over to March.

                [2] Travel Policy
                Booking trips

                Question: How many days of annual leave do I get?""", promptSeen);

        assertEquals("Twenty days a year [1].", answer.answer());
        assertEquals("groq", answer.provider());
        assertEquals("groq-model", answer.model());
        assertEquals(List.of(7, 3), answer.sources().stream().map(AskService.Source::documentId).toList());
        assertEquals(List.of(true, false), answer.sources().stream().map(AskService.Source::cited).toList());
        assertEquals("Booking trips", answer.sources().get(1).passage());
    }

    @Test
    void theChunkFoundBothWaysIsSentOnce() {
        hits = List.of(hit(7, "Leave Policy", "HR", null, "c-2"));
        passages = Map.of(7, new BodyTextMatcher.Passages(
                List.of("Staff get 20 days of annual leave."), Map.of("c-2", "Staff get 20 days of annual leave. ")));

        assertEquals("Staff get 20 days of annual leave.",
                     service().ask("annual leave", null).sources().get(0).passage());
    }

    @Test
    void whenNothingMatchesNoModelIsCalled() {
        // A hit with neither body text nor a description gives the model nothing to read.
        hits = List.of(hit(4, "Untitled scan", "HR", " ", null));

        AskService.Answer answer = service().ask("What is the wifi password?", null);

        assertNull(answer.answer());
        assertNull(answer.provider());
        assertEquals(List.of(), answer.sources());
        assertNull(promptSeen);
    }

    @Test
    void withoutMongoTheDescriptionsAreTheSources() {
        hits = List.of(hit(7, "Leave Policy", "HR", "Staff get 20 days of annual leave", "c-9"));
        mongoDown = true;

        AskService.Answer answer = service().ask("annual leave", null);

        assertEquals("Staff get 20 days of annual leave", answer.sources().get(0).passage());
    }

    @Test
    void aLongPassageIsCutBeforeItReachesTheModel() {
        hits = List.of(hit(7, "Manual", "IT", null, null));
        passages = Map.of(7, new BodyTextMatcher.Passages(List.of("backup ".repeat(1000)), Map.of()));

        String passage = service().ask("backup", null).sources().get(0).passage();

        assertEquals(AskService.MAX_PASSAGE_CHARS + 1, passage.length());
        assertTrue(passage.endsWith("…"));
    }

    @Test
    void theChosenProviderIsPassedOnAndAnUnconfiguredOneStopsBeforeSearching() {
        hits = List.of(hit(7, "Leave Policy", "HR", "Rules", null));
        service().ask("leave", GROQ);
        assertEquals(GROQ, providerSeen);

        searched = null;
        assertThrows(ServiceUnavailableException.class, () -> service().ask("leave", GEMINI));
        keys = "";
        assertThrows(ServiceUnavailableException.class, () -> service().ask("leave", null));
        assertNull(searched);
    }

    @Test
    void aModelsOwnCitationMarksAndMarkdownBecomePlainTextWithBracketCitations() {
        hits = List.of(hit(7, "Leave Policy", "HR", "Rules", null), hit(3, "Travel Policy", "HR", "Trips", null));
        reply = "**Permanent** staff get 30 days【1†L1-L3】【2†L4】.  \n* Part-time: pro rata [2]. ";

        AskService.Answer answer = service().ask("annual leave", null);

        assertEquals("Permanent staff get 30 days[1][2].\n- Part-time: pro rata [2].", answer.answer());
        assertEquals(List.of(true, true), answer.sources().stream().map(AskService.Source::cited).toList());
        assertEquals("2 * 3 = 6", AskService.tidy("2 * 3 = 6"));
    }

    @Test
    void citationsAreReadSinglyAndInGroups() {
        assertEquals(Set.of(1, 3, 4), AskService.cited("Yes [1]. Also [3, 4], but not 5 or [x] or [99999]."));
        assertEquals(Set.of(), AskService.cited("The documents do not cover this."));
    }
}
