/*
 * Week 6 check: a child that nobody waits for becomes a zombie, and the
 * SIGCHLD handler from src/signals.c reaps it.
 *
 *   gcc -Iinclude tests/sigchld_check.c src/signals.c -o bin/sigchld_check
 */
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#include "signals.h"

/* Process state letter from /proc/<pid>/stat ('Z' = zombie), or 0 if gone. */
static char state_of(pid_t pid) {
    char path[64];
    char buf[512];
    FILE *f;

    snprintf(path, sizeof(path), "/proc/%d/stat", (int)pid);
    f = fopen(path, "r");
    if (f == NULL) {
        return 0;
    }
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[n] = '\0';
    char *end = strrchr(buf, ')'); /* the command name may contain spaces */
    return end != NULL && end[1] == ' ' ? end[2] : '?';
}

/* Forks a child that exits at once, gives it time to finish, returns its pid. */
static pid_t short_lived_child(void) {
    pid_t pid = fork();
    if (pid == 0) {
        _exit(0);
    }
    for (int i = 0; i < 20 && state_of(pid) != 'Z' && state_of(pid) != 0; i++) {
        usleep(50000); /* a SIGCHLD may cut this short; the loop covers that */
    }
    return pid;
}

int main(void) {
    int failed = 0;

    pid_t first = short_lived_child();
    char before = state_of(first);
    printf("without a SIGCHLD handler: child %d state %c%s\n", (int)first, before ? before : '-',
           before == 'Z' ? " (zombie)" : "");
    failed |= before != 'Z';

    initialize_signals();

    pid_t second = short_lived_child();
    char after = state_of(second);
    printf("with the SIGCHLD handler:  child %d %s\n", (int)second,
           after == 0 ? "reaped (no /proc entry)" : "still present");
    failed |= after != 0;

    /* The handler's waitpid(-1) loop also collected the earlier zombie. */
    printf("earlier zombie %d: %s\n", (int)first, state_of(first) == 0 ? "reaped too" : "still present");
    failed |= state_of(first) != 0;

    return failed;
}
