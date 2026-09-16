package com.eip.backend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.List;

/** Request and response shapes for user administration and document access grants. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record UserSummary(Integer id, String username, String fullName, String email, boolean active,
                              List<String> roles, ZonedDateTime createdAt) {
    }

    public record RoleSummary(String name, String description, List<String> permissions) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
            @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Username may contain only letters, digits, dots, dashes and underscores")
            String username,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid address")
            @Size(max = 255, message = "Email must be at most 255 characters")
            String email,

            @NotBlank(message = "Full name is required")
            @Size(max = 255, message = "Full name must be at most 255 characters")
            String fullName,

            // BCrypt only reads the first 72 bytes, so longer passwords would be silently truncated.
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            String password,

            @NotBlank(message = "Role is required")
            String role) {
    }

    /** Every field is optional; only the ones present change. */
    public record UpdateUserRequest(
            @Size(max = 255, message = "Full name must be at most 255 characters")
            String fullName,
            Boolean active,
            String role) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            String password) {
    }

    public record GrantRequest(
            @NotBlank(message = "Username is required")
            String username,
            String permissionType) {
    }

    public record GrantResponse(Integer id, String username, String fullName, String permissionType,
                                ZonedDateTime grantedAt) {
    }
}
