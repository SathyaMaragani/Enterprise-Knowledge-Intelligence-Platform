package com.eip.backend.dto;

import com.eip.backend.service.AskService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A question for the documents; {@code provider} is "groq", "gemini", or absent/"auto" for whichever answers. */
public record AskRequest(
        @NotBlank(message = "Ask a question")
        @Size(max = AskService.MAX_QUESTION_CHARS,
              message = "A question can be at most " + AskService.MAX_QUESTION_CHARS + " characters")
        String question,

        String provider) {
}
