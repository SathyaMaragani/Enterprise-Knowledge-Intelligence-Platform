#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "thread.h"

#define DEFAULT_MONITOR_SECONDS 10
#define DEFAULT_THREADS 4
#define MAX_THREADS 16
#define DEFAULT_INCREMENTS 100000L
#define MAX_INCREMENTS 1000000L

/*
 * Starts `fn` in a new thread with every signal blocked. A new thread inherits
 * its creator's signal mask, so the mask is filled around pthread_create() and
 * then restored. See thread.h for why signals must stay on the main thread.
 */
static int spawn(pthread_t *tid, void *(*fn)(void *), void *arg) {
    sigset_t all, old;

    sigfillset(&all);
    pthread_sigmask(SIG_SETMASK, &all, &old);
    int rc = pthread_create(tid, NULL, fn, arg);
    pthread_sigmask(SIG_SETMASK, &old, NULL);
    return rc;
}

/* ---------------------------------------------------------------- monitor */

static pthread_t monitor_tid;
static int monitor_started;
static int monitor_seconds;
static int monitor_stop; /* guarded by monitor_lock */
static pthread_mutex_t monitor_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t monitor_wake = PTHREAD_COND_INITIALIZER;

/*
 * The handbook's monitor calls sleep(10) in a loop and is detached, so nothing
 * can stop it: the process ends with the thread mid-sleep. Waiting on a
 * condition variable with a deadline instead lets stop_monitor_thread() wake
 * it at once and join it. The inner loop absorbs spurious wake-ups.
 */
static void *monitor(void *arg) {
    (void)arg;
    pthread_mutex_lock(&monitor_lock);
    while (!monitor_stop) {
        struct timespec deadline;
        int rc = 0;

        clock_gettime(CLOCK_REALTIME, &deadline);
        deadline.tv_sec += monitor_seconds;
        while (!monitor_stop && rc != ETIMEDOUT) {
            rc = pthread_cond_timedwait(&monitor_wake, &monitor_lock, &deadline);
        }
        if (!monitor_stop) {
            printf("\n[Monitor] ShellForge Running...\n");
            fflush(stdout);
        }
    }
    pthread_mutex_unlock(&monitor_lock);
    return NULL;
}

void start_monitor_thread(void) {
    const char *setting = getenv("SHELLFORGE_MONITOR");

    monitor_seconds = setting ? atoi(setting) : DEFAULT_MONITOR_SECONDS;
    if (monitor_seconds <= 0) {
        return;
    }
    int rc = spawn(&monitor_tid, monitor, NULL);
    if (rc != 0) {
        /* pthread_* functions return the error rather than setting errno. */
        fprintf(stderr, "ShellForge: monitor thread: %s\n", strerror(rc));
        return;
    }
    monitor_started = 1;
}

void stop_monitor_thread(void) {
    if (!monitor_started) {
        return;
    }
    pthread_mutex_lock(&monitor_lock);
    monitor_stop = 1;
    pthread_cond_signal(&monitor_wake);
    pthread_mutex_unlock(&monitor_lock);
    pthread_join(monitor_tid, NULL);
    monitor_started = 0;
}

/* ---------------------------------------------------------------- race demo */

/*
 * The handbook's shared `counter`. volatile keeps every ++ a real load, add and
 * store, as at -O0, instead of one register written back when the loop ends.
 * It does not make ++ atomic: that is the race the demo shows.
 */
static volatile long counter;
static pthread_mutex_t counter_lock = PTHREAD_MUTEX_INITIALIZER;

static void *unsafe_worker(void *arg) {
    long increments = *(const long *)arg;

    for (long i = 0; i < increments; i++) {
        counter++; /* two threads can load the same value; one update is lost */
    }
    return NULL;
}

static void *safe_worker(void *arg) {
    long increments = *(const long *)arg;

    for (long i = 0; i < increments; i++) {
        pthread_mutex_lock(&counter_lock);
        counter++; /* the critical section: one thread at a time */
        pthread_mutex_unlock(&counter_lock);
    }
    return NULL;
}

/* Runs `count` workers and joins them all. Returns the milliseconds taken, or -1. */
static double run_workers(void *(*worker)(void *), int count, long *increments) {
    pthread_t tids[MAX_THREADS];
    struct timespec start, end;
    int started;

    counter = 0;
    clock_gettime(CLOCK_MONOTONIC, &start);
    for (started = 0; started < count; started++) {
        int rc = spawn(&tids[started], worker, increments);
        if (rc != 0) {
            fprintf(stderr, "ShellForge: pthread_create: %s\n", strerror(rc));
            break;
        }
    }
    for (int i = 0; i < started; i++) {
        pthread_join(tids[i], NULL); /* blocks until worker i has finished */
    }
    clock_gettime(CLOCK_MONOTONIC, &end);

    if (started < count) {
        return -1;
    }
    return (end.tv_sec - start.tv_sec) * 1e3 + (end.tv_nsec - start.tv_nsec) / 1e6;
}

/* A whole decimal number within [min, max], or -1. */
static int parse_range(const char *text, long min, long max, long *out) {
    char *end;

    errno = 0;
    long value = strtol(text, &end, 10);
    if (errno != 0 || end == text || *end != '\0' || value < min || value > max) {
        return -1;
    }
    *out = value;
    return 0;
}

int run_thread_demo(char **args) {
    long threads = DEFAULT_THREADS;
    long increments = DEFAULT_INCREMENTS;

    if ((args[1] != NULL && parse_range(args[1], 1, MAX_THREADS, &threads) != 0)
        || (args[1] != NULL && args[2] != NULL && parse_range(args[2], 1, MAX_INCREMENTS, &increments) != 0)
        || (args[1] != NULL && args[2] != NULL && args[3] != NULL)) {
        fprintf(stderr, "ShellForge: usage: demo-threads [threads 1-%d] [increments 1-%ld]\n",
                MAX_THREADS, MAX_INCREMENTS);
        return -1;
    }

    long expected = threads * increments;
    const char *plural = threads == 1 ? "" : "s";
    printf("[Threads] %ld thread%s x %ld increments, expected total %ld\n", threads, plural, increments, expected);

    double ms = run_workers(unsafe_worker, (int)threads, &increments);
    if (ms < 0) {
        return -1;
    }
    long racy = counter;
    printf("  Without a mutex: counter = %ld (%ld updates lost to the race), %.1f ms\n",
           racy, expected - racy, ms);

    ms = run_workers(safe_worker, (int)threads, &increments);
    if (ms < 0) {
        return -1;
    }
    printf("  With a mutex:    counter = %ld (%ld lost), %.1f ms\n", counter, expected - counter, ms);
    if (threads == 1) {
        printf("[Threads] the worker joined with pthread_join()\n");
    } else {
        printf("[Threads] all %ld workers joined with pthread_join()\n", threads);
    }
    fflush(stdout);
    return 0;
}
