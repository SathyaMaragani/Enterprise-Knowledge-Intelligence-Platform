package com.eip.backend.service;

import com.eip.backend.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the OSSP ShellForge shell (subjects/OSSP/shellforge, compiled into the
 * backend image) on fixed sessions and returns the transcript.
 *
 * <p>Only sessions defined here run. A shell that ran whatever a web user typed
 * would let any signed-in user execute commands on the server. Each run also
 * gets a fresh, empty working directory; an environment holding only PATH,
 * HOME, PWD, USER, SHELL and LANG, so the backend's secrets never reach the
 * shell; a time limit; and a cap on output.
 */
@Service
public class ShellForgeService {

    /** A scripted session; {@code monitorSeconds} is the Week 10 heartbeat interval (0: off). */
    public record Session(String id, int week, String title, String summary, List<String> commands,
                          int monitorSeconds) {
    }

    /** What one run printed, as a transcript: ShellForge echoes each command after its prompt. */
    public record Run(String session, List<String> commands, String output, int exitCode,
                      long durationMillis, boolean timedOut) {
    }

    public static final int MAX_THREADS = 8;
    public static final int MAX_INCREMENTS = 200_000;
    static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final Duration TIME_LIMIT = Duration.ofSeconds(20);

    private static final List<Session> SESSIONS = List.of(
            new Session("processes", 4, "Processes: fork, exec and wait",
                    "Each external command runs in a child made by fork(), replaced by execvp() and collected "
                            + "by waitpid(). A missing program fails in the child, and the shell carries on.",
                    List.of("echo Hello from a child process", "ls /usr", "nonexistent_command",
                            "echo The shell is still here"), 0),
            new Session("builtins", 5, "Built-in commands",
                    "cd, pwd, env and help run inside the shell process: a child's chdir() would vanish with "
                            + "the child. env shows the bare environment the server gives the shell.",
                    List.of("pwd", "cd /usr/bin", "pwd", "cd", "pwd", "env", "help"), 0),
            new Session("pipes", 7, "Pipes and IPC",
                    "pipe() and dup2() connect one command's output to the next one's input. demo-ipc passes "
                            + "messages between a parent and a child process and shows end-of-file.",
                    List.of("echo shellforge connects processes | tr a-z A-Z", "ls /usr/bin | wc -l",
                            "demo-ipc"), 0),
            new Session("redirection", 9, "Redirection",
                    "open() and dup2() point a command's input, output or errors at a file: > truncates, "
                            + ">> appends, < reads and 2> captures errors.",
                    List.of("echo first line > notes.txt", "echo second line >> notes.txt", "cat < notes.txt",
                            "sort -r < notes.txt > sorted.txt", "cat sorted.txt", "ls /missing 2> errors.txt",
                            "cat errors.txt", "ls"), 0),
            new Session("monitor", 10, "Background monitor thread",
                    "A second thread in the shell process prints a heartbeat every second, while the main "
                            + "thread runs commands and waits for them.",
                    List.of("echo The monitor thread reports every second", "sleep 2.5",
                            "echo The monitor kept running while the main thread waited"), 1),
            new Session("threads", 10, "Race condition and mutex",
                    "Worker threads add 1 to a shared counter. Without a lock their increments collide and "
                            + "some are lost; under a mutex every one counts.",
                    List.of("demo-threads 4 100000"), 0));

    // ponytail: two shells at a time, sized for the free instance's 0.1 CPU.
    private final Semaphore slots = new Semaphore(2);
    private final Path binary;

    public ShellForgeService(@Value("${shellforge.binary:/opt/shellforge/shellforge}") String binary) {
        this.binary = Path.of(binary);
    }

    public List<Session> sessions() {
        return SESSIONS;
    }

    public boolean available() {
        return Files.isExecutable(binary);
    }

    /**
     * Runs a session. {@code threads} and {@code increments} replace the race
     * demo's defaults; the caller has checked their range.
     */
    public Run run(String sessionId, Integer threads, Integer increments) {
        Session session = SESSIONS.stream()
                .filter(s -> s.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown session: " + sessionId));
        List<String> commands = session.commands();
        if (session.id().equals("threads")) {
            commands = List.of("demo-threads " + (threads == null ? 4 : threads) + " "
                    + (increments == null ? 100_000 : increments));
        }
        if (!available()) {
            throw new ServiceUnavailableException("ShellForge is not installed on this server");
        }
        if (!slots.tryAcquire()) {
            throw new ServiceUnavailableException("ShellForge is busy. Try again in a few seconds.");
        }
        try {
            return execute(session.id(), commands, session.monitorSeconds(), TIME_LIMIT);
        } finally {
            slots.release();
        }
    }

    Run execute(String sessionId, List<String> commands, int monitorSeconds, Duration limit) {
        Path base = null;
        try {
            // The script and the output sit beside the working directory, so `ls` does not see them.
            base = Files.createTempDirectory("shellforge-");
            Path work = Files.createDirectory(base.resolve("work"));
            Path input = base.resolve("input.txt");
            Path output = base.resolve("output.txt");
            Files.writeString(input, String.join("\n", commands) + "\nexit\n");

            // Files rather than pipes: nothing to drain on another thread, and no deadlock on a full pipe.
            ProcessBuilder builder = new ProcessBuilder(binary.toString(), "--echo")
                    .directory(work.toFile())
                    .redirectInput(input.toFile())
                    .redirectOutput(output.toFile())
                    .redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(environment(work, monitorSeconds));

            long start = System.nanoTime();
            Process process = builder.start();
            boolean finished = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor();
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            return new Run(sessionId, commands, read(output), finished ? process.exitValue() : -1, millis, !finished);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not run ShellForge", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("ShellForge run was interrupted", e);
        } finally {
            if (base != null) {
                try {
                    FileSystemUtils.deleteRecursively(base);
                } catch (IOException ignored) {
                    // The OS cleans its temporary directory; a leftover here holds no secrets.
                }
            }
        }
    }

    /** The shell's whole environment: nothing is inherited from the backend. */
    Map<String, String> environment(Path work, int monitorSeconds) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PATH", "/usr/local/bin:/usr/bin:/bin");
        env.put("HOME", work.toString());
        env.put("PWD", work.toString());
        env.put("USER", "shellforge");
        env.put("SHELL", binary.toString());
        env.put("LANG", "C.UTF-8");
        env.put("SHELLFORGE_MONITOR", Integer.toString(monitorSeconds));
        return env;
    }

    private static String read(Path output) throws IOException {
        try (InputStream in = Files.newInputStream(output)) {
            byte[] bytes = in.readNBytes(MAX_OUTPUT_BYTES);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return in.read() == -1 ? text : text + "\n[output truncated]";
        }
    }
}
