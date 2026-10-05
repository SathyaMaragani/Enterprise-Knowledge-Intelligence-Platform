#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"

#define TOKEN_BUFSIZE 64

/*
 * Operators are returned as these static strings rather than slices of the
 * line: the operator character is overwritten with '\0' to end the word in
 * front of it, as in "ls>out".
 */
static char PIPE_TOKEN[] = "|";
static char IN_TOKEN[] = "<";
static char OUT_TOKEN[] = ">";
static char APPEND_TOKEN[] = ">>";
static char ERR_TOKEN[] = "2>";

static inline int is_delimiter(char c) {
    return (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\a');
}

static inline int is_operator_char(char c) {
    return (c == '|' || c == '<' || c == '>');
}

/* The operator starting at p, and its length in *len. p must be an operator char. */
static char *match_operator(const char *p, int *len) {
    *len = 1;
    if (*p == '|') {
        return PIPE_TOKEN;
    }
    if (*p == '<') {
        return IN_TOKEN;
    }
    if (p[1] == '>') {
        *len = 2;
        return APPEND_TOKEN;
    }
    return OUT_TOKEN;
}

/* Appends a token, doubling the vector when only the slot for NULL is left. */
static void push(char ***tokens, int *position, int *bufsize, char *token) {
    (*tokens)[(*position)++] = token;
    if (*position >= *bufsize) {
        *bufsize += *bufsize;
        char **temp = realloc(*tokens, sizeof(char *) * *bufsize);
        if (!temp) {
            fprintf(stderr, "ShellForge: allocation error\n");
            free(*tokens);
            exit(EXIT_FAILURE);
        }
        *tokens = temp;
    }
}

/*
 * Splits a command line into a NULL-terminated argument vector.
 *
 * Slices strings directly in the caller's `line` buffer by inserting '\0'.
 * Recognizes the operators | < > >> and 2> as individual tokens, whether
 * surrounded by whitespace ("ls > out") or adjacent to words ("ls>out").
 * As in sh, "2>" is the stderr operator only when the 2 stands alone before
 * the '>': "echo a2>f" is the word "a2" with stdout sent to f.
 */
char **parse_line(char *line) {
    int bufsize = TOKEN_BUFSIZE;
    int position = 0;
    char **tokens = malloc(sizeof(char *) * bufsize);
    char *p = line;
    int len;

    if (!tokens) {
        fprintf(stderr, "ShellForge: allocation error\n");
        exit(EXIT_FAILURE);
    }

    while (*p != '\0') {
        if (is_delimiter(*p)) {
            *p++ = '\0'; /* also ends the word before it */
            continue;
        }

        if (is_operator_char(*p)) {
            char *op = match_operator(p, &len);
            push(&tokens, &position, &bufsize, op);
            p += len;
            continue;
        }

        /* Start of a normal token */
        char *start = p;
        while (*p != '\0' && !is_delimiter(*p) && !is_operator_char(*p)) {
            p++;
        }

        if (*p == '>' && p - start == 1 && *start == '2') {
            push(&tokens, &position, &bufsize, ERR_TOKEN);
            p++;
            continue;
        }

        if (is_operator_char(*p)) {
            /* Read the operator before its first character becomes the terminator. */
            char *op = match_operator(p, &len);
            *p = '\0';
            push(&tokens, &position, &bufsize, start);
            push(&tokens, &position, &bufsize, op);
            p += len;
            continue;
        }

        push(&tokens, &position, &bufsize, start);
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
