CC = gcc
CFLAGS = -Wall -Wextra -g -Iinclude
LDFLAGS = -pthread

SRCS = src/main.c src/input.c src/parser.c src/executor.c src/builtin.c \
       src/signals.c src/pipes.c src/redirect.c src/thread.c
OBJS = $(SRCS:.c=.o)
TARGET = bin/shellforge

all: $(TARGET)

$(TARGET): $(OBJS)
	@mkdir -p bin
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

run: all
	./$(TARGET)

test: all
	@sh tests/test_week4.sh
	@sh tests/test_week5.sh
	@sh tests/test_week6.sh
	@sh tests/test_week7.sh
	@sh tests/test_week8.sh
	@sh tests/test_week9.sh
	@sh tests/test_week10.sh

# Week 8: rebuild with AddressSanitizer + UndefinedBehaviorSanitizer.
asan: clean
	$(MAKE) CFLAGS="$(CFLAGS) -fsanitize=address,undefined"

# Week 8: leak check of an interactive session (needs valgrind).
valgrind: all
	valgrind --leak-check=full --show-leak-kinds=all --child-silent-after-fork=yes ./$(TARGET)

clean:
	rm -f $(OBJS)
	rm -rf bin
