package texthack.core;

/**
 * A growable list of primitive ints.
 *
 * <p>This exists because the subject rules forbid {@code java.util.*} inside the
 * algorithm implementations, which rules out {@code ArrayList}. It would also be
 * the wrong tool regardless: {@code ArrayList<Integer>} boxes every match
 * position into an object, and match positions are exactly the kind of dense
 * primitive data that boxing penalises hardest.
 *
 * <p>Growth is by doubling, giving amortised O(1) {@link #add}.
 *
 * <p>Time: add O(1) amortised, toArray O(n). Space: O(capacity).
 */
public final class IntList {

    private static final int DEFAULT_CAPACITY = 16;

    private int[] items;
    private int size;

    public IntList() {
        this(DEFAULT_CAPACITY);
    }

    public IntList(int initialCapacity) {
        if (initialCapacity < 1) {
            initialCapacity = 1;
        }
        this.items = new int[initialCapacity];
        this.size = 0;
    }

    public void add(int value) {
        if (size == items.length) {
            grow();
        }
        items[size] = value;
        size++;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + " size " + size);
        }
        return items[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Returns a copy trimmed to the current size. */
    public int[] toArray() {
        int[] copy = new int[size];
        for (int i = 0; i < size; i++) {
            copy[i] = items[i];
        }
        return copy;
    }

    private void grow() {
        int newCapacity = items.length * 2;
        int[] larger = new int[newCapacity];
        for (int i = 0; i < size; i++) {
            larger[i] = items[i];
        }
        items = larger;
    }
}
