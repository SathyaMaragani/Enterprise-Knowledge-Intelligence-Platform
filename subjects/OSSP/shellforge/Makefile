CC = gcc
CFLAGS = -Wall -Wextra -g -Iinclude

SRCS = src/main.c src/input.c
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

clean:
	rm -f $(OBJS)
	rm -rf bin
