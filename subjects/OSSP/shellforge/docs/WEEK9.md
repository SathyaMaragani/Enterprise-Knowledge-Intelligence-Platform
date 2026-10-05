# OSSP Week 9 — File Descriptors and I/O Redirection (CO-5)

**Milestone:** support input, output, append and error redirection:
`<`, `>`, `>>` and `2>`.

Module: `include/redirect.h`, `src/redirect.c`.

## File descriptors

A file descriptor is a small integer that indexes the process's table of open
files. The kernel treats regular files, terminals, pipes and devices alike
through it.

| fd | Stream | Default |
|---|---|---|
| 0 | stdin | keyboard |
| 1 | stdout | terminal |
| 2 | stderr | terminal |

`open()` returns the lowest unused descriptor. `dup2(old, new)` makes `new`
refer to the same open file as `old`. Programs only ever write to "fd 1", so
after `dup2(file, 1)` their output goes to the file without the program knowing.

```
before:  fd 1 -> terminal                  after dup2(fd, 1); close(fd):
         fd 3 -> out.txt (from open())     fd 1 -> out.txt
                                           fd 3    (closed)
```

## Operators

| Operator | Stream | `open()` flags |
|---|---|---|
| `> file` | stdout | `O_WRONLY \| O_CREAT \| O_TRUNC`, mode `0644` |
| `>> file` | stdout | `O_WRONLY \| O_CREAT \| O_APPEND`, mode `0644` |
| `< file` | stdin | `O_RDONLY` |
| `2> file` | stderr | `O_WRONLY \| O_CREAT \| O_TRUNC`, mode `0644` |

- **`O_CREAT`** creates a missing file, with the given mode masked by the umask:
  `0644` becomes `-rw-r--r--`.
- **`O_TRUNC`** empties an existing file first.
- **`O_APPEND`** makes every write go to the current end of the file.

## How a redirected command runs

```
 "sort < fruit.txt > sorted.txt"
        |
   parse_line()   ->  sort  <  fruit.txt  >  sorted.txt  NULL
        |
   begin_redirection()             (in the shell process)
     save fds 0-2  (fcntl F_DUPFD_CLOEXEC)
     open fruit.txt -> dup2 onto 0
     open sorted.txt -> dup2 onto 1
     remove operators            ->  sort NULL
        |
   built-in?  no  -> fork() -> child inherits fds 0 and 1 -> execvp("sort")
        |
   end_redirection()               dup2 the saved fds back, close them
        |
   next prompt on the terminal
```

Redirections are applied in the shell, around both built-ins and forked
commands, so `pwd > file` and `help > file` work as well as `ls > file`.

- The saved copies are opened close-on-exec, so they never leak into the
  programs the shell runs.
- `end_redirection()` restores the terminal afterwards.
- Buffered stdio output is flushed before each switch, so nothing written for
  the terminal ends up in the file, or the other way round.

In a pipeline, each side's redirections are applied in its own child by
`exec_child()`, *after* the pipe is connected. So `sort < in | head -1 > out`
works, and an explicit redirection overrides the pipe for that stream, as in sh.

## Tokenising operators

`parse_line()` recognises the operators with or without surrounding spaces:

| Input | Tokens |
|---|---|
| `ls > out` | `ls` `>` `out` |
| `ls>out` | `ls` `>` `out` |
| `echo x>>log` | `echo` `x` `>>` `log` |
| `sort<in>out` | `sort` `<` `in` `>` `out` |
| `ls /nope 2>err` | `ls` `/nope` `2>` `err` |
| `echo a2>f` | `echo` `a2` `>` `f`: `2>` only when the `2` stands alone, as in sh |

Operators are returned as static strings, because the operator character in
the line is overwritten with `'\0'` to end the word in front of it.

## Differences from the handbook listing

The chapter's `redirect.c` handles only `>` and `>>`, though its milestone and
README list `<` and `2>` as well. It also forks a child of its own, separate
from the Week 4 executor, and is called *after* the built-in check, so
`pwd > file` would print to the terminal. ShellForge instead:

- implements all four operators;
- strips them from the vector, so the existing executor runs the command;
- covers built-ins;
- reports a missing file name (`echo hi >`) as a syntax error instead of
  calling `open(NULL)`.

## Errors

```
myshell> echo hi >
ShellForge: syntax error: '>' needs a file name
myshell> cat < missing.txt
ShellForge: missing.txt: No such file or directory
myshell> echo hi > /nonexistent_dir/f.txt
ShellForge: /nonexistent_dir/f.txt: No such file or directory
```

In each case the command is not run, the terminal is restored, and the shell
continues.

## Verification — `tests/test_week9.sh` (29 assertions)

| Area | Cases |
|---|---|
| `>` | writes, keeps output off the terminal, truncates, creates with mode 0644 |
| `>>` | appends in order, creates a missing file |
| `<` | feeds `sort`; a missing file is reported and the shell continues |
| `2>` | captures stderr; stdout and stderr to separate files; the shell's own "not found" message follows `2>` |
| Combined | `sort < in > out` |
| No spaces | `echo x>f`, `>>`, `sort<in>out`, and `a2>f` read as the word `a2` |
| Built-ins | `pwd > f`, `help > f`, terminal restored afterwards |
| Pipelines | `sort < in \| head -1 > out` |
| Errors | missing file name, operator as a file name, `open()` failure |
| Descriptor leaks | after redirections and a pipe, `ls /proc/self/fd` in a child lists only `0 1 2 3` |
| Sanitizers | ASan + UBSan over every case, no report |

The descriptor-leak check was confirmed against a mutation. Building with
`F_DUPFD` instead of `F_DUPFD_CLOEXEC` makes the test fail, listing the leaked
`10 11 12`.
