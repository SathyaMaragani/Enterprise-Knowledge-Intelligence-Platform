#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "shell.h"
#include "input.h"
#include "parser.h"

int main(void) {
    char *line;
    char **tokens;
    int i;

    printf("=====================================\n");
    printf(" Welcome to %s Version %s\n", SHELL_NAME, VERSION);
    printf("=====================================\n\n");

    while (1) {
        printf("myshell> ");
        line = read_line();

        if (line[0] == '\0' && feof(stdin)) {
            printf("\n");
            free(line);
            break;
        }

        if (strcmp(line, "exit") == 0) {
            printf("Exiting %s...\n", SHELL_NAME);
            free(line);
            break;
        }

        if (line[0] != '\0') {
            tokens = parse_line(line);

            /* A line of nothing but whitespace parses to an empty vector. */
            if (tokens[0] != NULL) {
                printf("\nParsed Tokens\n");
                for (i = 0; tokens[i] != NULL; i++) {
                    printf("argv[%d] = %s\n", i, tokens[i]);
                }
                printf("argv[%d] = NULL\n", i);
            }

            free_tokens(tokens);
        }

        free(line);
    }

    return 0;
}
