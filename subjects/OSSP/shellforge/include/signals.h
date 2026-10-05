#ifndef SIGNALS_H
#define SIGNALS_H

/*
 * Installs the shell's signal dispositions:
 *   SIGINT  (Ctrl+C) -- caught, so the shell survives; the foreground child,
 *                       which restores the default, is the one terminated.
 *   SIGTSTP (Ctrl+Z) -- ignored by the shell and the programs it starts:
 *                       without job control there is no `fg` to resume a
 *                       stopped child, so stopping it would hang the shell.
 *   SIGCHLD          -- caught; the handler reaps any child that no
 *                       waitpid() is waiting for, so none is left a zombie.
 */
void initialize_signals(void);

/*
 * Bracket every fork()..waitpid() region. SIGCHLD stays blocked in between,
 * so the reaper cannot collect the foreground child -- and its exit status --
 * before the shell's own waitpid() does. A SIGCHLD that arrives meanwhile is
 * delivered when foreground_end() unblocks it.
 */
void foreground_begin(void);
void foreground_end(void);

/*
 * Called in a child between fork() and execvp(): restores the default SIGINT
 * and SIGCHLD actions and unblocks SIGCHLD, because a blocked mask survives
 * exec and the new program would otherwise start with SIGCHLD blocked.
 */
void reset_child_signals(void);

#endif // SIGNALS_H
