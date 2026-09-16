CC = gcc
CFLAGS = -Wall -Wextra -g -Iinclude

SRCS = src/main.c src/input.c src/parser.c src/executor.c src/builtin.c
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

clean:
	rm -f $(OBJS)
	rm -rf bin
