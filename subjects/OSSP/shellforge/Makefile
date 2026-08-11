CC = gcc
CFLAGS = -Wall -Wextra -Iinclude
SRC_DIR = src
BIN_DIR = bin

all: shellforge

shellforge:
	mkdir -p $(BIN_DIR)
	$(CC) $(CFLAGS) -o $(BIN_DIR)/shellforge $(SRC_DIR)/main.c

clean:
	rm -rf $(BIN_DIR)
