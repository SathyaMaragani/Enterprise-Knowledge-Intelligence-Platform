#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"

#define TOKEN_BUFSIZE 64
static char PIPE_TOKEN[] = "|";

static inline int is_delimiter(char c) {
    return (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\a');
}

/*
 * Splits a command line into a NULL-terminated argument vector.
 *
 * Slices strings directly in the caller's `line` buffer by inserting '\0'.
 * Recognizes the pipe metacharacter '|' as an individual token, whether
 * surrounded by whitespace (e.g. "cmd1 | cmd2") or adjacent to arguments
 * (e.g. "cmd1|cmd2").
 */
char **parse_line(char *line) {
    int bufsize = TOKEN_BUFSIZE;
    int position = 0;
    char **tokens = malloc(sizeof(char *) * bufsize);
    char *p = line;

    if (!tokens) {
        fprintf(stderr, "ShellForge: allocation error\n");
        exit(EXIT_FAILURE);
    }

    while (*p != '\0') {
        /* Skip leading delimiters */
        while (*p != '\0' && is_delimiter(*p)) {
            *p = '\0';
            p++;
        }
        if (*p == '\0') {
            break;
        }

        if (*p == '|') {
            /* Pipe metacharacter is its own token */
            *p = '\0';
            tokens[position++] = PIPE_TOKEN;
            p++;
        } else {
            /* Start of a normal token */
            tokens[position++] = p;
            while (*p != '\0' && !is_delimiter(*p) && *p != '|') {
                p++;
            }
            if (*p == '|') {
                /* Terminate this token and record pipe token in the next slot */
                *p = '\0';
                p++;
                if (position >= bufsize - 1) {
                    bufsize += bufsize;
                    char **temp = realloc(tokens, sizeof(char *) * bufsize);
                    if (!temp) {
                        fprintf(stderr, "ShellForge: allocation error\n");
                        free(tokens);
                        exit(EXIT_FAILURE);
                    }
                    tokens = temp;
                }
                tokens[position++] = PIPE_TOKEN;
            } else if (*p != '\0') {
                *p = '\0';
                p++;
            }
        }

        /* Keep at least one slot free so the terminating NULL always fits */
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
    }

    tokens[position] = NULL;
    return tokens;
}

/*
 * Frees only the vector, not the strings it holds: those are slices of the
 * caller's `line` buffer or static token strings.
 */
void free_tokens(char **tokens) {
    free(tokens);
}
