#include <errno.h>
#include <signal.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#include "signals.h"

/* Set while a foreground command runs; read by the SIGINT handler. */
static volatile sig_atomic_t foreground_running = 0;

static void write_str(const char *s) {
    /* write() is async-signal-safe; printf() is not, so handlers use this. */
    ssize_t ignored = write(STDOUT_FILENO, s, strlen(s));
    (void)ignored;
}

/*
 * Ctrl+C reaches every process in the terminal's foreground group: the
 * running command, which dies with the default action, and the shell, which
 * lands here and carries on. At an idle prompt the line typed so far has
 * already been discarded by the terminal, so a fresh prompt is drawn.
 */
static void sigint_handler(int sig) {
    (void)sig;
    if (foreground_running) {
        write_str("\n");
    } else {
        write_str("\nShellForge: type 'exit' to quit.\nmyshell> ");
    }
}

/*
 * Reaps every child that has finished. WNOHANG keeps the handler from
 * blocking on children still running, and the loop matters because signals
 * do not queue: two children exiting together may raise a single SIGCHLD.
 */
static void sigchld_handler(int sig) {
    int saved_errno = errno; /* waitpid() may change errno under the main code */
    (void)sig;
    while (waitpid(-1, NULL, WNOHANG) > 0) {
    }
    errno = saved_errno;
}

static void install(int sig, void (*handler)(int)) {
    struct sigaction sa;

    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = handler;
    sigemptyset(&sa.sa_mask);
    /*
     * SA_RESTART resumes a read() or waitpid() the signal interrupted, so
     * Ctrl+C at the prompt does not surface as a spurious EOF or wait error.
     */
    sa.sa_flags = SA_RESTART;
    sigaction(sig, &sa, NULL);
}

static void change_sigchld_mask(int how) {
    sigset_t set;

    sigemptyset(&set);
    sigaddset(&set, SIGCHLD);
    sigprocmask(how, &set, NULL);
}

void initialize_signals(void) {
    install(SIGINT, sigint_handler);
    install(SIGCHLD, sigchld_handler);
    install(SIGTSTP, SIG_IGN);
}

void foreground_begin(void) {
    change_sigchld_mask(SIG_BLOCK);
    foreground_running = 1;
}

void foreground_end(void) {
    foreground_running = 0;
    change_sigchld_mask(SIG_UNBLOCK);
}

void reset_child_signals(void) {
    /*
     * A caught signal reverts to its default on exec anyway, but an ignored
     * one stays ignored -- and a shell started in the background by sh already
     * has SIGINT ignored. Resetting explicitly makes Ctrl+C reach the child
     * either way. SIGTSTP is deliberately left ignored (see signals.h).
     */
    signal(SIGINT, SIG_DFL);
    signal(SIGCHLD, SIG_DFL);
    change_sigchld_mask(SIG_UNBLOCK);
}
