#ifndef PIPES_H
#define PIPES_H

/*
 * Executes a two-process pipeline: args1 | args2.
 * Connects stdout of child 1 to write-end of pipe,
 * and stdin of child 2 to read-end of pipe.
 * Closes unused file descriptors in parent and children to ensure proper EOF.
 * Each side may carry its own redirections, applied after the pipe ends.
 */
int execute_pipeline(char **args1, char **args2);

/*
 * Demonstrates inter-process communication (IPC) fundamentals using pipe():
 * - Parent -> Child communication
 * - Child -> Parent communication
 * - EOF detection upon closing the pipe's write end
 */
int run_ipc_demo(void);

#endif // PIPES_H
