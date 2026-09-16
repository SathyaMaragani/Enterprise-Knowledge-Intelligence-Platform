package com.eip.backend.dto.auth;

import java.util.List;

/**
 * The signed-in user's own profile: who they are and what they may do.
 *
 * <p>Roles and permissions are sorted so the response is stable. They describe
 * what the UI should offer; every endpoint still enforces its own rules.
 */
public record CurrentUserResponse(
        String username,
        String fullName,
        String email,
        List<String> roles,
        List<String> permissions) {
}
