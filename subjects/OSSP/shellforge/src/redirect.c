#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "redirect.h"

/* The stream an operator redirects, or -1 if the token is not an operator. */
static int target_fd(const char *token) {
    if (strcmp(token, "<") == 0) {
        return STDIN_FILENO;
    }
    if (strcmp(token, ">") == 0 || strcmp(token, ">>") == 0) {
        return STDOUT_FILENO;
    }
    if (strcmp(token, "2>") == 0) {
        return STDERR_FILENO;
    }
    return -1;
}

static int is_operator(const char *token) {
    return target_fd(token) != -1 || strcmp(token, "|") == 0;
}

int apply_redirections(char **args) {
    int read_pos;
    int write_pos = 0;

    for (read_pos = 0; args[read_pos] != NULL; read_pos++) {
        const char *op = args[read_pos];
        int fd = target_fd(op);

        if (fd == -1) {
            args[write_pos++] = args[read_pos]; /* an ordinary argument: keep it */
            continue;
        }

        const char *path = args[read_pos + 1];
        if (path == NULL || is_operator(path)) {
            fprintf(stderr, "ShellForge: syntax error: '%s' needs a file name\n", op);
            return -1;
        }

        int flags;
        if (fd == STDIN_FILENO) {
            flags = O_RDONLY;
        } else if (strcmp(op, ">>") == 0) {
            flags = O_WRONLY | O_CREAT | O_APPEND;
        } else {
            flags = O_WRONLY | O_CREAT | O_TRUNC;
        }

        /* 0644: owner reads and writes, group and others read (before umask). */
        int file = open(path, flags, 0644);
        if (file == -1) {
            fprintf(stderr, "ShellForge: %s: %s\n", path, strerror(errno));
            return -1;
        }
        if (file != fd) {
            /* open() may return fd itself if that stream was closed; then no move is needed. */
            if (dup2(file, fd) == -1) {
                perror("ShellForge: dup2 error");
                close(file);
                return -1;
            }
            close(file); /* the stream now holds the file; this copy would leak */
        }
        read_pos++; /* skip the file name */
    }

    args[write_pos] = NULL;
    return 0;
}

int begin_redirection(char **args, int saved[3]) {
    int i;
    int has_redirection = 0;

    for (i = 0; i < 3; i++) {
        saved[i] = -1;
    }
    for (i = 0; args[i] != NULL; i++) {
        if (target_fd(args[i]) != -1) {
            has_redirection = 1;
        }
    }
    if (!has_redirection) {
        return 0;
    }

    /* Anything already buffered belongs to the terminal, not to the file. */
    fflush(stdout);
    fflush(stderr);

    for (i = 0; i < 3; i++) {
        /*
         * Close-on-exec, so the saved copies do not leak into every program
         * the shell runs while the redirection is in place.
         */
        saved[i] = fcntl(i, F_DUPFD_CLOEXEC, 10);
    }

    if (apply_redirections(args) == -1) {
        end_redirection(saved);
        return -1;
    }
    return 0;
}

void end_redirection(int saved[3]) {
    int i;

    fflush(stdout);
    fflush(stderr);
    for (i = 0; i < 3; i++) {
        if (saved[i] != -1) {
            dup2(saved[i], i);
            close(saved[i]);
            saved[i] = -1;
        }
    }
}
