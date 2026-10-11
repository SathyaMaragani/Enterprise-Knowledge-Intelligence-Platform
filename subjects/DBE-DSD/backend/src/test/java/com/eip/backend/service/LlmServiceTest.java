package com.eip.backend.service;

import com.eip.backend.exception.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.eip.backend.service.LlmService.Provider.GEMINI;
import static com.eip.backend.service.LlmService.Provider.GROQ;
import static org.junit.jupiter.api.Assertions.*;

/** The two providers' wire formats and the fallback between them, against a local stand-in server. */
class LlmServiceTest {

    private static final String GROQ_REPLY = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\" Twenty days [1]. \",\"reasoning\":\"The source says 20.\"}}]}";
    private static final String GEMINI_REPLY = "{\"candidates\":[{\"content\":{\"parts\":["
            + "{\"text\":\"Let me think.\",\"thought\":true},{\"text\":\"Twenty \"},{\"text\":\"days [1].\"}]}}]}";

    /** One request the stand-in received. */
    private record Seen(String path, String authorization, String googleKey, String body) {
    }

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(String path, int status, String json) {
        answer(path, () -> new String[] {String.valueOf(status), json});
    }

    /** Serves {@code path} with whatever status and JSON {@code response} gives at the time of each request. */
    private void answer(String path, Supplier<String[]> response) {
        server.createContext(path, exchange -> {
            seen.add(new Seen(exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String[] now = response.get();
            byte[] body = now[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(now[0]), body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private LlmService service(String groqKey, String geminiKey) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new LlmService(groqKey, "groq-model", base + "/groq", geminiKey, "gemini-model", base + "/gemini");
    }

    @Test
    void groqGetsTheKeyAsABearerTokenAndItsContentIsTheAnswer() {
        answer("/groq", 200, GROQ_REPLY);

        LlmService.Reply reply = service("groq-secret", "").complete("Be brief.", "How many days?", null);

        assertEquals(new LlmService.Reply("Twenty days [1].", GROQ, "groq-model"), reply);
        Seen request = seen.get(0);
        assertEquals("Bearer groq-secret", request.authorization());
        assertTrue(request.body().contains("\"model\":\"groq-model\""), request.body());
        assertTrue(request.body().contains("{\"role\":\"system\",\"content\":\"Be brief.\"}")
                || request.body().contains("{\"content\":\"Be brief.\",\"role\":\"system\"}"), request.body());
        assertTrue(request.body().contains("How many days?"), request.body());
    }

    @Test
    void geminiGetsTheKeyInAHeaderNeverTheUrlAndThoughtsAreLeftOut() {
        answer("/gemini", 200, GEMINI_REPLY);

        LlmService.Reply reply = service("", "gemini-secret").complete("Be brief.", "How many days?", null);

        assertEquals(new LlmService.Reply("Twenty days [1].", GEMINI, "gemini-model"), reply);
        Seen request = seen.get(0);
        assertEquals("/gemini/gemini-model:generateContent", request.path());
        assertEquals("gemini-secret", request.googleKey());
        assertTrue(request.body().contains("\"system_instruction\""), request.body());
        assertTrue(request.body().contains("How many days?"), request.body());
    }

    @Test
    void withNoPreferenceAProviderThatFailsOrSaysNothingFallsBackToTheNext() {
        AtomicReference<String[]> groq = new AtomicReference<>();
        answer("/groq", groq::get);
        answer("/gemini", 200, GEMINI_REPLY);
        LlmService service = service("groq-secret", "gemini-secret");

        for (String[] response : new String[][] {
                {"429", "{\"error\":{\"message\":\"Rate limit reached\"}}"},
                {"200", "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"},
                {"200", "{}"}}) {
            groq.set(response);
            seen.clear();

            assertEquals(GEMINI, service.complete("s", "u", null).provider(), response[1]);
            assertEquals(List.of("/groq", "/gemini/gemini-model:generateContent"),
                         seen.stream().map(Seen::path).toList());
        }
    }

    @Test
    void aChosenProviderIsNotSwappedForAnotherWhenItFails() {
        answer("/groq", 500, "{}");
        answer("/gemini", 200, GEMINI_REPLY);
        LlmService service = service("groq-secret", "gemini-secret");

        ServiceUnavailableException failure = assertThrows(ServiceUnavailableException.class,
                () -> service.complete("s", "u", GROQ));

        assertTrue(failure.getMessage().startsWith("Groq did not answer"), failure.getMessage());
        assertEquals(List.of("/groq"), seen.stream().map(Seen::path).toList());
        assertEquals(GEMINI, service.complete("s", "u", GEMINI).provider());
    }

    @Test
    void aProviderIsConfiguredOnlyWhenItsKeyIsSet() {
        assertEquals(List.of(), service("", "  ").configured());
        assertEquals(List.of(GEMINI), service("", "k").configured());
        assertEquals(List.of(GROQ, GEMINI), service("k", "k").configured());

        assertThrows(ServiceUnavailableException.class, () -> service("", "").complete("s", "u", null));
        ServiceUnavailableException failure = assertThrows(ServiceUnavailableException.class,
                () -> service("", "k").complete("s", "u", GROQ));
        assertEquals("Groq is not set up on this server.", failure.getMessage());
        assertTrue(seen.isEmpty());
    }

    @Test
    void anUnreachableProviderIsAFailureNotAnException() throws IOException {
        int port = server.getAddress().getPort();
        server.stop(0);
        LlmService service = new LlmService("k", "m", "http://127.0.0.1:" + port + "/groq", "", "m", "http://unused");

        assertThrows(ServiceUnavailableException.class, () -> service.complete("s", "u", null));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }
}
