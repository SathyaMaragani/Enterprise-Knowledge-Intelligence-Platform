CC = gcc
CFLAGS = -Wall -Wextra -g -Iinclude

SRCS = src/main.c src/input.c src/parser.c src/executor.c src/builtin.c \
       src/signals.c src/pipes.c src/redirect.c
OBJS = $(SRCS:.c=.o)
TARGET = bin/shellforge

all: $(TARGET)

$(TARGET): $(OBJS)
	@mkdir -p bin
	$(CC) $(CFLAGS) -o $@ $^

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

# Week 8: rebuild with AddressSanitizer + UndefinedBehaviorSanitizer.
asan: clean
	$(MAKE) CFLAGS="$(CFLAGS) -fsanitize=address,undefined"

# Week 8: leak check of an interactive session (needs valgrind).
valgrind: all
	valgrind --leak-check=full --show-leak-kinds=all --child-silent-after-fork=yes ./$(TARGET)

clean:
	rm -f $(OBJS)
	rm -rf bin
