#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include <string.h>
#include "executor.h"

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

    pid_t pid = fork();

    if (pid < 0) {
        /* Fork failed: system resource exhaustion or limit reached */
        perror("ShellForge: fork error");
        return -1;
    } else if (pid == 0) {
        /* Child process: load and execute the program */
        if (execvp(args[0], args) == -1) {
            fprintf(stderr, "ShellForge: %s: %s\n", args[0], strerror(errno));
            fflush(stderr);
            /*
             * Child MUST terminate immediately using _exit() after a failed execvp().
             * Using _exit() avoids flushing parent stdio buffers or calling parent atexit handlers.
             */
            _exit(errno == ENOENT ? 127 : EXIT_FAILURE);
        }
    } else {
        /* Parent process: wait for the child process to complete */
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
        } else if (WIFSIGNALED(status)) {
            return 128 + WTERMSIG(status);
        }
    }

    return 0;
}

/*
 * Executes a two-stage pipeline: args1 | args2.
 * Connects stdout of child 1 to pipe write-end, and stdin of child 2 to pipe read-end.
 * Rigorously cleans up all unused file descriptors in parent and both children.
 */
int execute_pipeline(char **args1, char **args2) {
    if (args1 == NULL || args1[0] == NULL || args2 == NULL || args2[0] == NULL) {
        fprintf(stderr, "ShellForge: syntax error near unexpected token '|'\n");
        return -1;
    }

    int pipefd[2];
    if (pipe(pipefd) == -1) {
        perror("ShellForge: pipe error");
        return -1;
    }

    /* Flush stdio buffers before fork */
    fflush(stdout);
    fflush(stderr);

    /* Fork first child (left-side of pipe: writer) */
    pid_t pid1 = fork();
    if (pid1 < 0) {
        perror("ShellForge: fork error");
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    } else if (pid1 == 0) {
        /* In child 1: redirect stdout to pipe write-end */
        if (dup2(pipefd[1], STDOUT_FILENO) == -1) {
            perror("ShellForge: dup2 error");
            _exit(EXIT_FAILURE);
        }
        /* Close both pipe descriptors in child 1 */
        close(pipefd[0]);
        close(pipefd[1]);

        if (execvp(args1[0], args1) == -1) {
            fprintf(stderr, "ShellForge: %s: %s\n", args1[0], strerror(errno));
            fflush(stderr);
            _exit(errno == ENOENT ? 127 : EXIT_FAILURE);
        }
    }

    /* Fork second child (right-side of pipe: reader) */
    pid_t pid2 = fork();
    if (pid2 < 0) {
        perror("ShellForge: fork error");
        close(pipefd[0]);
        close(pipefd[1]);
        waitpid(pid1, NULL, 0);
        return -1;
    } else if (pid2 == 0) {
        /* In child 2: redirect stdin from pipe read-end */
        if (dup2(pipefd[0], STDIN_FILENO) == -1) {
            perror("ShellForge: dup2 error");
            _exit(EXIT_FAILURE);
        }
        /* Close both pipe descriptors in child 2 */
        close(pipefd[0]);
        close(pipefd[1]);

        if (execvp(args2[0], args2) == -1) {
            fprintf(stderr, "ShellForge: %s: %s\n", args2[0], strerror(errno));
            fflush(stderr);
            _exit(errno == ENOENT ? 127 : EXIT_FAILURE);
        }
    }

    /*
     * CRITICAL FILE DESCRIPTOR LIFECYCLE RULE:
     * Parent MUST close both ends of the pipe immediately after forking.
     * If pipefd[1] is kept open in parent, child 2's stdin will never receive EOF
     * after child 1 terminates, causing child 2 to hang indefinitely!
     */
    close(pipefd[0]);
    close(pipefd[1]);

    int status1, status2;
    waitpid(pid1, &status1, 0);
    waitpid(pid2, &status2, 0);

    if (WIFEXITED(status2)) {
        return WEXITSTATUS(status2);
    } else if (WIFSIGNALED(status2)) {
        return 128 + WTERMSIG(status2);
    }
    return 0;
}

/*
 * Direct demonstration of Inter-Process Communication (IPC) via pipe():
 * Part 1: Parent -> Child communication
 * Part 2: Child -> Parent communication
 * Part 3: EOF detection upon writer closing
 */
int run_ipc_demo(void) {
    printf("\n=====================================================\n");
    printf(" ShellForge IPC Demonstration (CO-3: pipe() & IPC)  \n");
    printf("=====================================================\n\n");

    /* Part 1: Parent -> Child communication */
    printf("[IPC Demo 1] Parent -> Child Unidirectional Communication\n");
    int pipe1[2];
    if (pipe(pipe1) == -1) {
        perror("ShellForge: pipe error");
        return -1;
    }

    fflush(stdout);
    pid_t pid1 = fork();
    if (pid1 < 0) {
        perror("ShellForge: fork error");
        close(pipe1[0]);
        close(pipe1[1]);
        return -1;
    } else if (pid1 == 0) {
        /* Child: reads message sent by parent */
        close(pipe1[1]); /* Close unused write end */
        char buffer[128];
        ssize_t bytes_read = read(pipe1[0], buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            buffer[bytes_read] = '\0';
            printf("  [Child PID %d] Received from parent: \"%s\"\n", getpid(), buffer);
            fflush(stdout);
        }
        close(pipe1[0]);
        _exit(0);
    } else {
        /* Parent: writes message to child */
        close(pipe1[0]); /* Close unused read end */
        const char *msg = "Hello Child from Parent!";
        printf("  [Parent PID %d] Sending to child: \"%s\"\n", getpid(), msg);
        fflush(stdout);
        write(pipe1[1], msg, strlen(msg));
        close(pipe1[1]); /* Closing write end signals EOF to child */
        waitpid(pid1, NULL, 0);
    }

    /* Part 2: Child -> Parent communication */
    printf("\n[IPC Demo 2] Child -> Parent Unidirectional Communication\n");
    int pipe2[2];
    if (pipe(pipe2) == -1) {
        perror("ShellForge: pipe error");
        return -1;
    }

    fflush(stdout);
    pid_t pid2 = fork();
    if (pid2 < 0) {
        perror("ShellForge: fork error");
        close(pipe2[0]);
        close(pipe2[1]);
        return -1;
    } else if (pid2 == 0) {
        /* Child: writes message to parent */
        close(pipe2[0]); /* Close unused read end */
        const char *msg = "Greetings Parent from Child!";
        printf("  [Child PID %d] Sending to parent: \"%s\"\n", getpid(), msg);
        fflush(stdout);
        write(pipe2[1], msg, strlen(msg));
        close(pipe2[1]); /* Closing write end signals EOF to parent */
        _exit(0);
    } else {
        /* Parent: reads message sent by child */
        close(pipe2[1]); /* Close unused write end */
        char buffer[128];
        ssize_t bytes_read = read(pipe2[0], buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            buffer[bytes_read] = '\0';
            printf("  [Parent PID %d] Received from child: \"%s\"\n", getpid(), buffer);
            fflush(stdout);
        }
        close(pipe2[0]);
        waitpid(pid2, NULL, 0);
    }

    /* Part 3: EOF Behavior Verification */
    printf("\n[IPC Demo 3] EOF Detection Verification\n");
    int pipe3[2];
    if (pipe(pipe3) == -1) {
        perror("ShellForge: pipe error");
        return -1;
    }
    printf("  Closing write-end of pipe (pipe3[1])...\n");
    close(pipe3[1]);
    char eof_buf[16];
    ssize_t n = read(pipe3[0], eof_buf, sizeof(eof_buf));
    printf("  Reader read() returned: %zd byte(s) (0 indicates EOF received)\n", n);
    close(pipe3[0]);

    printf("\n=====================================================\n");
    printf(" IPC Demonstration Completed Successfully            \n");
    printf("=====================================================\n\n");
    return 0;
}

