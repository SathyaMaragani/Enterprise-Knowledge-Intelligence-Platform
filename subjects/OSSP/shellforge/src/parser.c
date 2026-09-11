#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"

#define TOKEN_BUFSIZE 64
#define TOKEN_DELIMITERS " \t\r\n\a"

/*
 * Splits a command line into a NULL-terminated argument vector.
 *
 * The result has exactly the shape execvp() expects:
 *
 *     "ls -l /home"  ->  argv[0] = "ls"
 *                        argv[1] = "-l"
 *                        argv[2] = "/home"
 *                        argv[3] = NULL
 *
 * strtok() writes '\0' over each delimiter it finds, so the returned
 * pointers point into `line` itself rather than to copies. `line` must stay
 * alive for as long as the tokens are used, and must be freed by the caller.
 */
char **parse_line(char *line) {
    int bufsize = TOKEN_BUFSIZE;
    int position = 0;
    char **tokens = malloc(sizeof(char *) * bufsize);
    char *token;

    if (!tokens) {
        fprintf(stderr, "ShellForge: allocation error\n");
        exit(EXIT_FAILURE);
    }

    token = strtok(line, TOKEN_DELIMITERS);
    while (token != NULL) {
        tokens[position] = token;
        position++;

        /* Keep one slot free so the terminating NULL always fits. */
        if (position >= bufsize) {
            bufsize += bufsize;
            char **temp = realloc(tokens, sizeof(char *) * bufsize);
            if (!temp) {
                fprintf(stderr, "ShellForge: allocation error\n");
                free(tokens);
                exit(EXIT_FAILURE);
            }
            tokens = temp;
        }

        token = strtok(NULL, TOKEN_DELIMITERS);
    }

    tokens[position] = NULL;
    return tokens;
}

/*
 * Frees only the vector, not the strings it holds: those are slices of the
 * caller's `line` buffer, which the caller frees separately.
 */
void free_tokens(char **tokens) {
    free(tokens);
}
