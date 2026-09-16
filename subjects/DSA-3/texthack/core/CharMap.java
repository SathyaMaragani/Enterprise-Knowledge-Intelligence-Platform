package texthack.core;

/**
 * A small map from {@code char} to {@code int}, kept sorted by key.
 *
 * <p>Aho-Corasick needs child lookup on each trie node, and the two obvious
 * options are both wrong here. {@code HashMap} is barred by the subject rules
 * (and boxes both key and value). A flat {@code int[65536]} per node indexes the
 * whole UTF-16 code unit range, which would cost 256 KB per node and make the
 * automaton unusable on anything but a toy alphabet.
 *
 * <p>Trie nodes are overwhelmingly sparse -- most have a handful of children --
 * so parallel sorted arrays with a binary search are the right shape: memory
 * proportional to actual children, and O(log k) lookup.
 *
 * <p>Time: get O(log k), put O(k) worst case from the shift. Space: O(k).
 */
public final class CharMap {

    private static final int DEFAULT_CAPACITY = 4;

    private char[] keys;
    private int[] values;
    private int size;

    public CharMap() {
        this.keys = new char[DEFAULT_CAPACITY];
        this.values = new int[DEFAULT_CAPACITY];
        this.size = 0;
    }

    public int size() {
        return size;
    }

    /** Key at slot {@code i} in ascending key order. */
    public char keyAt(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("index " + i + " size " + size);
        }
        return keys[i];
    }

    /** Value at slot {@code i} in ascending key order. */
    public int valueAt(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("index " + i + " size " + size);
        }
        return values[i];
    }

    /** Returns the value for {@code key}, or {@code -1} when absent. */
    public int get(char key) {
        int slot = indexOf(key);
        return slot < 0 ? -1 : values[slot];
    }

    /** Inserts or overwrites, keeping keys in ascending order. */
    public void put(char key, int value) {
        int slot = indexOf(key);
        if (slot >= 0) {
            values[slot] = value;
            return;
        }

        int insertAt = -(slot + 1);
        if (size == keys.length) {
            grow();
        }
        for (int i = size; i > insertAt; i--) {
            keys[i] = keys[i - 1];
            values[i] = values[i - 1];
        }
        keys[insertAt] = key;
        values[insertAt] = value;
        size++;
    }

    /**
     * Binary search. Returns the slot when found, otherwise
     * {@code -(insertionPoint) - 1}, matching the convention used by the JDK's
     * own binary search so the encoding is familiar rather than novel.
     */
    private int indexOf(char key) {
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            char midKey = keys[mid];
            if (midKey < key) {
                low = mid + 1;
            } else if (midKey > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    private void grow() {
        int capacity = keys.length * 2;
        char[] newKeys = new char[capacity];
        int[] newValues = new int[capacity];
        for (int i = 0; i < size; i++) {
            newKeys[i] = keys[i];
            newValues[i] = values[i];
        }
        keys = newKeys;
        values = newValues;
    }
}
