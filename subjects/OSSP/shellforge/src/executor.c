#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include <string.h>
#include "executor.h"
#include "redirect.h"
#include "signals.h"

void exec_child(char **args) {
    reset_child_signals();

    if (apply_redirections(args) == -1) {
        _exit(EXIT_FAILURE);
    }
    if (args[0] == NULL) {
        _exit(0); /* only redirections, e.g. "> file | cat": nothing to run */
    }

    execvp(args[0], args);

    /* Save errno first: fprintf() may itself change it. */
    int err = errno;
    fprintf(stderr, "ShellForge: %s: %s\n", args[0], strerror(err));
    fflush(stderr);
    /*
     * _exit(), not exit(): exit() would flush stdio buffers copied from the
     * parent at fork time and run its atexit handlers a second time.
     */
    _exit(err == ENOENT ? 127 : EXIT_FAILURE);
}

int wait_child(pid_t pid) {
    int status;

    do {
        if (waitpid(pid, &status, WUNTRACED) == -1) {
            if (errno == EINTR) {
                continue;
            }
            perror("ShellForge: waitpid error");
            return -1;
        }
    } while (!WIFEXITED(status) && !WIFSIGNALED(status));

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return 128 + WTERMSIG(status);
}

/*
 * Executes an external program by creating a child process with fork(),
 * replacing the child's image with execvp(), and waiting for completion
 * in the parent with waitpid().
 */
int execute_command(char **args) {
    if (args == NULL || args[0] == NULL) {
        return 0;
    }

    /* Flush stdio streams before fork so child does not inherit unflushed buffers */
    fflush(stdout);
    fflush(stderr);

    foreground_begin();
    pid_t pid = fork();

    if (pid < 0) {
        /* Fork failed: system resource exhaustion or limit reached */
        perror("ShellForge: fork error");
        foreground_end();
        return -1;
    }
    if (pid == 0) {
        exec_child(args);
    }

    int status = wait_child(pid);
    foreground_end();
    return status;
}
