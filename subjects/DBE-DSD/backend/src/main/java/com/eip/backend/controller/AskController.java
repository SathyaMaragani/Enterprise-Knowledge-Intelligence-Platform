package com.eip.backend.controller;

import com.eip.backend.dto.AskRequest;
import com.eip.backend.service.AskService;
import com.eip.backend.service.LlmService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Questions answered from the documents by a language model. Any signed-in user
 * may ask; an answer draws only on documents that user can read.
 */
@RestController
@RequestMapping("/api/ask")
public class AskController {

    /** A model that can be asked for by {@code id}. */
    public record ProviderInfo(String id, String model) {
    }

    /** Whether any model is set up here, and which. */
    public record Overview(boolean available, List<ProviderInfo> providers) {
    }

    private final AskService askService;
    private final LlmService llm;

    public AskController(AskService askService, LlmService llm) {
        this.askService = askService;
        this.llm = llm;
    }

    @GetMapping
    public Overview overview() {
        List<ProviderInfo> providers = llm.configured().stream()
                .map(provider -> new ProviderInfo(provider.name().toLowerCase(Locale.ROOT), llm.model(provider)))
                .toList();
        return new Overview(!providers.isEmpty(), providers);
    }

    /** 400 for a blank or over-long question or an unknown provider; 503 if no model is set up or none answered. */
    @PostMapping
    public AskService.Answer ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.question(), provider(request.provider()));
    }

    private static LlmService.Provider provider(String name) {
        if (name == null || name.isBlank() || name.strip().equalsIgnoreCase("auto")) {
            return null;
        }
        try {
            return LlmService.Provider.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("provider must be auto, groq or gemini");
        }
    }
}
