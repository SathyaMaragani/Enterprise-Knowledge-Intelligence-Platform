#ifndef REDIRECT_H
#define REDIRECT_H

/*
 * Applies the redirections in args to this process and removes them from
 * args, leaving a plain argv for execvp():
 *
 *   < file    stdin  from file   (O_RDONLY)
 *   > file    stdout to file     (O_WRONLY | O_CREAT | O_TRUNC, 0644)
 *   >> file   stdout to file     (O_WRONLY | O_CREAT | O_APPEND, 0644)
 *   2> file   stderr to file     (O_WRONLY | O_CREAT | O_TRUNC, 0644)
 *
 * Each target is opened with open() and moved onto fd 0, 1 or 2 with dup2();
 * later redirections of the same stream win, as in sh. Returns 0, or -1
 * after printing why (missing file name, or open() failed).
 */
int apply_redirections(char **args);

/*
 * The same for a command the shell runs itself (a built-in, or the parent
 * side of a single command): saves fds 0-2 into saved[] before redirecting,
 * so end_redirection() can put the terminal back. Returns 0 or -1; on -1 the
 * streams are already restored. Does nothing if args has no redirection.
 */
int begin_redirection(char **args, int saved[3]);
void end_redirection(int saved[3]);

#endif // REDIRECT_H
