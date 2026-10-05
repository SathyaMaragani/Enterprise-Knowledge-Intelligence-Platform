#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <string.h>
#include "pipes.h"
#include "executor.h"
#include "signals.h"

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
    foreground_begin();

    /* Fork first child (left-side of pipe: writer) */
    pid_t pid1 = fork();
    if (pid1 < 0) {
        perror("ShellForge: fork error");
        close(pipefd[0]);
        close(pipefd[1]);
        foreground_end();
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
        exec_child(args1);
    }

    /* Fork second child (right-side of pipe: reader) */
    pid_t pid2 = fork();
    if (pid2 < 0) {
        perror("ShellForge: fork error");
        close(pipefd[0]);
        close(pipefd[1]);
        wait_child(pid1);
        foreground_end();
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
        exec_child(args2);
    }

    /*
     * CRITICAL FILE DESCRIPTOR LIFECYCLE RULE:
     * Parent MUST close both ends of the pipe immediately after forking.
     * If pipefd[1] is kept open in parent, child 2's stdin will never receive EOF
     * after child 1 terminates, causing child 2 to hang indefinitely!
     */
    close(pipefd[0]);
    close(pipefd[1]);

    wait_child(pid1);
    int status = wait_child(pid2); /* a pipeline's status is its last command's */
    foreground_end();
    return status;
}

/*
 * Direct demonstration of Inter-Process Communication (IPC) via pipe():
 * Part 1: Parent -> Child communication
 * Part 2: Child -> Parent communication
 * Part 3: EOF detection upon writer closing
 */
static int ipc_demo_body(void) {
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


int run_ipc_demo(void) {
    /* The demo waits for its own children; keep the SIGCHLD reaper off them. */
    foreground_begin();
    int status = ipc_demo_body();
    foreground_end();
    return status;
}
