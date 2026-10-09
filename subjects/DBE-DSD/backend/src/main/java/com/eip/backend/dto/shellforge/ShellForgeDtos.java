package com.eip.backend.dto.shellforge;

import com.eip.backend.service.ShellForgeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request and response shapes for running ShellForge sessions.
 *
 * <p>The thread limits bound the work one request can cause on a 0.1-CPU
 * instance: 8 threads x 200,000 increments, run twice, is about 3.2 million
 * mutex lock/unlock pairs.
 */
public final class ShellForgeDtos {

    private ShellForgeDtos() {
    }

    /** Whether the shell is installed here, and the sessions it can run. */
    public record Overview(boolean available, List<ShellForgeService.Session> sessions) {
    }

    /** A session id; {@code threads} and {@code increments} apply to the race demo only. */
    public record RunRequest(
            @NotBlank(message = "Choose a session")
            String session,

            @Min(value = 1, message = "threads must be between 1 and " + ShellForgeService.MAX_THREADS)
            @Max(value = ShellForgeService.MAX_THREADS,
                 message = "threads must be between 1 and " + ShellForgeService.MAX_THREADS)
            Integer threads,

            @Min(value = 1, message = "increments must be between 1 and " + ShellForgeService.MAX_INCREMENTS)
            @Max(value = ShellForgeService.MAX_INCREMENTS,
                 message = "increments must be between 1 and " + ShellForgeService.MAX_INCREMENTS)
            Integer increments) {
    }
}
