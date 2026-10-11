package com.eip.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A user changing their own password: the current one proves who is asking. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password")
        String currentPassword,

        // BCrypt only reads the first 72 bytes, so longer passwords would be silently truncated.
        @NotBlank(message = "Enter a new password")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String newPassword) {
}
