package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** Two-long keys and primitive values, with no per-entry traversal allocations. */
public final class Int1282ByteMap {
    private long[] keyHi;
    private long[] keyLo;
    private byte[] values;
    private int n;
    private int size;
    private int version;
    private boolean containsNullKey;
    private byte defaultReturnValue;

    public Int1282ByteMap() { this(16); }

    public Int1282ByteMap(int expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        n = HashCommon.arraySize(expected, 0.75F);
        keyHi = new long[n + 1];
        keyLo = new long[n + 1];
        values = new byte[n + 1];
    }

    private static long hash(long hi, long lo) {
        return HashCommon.mix(hi) ^ Long.rotateLeft(HashCommon.mix(lo), 32);
    }

    private boolean occupied(int slot) { return (keyHi[slot] | keyLo[slot]) != 0L; }

    private int find(long hi, long lo) {
        if ((hi | lo) == 0L) return containsNullKey ? n : -n - 1;
        int slot = (int)hash(hi, lo) & (n - 1);
        while (occupied(slot)) {
            if (keyHi[slot] == hi && keyLo[slot] == lo) return slot;
            slot = (slot + 1) & (n - 1);
        }
        return -slot - 1;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public boolean containsKey(long hi, long lo) { return find(hi, lo) >= 0; }
    public byte defaultReturnValue() { return defaultReturnValue; }
    public void defaultReturnValue(byte value) { defaultReturnValue = value; }

    /** Absence returns the configured default (initially zero); use containsKey to distinguish it. */
    public byte get(long hi, long lo) {
        int slot = find(hi, lo);
        return slot < 0 ? defaultReturnValue : values[slot];
    }

    /** Returns the previous value, or the configured default for a new key. */
    public byte put(long hi, long lo, byte value) {
        int slot = find(hi, lo);
        if (slot >= 0) {
            byte previous = values[slot];
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
        return defaultReturnValue;
    }

    public byte remove(long hi, long lo) {
        int slot = find(hi, lo);
        if (slot < 0) return defaultReturnValue;
        byte previous = values[slot];
        if (slot == n) containsNullKey = false;
        else shiftKeys(slot);
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
        byte[] data = new byte[capacity + 1];
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
        containsNullKey = false;
        size = 0;
        ++version;
    }

    /** One cursor allocation per traversal; structural changes fail fast. */
    public Cursor cursor() { return new Cursor(); }

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
        public byte value() { return values[slot()]; }
    }
}
