package com.eip.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * An edit to a document. A field left out (null) is not changed; a blank
 * description removes the description.
 */
public record DocumentUpdateRequest(
        @Size(max = 255, message = "Titles can be at most 255 characters")
        String title,

        @Size(max = 2000, message = "Descriptions can be at most 2000 characters")
        String description,

        String category) {
}
