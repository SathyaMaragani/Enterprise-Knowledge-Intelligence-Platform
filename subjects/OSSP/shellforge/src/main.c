#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "shell.h"
#include "input.h"

int main(void) {
    char *line;

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
            printf("You entered : %s\n", line);
        }

        free(line);
    }

    return 0;
}
