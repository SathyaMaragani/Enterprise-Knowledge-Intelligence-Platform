#ifndef EXECUTOR_H
#define EXECUTOR_H

#include <sys/types.h>

/*
 * Executes a single command by creating a child process via fork(),
 * loading the program via execvp(), and waiting for completion
 * in the parent via waitpid().
 *
 * Parameters:
 *   args - NULL-terminated array of arguments (argv format)
 *
 * Returns:
 *   Exit status of the executed program (128 + signal number if a signal
 *   ended it), or -1 on fork failure.
 */
int execute_command(char **args);

/*
 * The child's half of every command: restores default signal handling,
 * applies any redirections left in args, then execvp(). Never returns --
 * a failed exec ends the child with _exit(127) for "not found", else 1.
 */
void exec_child(char **args);

/*
 * Waits for one child, retrying when a signal interrupts the wait, and
 * returns its status in the shell convention: the exit code, or 128 + the
 * signal number. Returns -1 if the wait itself fails.
 */
int wait_child(pid_t pid);

#endif // EXECUTOR_H
