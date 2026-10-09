package com.eip.backend.service;

import com.eip.backend.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ShellForge runner. Tests that run the real shell need the Linux binary:
 * CI builds it and points SHELLFORGE_BINARY at it; elsewhere they are skipped.
 */
class ShellForgeServiceTest {

    private static final String BINARY = System.getenv().getOrDefault(
            "SHELLFORGE_BINARY", "../../OSSP/shellforge/bin/shellforge");

    private final ShellForgeService service = new ShellForgeService(BINARY);

    /** The binary is a Linux ELF: on Windows it exists but cannot run. */
    private void assumeRunnable() {
        assumeTrue(service.available() && Path.of("/").toString().equals("/"), "ShellForge binary not runnable: " + BINARY);
    }

    private ShellForgeService.Run run(String session) {
        assumeRunnable();
        return service.run(session, null, null);
    }

    @Test
    void sessionsCoverWeeksFourToTen() {
        List<String> ids = service.sessions().stream().map(ShellForgeService.Session::id).toList();
        assertEquals(List.of("processes", "builtins", "pipes", "redirection", "monitor", "threads"), ids);
        assertEquals(Set.of(4, 5, 7, 9, 10),
                Set.copyOf(service.sessions().stream().map(ShellForgeService.Session::week).toList()));
    }

    @Test
    void onlyKnownSessionsRun() {
        assertThrows(IllegalArgumentException.class, () -> service.run("rm -rf /", null, null));
    }

    @Test
    void aMissingBinaryIsUnavailableNotAnError() {
        ShellForgeService missing = new ShellForgeService("/nonexistent/shellforge");
        assertFalse(missing.available());
        assertThrows(ServiceUnavailableException.class, () -> missing.run("pipes", null, null));
    }

    @Test
    void theShellInheritsNothingFromTheBackend() {
        // JWT_SECRET, database passwords and API keys live in the backend's environment.
        Map<String, String> env = service.environment(Path.of("/tmp/work"), 0);
        assertEquals(Set.of("PATH", "HOME", "PWD", "USER", "SHELL", "LANG", "SHELLFORGE_MONITOR"), env.keySet());
        assertEquals(Path.of("/tmp/work").toString(), env.get("HOME"));
        assertEquals("0", env.get("SHELLFORGE_MONITOR"));
    }

    @Test
    void aPipelineRunsInTheRealShell() {
        ShellForgeService.Run run = run("pipes");
        assertTrue(run.output().contains("myshell> echo shellforge connects processes | tr a-z A-Z"), run.output());
        assertTrue(run.output().contains("SHELLFORGE CONNECTS PROCESSES"), run.output());
        assertTrue(run.output().contains("IPC Demonstration Completed Successfully"), run.output());
        assertEquals(0, run.exitCode());
        assertFalse(run.timedOut());
    }

    @Test
    void theEnvBuiltinShowsTheSandboxOnly() {
        String output = run("builtins").output();
        assertTrue(output.contains("USER=shellforge"), output);
        assertTrue(output.contains("PATH=/usr/local/bin:/usr/bin:/bin"), output);
    }

    @Test
    void redirectedFilesStayInTheWorkingDirectory() {
        String output = run("redirection").output();
        assertTrue(output.contains("second line\nfirst line"), output); // sort -r
        // `ls` lists what the session made, not the script and output beside it.
        assertTrue(output.contains("errors.txt\nnotes.txt\nsorted.txt"), output);
        assertFalse(output.contains("input.txt"), output);
    }

    @Test
    void theRaceDemoUsesTheRequestedNumbers() {
        assumeRunnable();
        ShellForgeService.Run run = service.run("threads", 2, 1000);
        assertEquals(List.of("demo-threads 2 1000"), run.commands());
        assertTrue(run.output().contains("With a mutex:    counter = 2000 (0 lost)"), run.output());
    }

    @Test
    void theMonitorThreadReportsDuringACommand() {
        String output = run("monitor").output();
        assertTrue(output.contains("[Monitor] ShellForge Running..."), output);
        assertTrue(output.contains("The monitor kept running while the main thread waited"), output);
    }

    @Test
    void aRunawaySessionIsStoppedAtTheTimeLimit() {
        assumeRunnable();
        ShellForgeService.Run run = service.execute("slow", List.of("sleep 30"), 0, Duration.ofMillis(500));
        assertTrue(run.timedOut());
        assertEquals(-1, run.exitCode());
        assertTrue(run.durationMillis() < 5000, "took " + run.durationMillis() + " ms");
    }
}
