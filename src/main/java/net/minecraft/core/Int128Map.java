package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** Two-long keys with no key or entry allocation during probing or cursor traversal. */
public final class Int128Map<V> {
    private long[] keyHi;
    private long[] keyLo;
    private Object[] values;
    private int n;
    private int size;
    private int version;
    private boolean containsNullKey;

    public Int128Map() {
        this(16);
    }

    public Int128Map(int expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        n = HashCommon.arraySize(expected, 0.75F);
        keyHi = new long[n + 1];
        keyLo = new long[n + 1];
        values = new Object[n + 1];
    }

    private static long hash(long hi, long lo) {
        return HashCommon.mix(hi) ^ Long.rotateLeft(HashCommon.mix(lo), 32);
    }

    private boolean occupied(int slot) {
        return keyHi[slot] != 0L || keyLo[slot] != 0L;
    }

    private int find(long hi, long lo) {
        if ((hi | lo) == 0L) return containsNullKey ? n : -n - 1;
        int slot = (int)hash(hi, lo) & (n - 1);
        while (occupied(slot)) {
            if (keyHi[slot] == hi && keyLo[slot] == lo) return slot;
            slot = (slot + 1) & (n - 1);
        }
        return -slot - 1;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean containsKey(long hi, long lo) {
        return find(hi, lo) >= 0;
    }

    @SuppressWarnings("unchecked")
    private V value(int slot) {
        return (V)values[slot];
    }

    /** Null values are supported; use containsKey to distinguish them from absence. */
    public V get(long hi, long lo) {
        int slot = find(hi, lo);
        return slot < 0 ? null : value(slot);
    }

    public V put(long hi, long lo, V value) {
        int slot = find(hi, lo);
        if (slot >= 0) {
            V previous = value(slot);
            values[slot] = value;
            return previous;
        }
        // Grow before mutation so a failed allocation leaves the table intact.
        if (size + 1 >= HashCommon.maxFill(n, 0.75F)) {
            rehash(HashCommon.arraySize(size + 2, 0.75F));
            slot = find(hi, lo);
        }
        slot = -slot - 1;
        if (slot == n) containsNullKey = true;
        keyHi[slot] = hi;
        keyLo[slot] = lo;
        values[slot] = value;
        ++size;
        ++version;
        return null;
    }

    public V remove(long hi, long lo) {
        int slot = find(hi, lo);
        if (slot < 0) return null;
        V previous = value(slot);
        if (slot == n) {
            containsNullKey = false;
            values[n] = null;
        } else {
            shiftKeys(slot);
        }
        --size;
        ++version;
        return previous;
    }

    private void shiftKeys(int hole) {
        for (;;) {
            int slot = (hole + 1) & (n - 1);
            while (occupied(slot)) {
                int home = (int)hash(keyHi[slot], keyLo[slot]) & (n - 1);
                if (hole <= slot ? hole >= home || home > slot : hole >= home && home > slot) break;
                slot = (slot + 1) & (n - 1);
            }
            if (!occupied(slot)) {
                keyHi[hole] = 0L;
                keyLo[hole] = 0L;
                values[hole] = null;
                return;
            }
            keyHi[hole] = keyHi[slot];
            keyLo[hole] = keyLo[slot];
            values[hole] = values[slot];
            hole = slot;
        }
    }

    private void rehash(int capacity) {
        long[] hi = new long[capacity + 1];
        long[] lo = new long[capacity + 1];
        Object[] data = new Object[capacity + 1];
        for (int i = 0; i < n; ++i) {
            if (!occupied(i)) continue;
            int slot = (int)hash(keyHi[i], keyLo[i]) & (capacity - 1);
            while ((hi[slot] | lo[slot]) != 0L) slot = (slot + 1) & (capacity - 1);
            hi[slot] = keyHi[i];
            lo[slot] = keyLo[i];
            data[slot] = values[i];
        }
        data[capacity] = values[n];
        keyHi = hi;
        keyLo = lo;
        values = data;
        n = capacity;
    }

    public void clear() {
        if (size == 0) return;
        Arrays.fill(keyHi, 0L);
        Arrays.fill(keyLo, 0L);
        Arrays.fill(values, null);
        containsNullKey = false;
        size = 0;
        ++version;
    }

    /** One cursor allocation per traversal, no allocation per entry. Structural changes fail fast. */
    public Cursor cursor() {
        return new Cursor();
    }

    public final class Cursor {
        private final int expectedVersion = version;
        private int next;
        private int current = -1;

        private void check() {
            if (expectedVersion != version) throw new ConcurrentModificationException();
        }

        public boolean advance() {
            check();
            while (next < n && !occupied(next)) ++next;
            if (next < n || next == n && containsNullKey) {
                current = next++;
                return true;
            }
            current = -1;
            return false;
        }

        private int slot() {
            check();
            if (current < 0) throw new NoSuchElementException();
            return current;
        }

        public long hi() { return keyHi[slot()]; }
        public long lo() { return keyLo[slot()]; }
        public V value() { return Int128Map.this.value(slot()); }
    }
}
