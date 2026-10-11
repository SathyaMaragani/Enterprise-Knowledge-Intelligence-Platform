package com.eip.backend.service;

import com.eip.backend.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends one prompt to a hosted language model: Groq (OpenAI-compatible chat
 * completions) or Google Gemini (generateContent). A provider is available when
 * its API key is set in the environment (GROQ_API_KEY, GEMINI_API_KEY); with
 * neither, {@link #complete} answers 503 and the rest of the app is unaffected.
 *
 * <p>Keys travel in request headers only, never in a URL, and are never logged.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    /** In the order they are tried when the caller has no preference: Groq answers fastest. */
    public enum Provider { GROQ, GEMINI }

    public record Reply(String text, Provider provider, String model) {
    }

    // Reasoning models spend part of this on thinking before the answer.
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final double TEMPERATURE = 0.2;

    private final String groqKey;
    private final String groqModel;
    private final String groqUrl;
    private final String geminiKey;
    private final String geminiModel;
    private final String geminiUrl;
    private final RestClient http;

    public LlmService(
            @Value("${groq.api-key:}") String groqKey,
            @Value("${groq.model:openai/gpt-oss-120b}") String groqModel,
            @Value("${groq.url:https://api.groq.com/openai/v1/chat/completions}") String groqUrl,
            @Value("${gemini.api-key:}") String geminiKey,
            @Value("${gemini.model:gemini-flash-lite-latest}") String geminiModel,
            @Value("${gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String geminiUrl) {
        this.groqKey = groqKey.strip();
        this.groqModel = groqModel;
        this.groqUrl = groqUrl;
        this.geminiKey = geminiKey.strip();
        this.geminiModel = geminiModel;
        this.geminiUrl = geminiUrl;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(40));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** The providers with an API key, in the order they are tried. */
    public List<Provider> configured() {
        List<Provider> providers = new ArrayList<>();
        if (!groqKey.isEmpty()) {
            providers.add(Provider.GROQ);
        }
        if (!geminiKey.isEmpty()) {
            providers.add(Provider.GEMINI);
        }
        return providers;
    }

    public String model(Provider provider) {
        return provider == Provider.GROQ ? groqModel : geminiModel;
    }

    /** @throws ServiceUnavailableException if {@code preferred}, or with null any provider, has no API key */
    public void requireConfigured(Provider preferred) {
        List<Provider> available = configured();
        if (available.isEmpty()) {
            throw new ServiceUnavailableException(
                    "No language model is set up on this server: set GROQ_API_KEY or GEMINI_API_KEY.");
        }
        if (preferred != null && !available.contains(preferred)) {
            throw new ServiceUnavailableException(label(preferred) + " is not set up on this server.");
        }
    }

    /**
     * Answers {@code user} under the instructions in {@code system}. With a
     * {@code preferred} provider only that one is asked; with null each configured
     * provider is tried in turn, so a rate limit or outage at one falls back to
     * the other.
     *
     * @throws ServiceUnavailableException if no provider is configured, or none answered
     */
    public Reply complete(String system, String user, Provider preferred) {
        requireConfigured(preferred);
        for (Provider provider : preferred != null ? List.of(preferred) : configured()) {
            try {
                String text = provider == Provider.GROQ ? groq(system, user) : gemini(system, user);
                if (text != null && !text.isBlank()) {
                    return new Reply(text.strip(), provider, model(provider));
                }
                logger.warn("{} returned no text", provider);
            } catch (RestClientResponseException e) {
                logger.warn("{} answered HTTP {}", provider, e.getStatusCode().value());
            } catch (RestClientException e) {
                logger.warn("{} could not be reached: {}", provider, e.getClass().getSimpleName());
            }
        }
        throw new ServiceUnavailableException(preferred != null
                ? label(preferred) + " did not answer. Try again in a moment, or choose another model."
                : "The language model did not answer. Try again in a moment.");
    }

    private String groq(String system, String user) {
        JsonNode reply = http.post().uri(groqUrl)
                .header("Authorization", "Bearer " + groqKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", groqModel,
                        "temperature", TEMPERATURE,
                        "max_completion_tokens", MAX_OUTPUT_TOKENS,
                        "messages", List.of(
                                Map.of("role", "system", "content", system),
                                Map.of("role", "user", "content", user))))
                .retrieve().body(JsonNode.class);
        return reply == null ? null : reply.at("/choices/0/message/content").asText(null);
    }

    private String gemini(String system, String user) {
        JsonNode reply = http.post().uri(geminiUrl + "/{model}:generateContent", geminiModel)
                .header("x-goog-api-key", geminiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "system_instruction", Map.of("parts", List.of(Map.of("text", system))),
                        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                        "generationConfig", Map.of("temperature", TEMPERATURE, "maxOutputTokens", MAX_OUTPUT_TOKENS)))
                .retrieve().body(JsonNode.class);
        if (reply == null) {
            return null;
        }
        // The answer can arrive in several parts; parts flagged as thoughts are the model's reasoning.
        StringBuilder text = new StringBuilder();
        for (JsonNode part : reply.at("/candidates/0/content/parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    private static String label(Provider provider) {
        return provider == Provider.GROQ ? "Groq" : "Gemini";
    }
}
