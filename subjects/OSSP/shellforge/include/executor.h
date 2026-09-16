#ifndef EXECUTOR_H
#define EXECUTOR_H

/*
 * Executes a single command by creating a child process via fork(),
 * loading the program via execvp(), and waiting for completion
 * in the parent via waitpid().
 *
 * Parameters:
 *   args - NULL-terminated array of arguments (argv format)
 *
 * Returns:
 *   Exit status of the executed program, or -1 on fork failure.
 */
int execute_command(char **args);

/*
 * Executes a two-process pipeline: args1 | args2.
 * Connects stdout of child 1 to write-end of pipe,
 * and stdin of child 2 to read-end of pipe.
 * Closes unused file descriptors in parent and children to ensure proper EOF.
 */
int execute_pipeline(char **args1, char **args2);

/*
 * Demonstrates inter-process communication (IPC) fundamentals using pipe():
 * - Parent -> Child communication
 * - Child -> Parent communication
 * - EOF detection upon closing the pipe's write end
 */
int run_ipc_demo(void);

#endif // EXECUTOR_H
