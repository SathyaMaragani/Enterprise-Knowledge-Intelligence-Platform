#ifndef THREAD_H
#define THREAD_H

/*
 * Week 10: POSIX threads.
 *
 * The monitor is a second thread inside the shell process that prints a
 * heartbeat every SHELLFORGE_MONITOR seconds (default 10; 0 turns it off).
 * Every thread ShellForge creates starts with all signals blocked, so SIGINT
 * and SIGCHLD keep going to the main thread: there, foreground_begin() blocks
 * SIGCHLD while the shell waits for a command, and a reaper running in another
 * thread would steal that command's exit status.
 */
void start_monitor_thread(void);

/*
 * Wakes the monitor and joins it with pthread_join(), so the shell exits with
 * no thread still running and its stack and thread-local storage freed.
 */
void stop_monitor_thread(void);

/*
 * The `demo-threads [threads] [increments]` built-in: `threads` workers each
 * add 1 to a shared counter `increments` times, first with no lock and then
 * under a mutex, and the totals are compared with the expected value.
 * Returns 0, or -1 if the arguments are out of range.
 */
int run_thread_demo(char **args);

#endif // THREAD_H
