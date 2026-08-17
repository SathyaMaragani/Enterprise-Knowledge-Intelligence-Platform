#include <stdio.h>
#include <stdlib.h>
#include "input.h"

char *read_line(void) {
    int bufsize = 64;
    int position = 0;
    char *buffer = malloc(sizeof(char) * bufsize);
    int c;

    if (!buffer) {
        fprintf(stderr, "ShellForge: allocation error\n");
        exit(EXIT_FAILURE);
    }

    while (1) {
        c = getchar();

        if (c == EOF || c == '\n') {
            buffer[position] = '\0';
            return buffer;
        } else {
            buffer[position] = c;
        }
        position++;

        if (position >= bufsize) {
            bufsize += bufsize;
            char *temp = realloc(buffer, bufsize);
            if (!temp) {
                fprintf(stderr, "ShellForge: allocation error\n");
                free(buffer);
                exit(EXIT_FAILURE);
            }
            buffer = temp;
        }
    }
}
