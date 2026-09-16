#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include "builtin.h"

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

/*
 * Environment variables surfaced by the `env` built-in. getenv() returns NULL
 * for anything unset, and passing NULL to printf("%s") is undefined behaviour,
 * so every lookup here goes through a guard.
 */
static const char *ENV_KEYS[] = {"HOME", "USER", "PATH", "SHELL", "PWD"};
#define ENV_KEY_COUNT (sizeof(ENV_KEYS) / sizeof(ENV_KEYS[0]))

static const char *env_or_unset(const char *name) {
    const char *value = getenv(name);
    return value ? value : "(not set)";
}

/*
 * Prints the working directory.
 *
 * getcwd() can fail -- the directory may have been deleted underneath us, or
 * the path may exceed the buffer -- so the result is checked rather than
 * printing an uninitialised buffer.
 */
static int builtin_pwd(void) {
    char cwd[PATH_MAX];

    if (getcwd(cwd, sizeof(cwd)) == NULL) {
        fprintf(stderr, "ShellForge: pwd: %s\n", strerror(errno));
        return BUILTIN_HANDLED;
    }

    printf("%s\n", cwd);
    return BUILTIN_HANDLED;
}

/*
 * Changes the shell's own working directory.
 *
 * With no argument this follows $HOME, the behaviour of every standard shell.
 * On success PWD is updated so a subsequent `env` does not report a stale
 * directory -- the kernel tracks the real working directory, but PWD is just
 * an ordinary environment variable and does not follow chdir() on its own.
 */
static int builtin_cd(char **args) {
    const char *target = args[1];

    if (target == NULL) {
        target = getenv("HOME");
        if (target == NULL) {
            fprintf(stderr, "ShellForge: cd: HOME not set\n");
            return BUILTIN_HANDLED;
        }
    }

    if (args[1] != NULL && args[2] != NULL) {
        fprintf(stderr, "ShellForge: cd: too many arguments\n");
        return BUILTIN_HANDLED;
    }

    if (chdir(target) != 0) {
        fprintf(stderr, "ShellForge: cd: %s: %s\n", target, strerror(errno));
        return BUILTIN_HANDLED;
    }

    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) != NULL) {
        setenv("PWD", cwd, 1);
    }

    return BUILTIN_HANDLED;
}

/*
 * Clears the screen with the ANSI erase-display and cursor-home sequences.
 *
 * The chapter listing uses system("clear"), which forks a shell to run an
 * external binary that is not present in every image. Writing the escape
 * sequence directly costs no process and cannot fail for that reason.
 */
static int builtin_clear(void) {
    printf("\033[2J\033[H");
    fflush(stdout);
    return BUILTIN_HANDLED;
}

static int builtin_help(void) {
    printf("\n%s built-in commands\n", "ShellForge");
    printf("---------------------------------------------\n");
    printf("  cd [dir]   change the shell's working directory (defaults to $HOME)\n");
    printf("  pwd        print the working directory\n");
    printf("  env        show common environment variables\n");
    printf("  clear      clear the screen\n");
    printf("  help       show this list\n");
    printf("  exit       leave the shell\n");
    printf("\nAnything else is executed as an external program via fork/execvp.\n\n");
    return BUILTIN_HANDLED;
}

static int builtin_env(void) {
    size_t i;

    for (i = 0; i < ENV_KEY_COUNT; i++) {
        printf("%s=%s\n", ENV_KEYS[i], env_or_unset(ENV_KEYS[i]));
    }
    return BUILTIN_HANDLED;
}

int execute_builtin(char **args) {
    if (args == NULL || args[0] == NULL) {
        return BUILTIN_HANDLED; /* nothing to run, but nothing to fork either */
    }

    if (strcmp(args[0], "exit") == 0) {
        /*
         * Deliberately not exit() here. main() still holds the heap-allocated
         * line and token vector; unwinding lets it free them, which is what
         * keeps the leak checker quiet on a normal exit.
         */
        return BUILTIN_EXIT;
    }
    if (strcmp(args[0], "cd") == 0) {
        return builtin_cd(args);
    }
    if (strcmp(args[0], "pwd") == 0) {
        return builtin_pwd();
    }
    if (strcmp(args[0], "clear") == 0) {
        return builtin_clear();
    }
    if (strcmp(args[0], "help") == 0) {
        return builtin_help();
    }
    if (strcmp(args[0], "env") == 0) {
        return builtin_env();
    }

    return BUILTIN_NOT_FOUND;
}
