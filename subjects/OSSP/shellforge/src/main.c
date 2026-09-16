#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "shell.h"
#include "input.h"
#include "parser.h"
#include "executor.h"
#include "builtin.h"

int main(int argc, char **argv) {
    char *line;
    char **tokens;
    int i;
    int debug_mode = (getenv("SHELLFORGE_DEBUG") != NULL);

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--debug-tokens") == 0) {
            debug_mode = 1;
        } else if (strcmp(argv[i], "--demo-ipc") == 0) {
            return run_ipc_demo();
        }
    }

    printf("=====================================\n");
    printf(" Welcome to %s Version %s\n", SHELL_NAME, VERSION);
    printf("=====================================\n\n");

    while (1) {
        printf("myshell> ");
        fflush(stdout);
        line = read_line();

        if (line[0] == '\0' && feof(stdin)) {
            printf("\n");
            free(line);
            break;
        }

        if (line[0] == '\0') {
            free(line);
            continue;
        }

        tokens = parse_line(line);

        /* A line of nothing but whitespace parses to an empty vector. */
        if (tokens[0] == NULL) {
            free_tokens(tokens);
            free(line);
            continue;
        }

        if (strcmp(tokens[0], "demo-ipc") == 0) {
            run_ipc_demo();
            free_tokens(tokens);
            free(line);
            continue;
        }

        if (debug_mode) {
            printf("\nParsed Tokens\n");
            for (i = 0; tokens[i] != NULL; i++) {
                printf("argv[%d] = %s\n", i, tokens[i]);
            }
            printf("argv[%d] = NULL\n", i);
        }

        /* Check for the pipe metacharacter '|' */
        int pipe_count = 0;
        int pipe_idx = -1;
        for (i = 0; tokens[i] != NULL; i++) {
            if (strcmp(tokens[i], "|") == 0) {
                pipe_count++;
                if (pipe_idx == -1) {
                    pipe_idx = i;
                }
            }
        }

        if (pipe_count == 0) {
            /*
             * Built-ins are tried first and run in this process. A built-in
             * forked into a child could not change the shell's own state --
             * `cd` would move the child's directory and then the child would
             * exit, leaving the shell exactly where it started.
             */
            int builtin_status = execute_builtin(tokens);

            if (builtin_status == BUILTIN_EXIT) {
                printf("Exiting %s...\n", SHELL_NAME);
                free_tokens(tokens);
                free(line);
                break;
            }

            if (builtin_status == BUILTIN_NOT_FOUND) {
                execute_command(tokens);
            }
        } else if (pipe_count == 1) {
            if (pipe_idx == 0 || tokens[pipe_idx + 1] == NULL) {
                fprintf(stderr, "ShellForge: syntax error near unexpected token '|'\n");
            } else {
                tokens[pipe_idx] = NULL;
                char **args1 = &tokens[0];
                char **args2 = &tokens[pipe_idx + 1];
                execute_pipeline(args1, args2);
            }
        } else {
            fprintf(stderr, "ShellForge: multi-stage pipelines are not supported in Week 6 (two-stage pipeline only)\n");
        }

        free_tokens(tokens);
        free(line);
    }

    return 0;
}
