#ifndef BUILTIN_H
#define BUILTIN_H

/*
 * Return values from execute_builtin().
 *
 * The caller needs three outcomes, not two: a command that was not a built-in
 * (so it must be forked and exec'd), one that was handled here, and `exit`,
 * which has to unwind through main() so the line buffer and token vector are
 * freed before the process ends.
 */
#define BUILTIN_NOT_FOUND 0
#define BUILTIN_HANDLED   1
#define BUILTIN_EXIT      2

/*
 * Runs `args[0]` as a built-in command if it is one.
 *
 * Built-ins execute inside the shell process rather than a forked child,
 * because a child cannot change the shell's own state: `cd` in a child would
 * change that child's working directory and then exit, leaving the parent
 * exactly where it was.
 *
 * Returns BUILTIN_NOT_FOUND when args[0] is not a built-in, so the caller can
 * fall through to external execution.
 */
int execute_builtin(char **args);

#endif // BUILTIN_H
