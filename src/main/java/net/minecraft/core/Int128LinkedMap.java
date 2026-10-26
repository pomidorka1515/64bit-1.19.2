package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** Insertion-ordered two-long keys with allocation-free entry traversal. */
public final class Int128LinkedMap<V> {
    private long[] keyHi, keyLo;
    private Object[] values;
    private int[] prev, next;
    private int n, size, version;
    private int first = -1, last = -1;
    private boolean containsNullKey;

    public Int128LinkedMap() { this(16); }
    public Int128LinkedMap(int expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        n = HashCommon.arraySize(expected, 0.75F);
        keyHi = new long[n + 1];
        keyLo = new long[n + 1];
        values = new Object[n + 1];
        prev = new int[n + 1];
        next = new int[n + 1];
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
    @SuppressWarnings("unchecked")
    private V value(int slot) { return (V)values[slot]; }
    /** Null values are supported; containsKey distinguishes absence. */
    public V get(long hi, long lo) {
        int slot = find(hi, lo);
        return slot < 0 ? null : value(slot);
    }
    public V getAndMoveToFirst(long hi, long lo) {
        int slot = find(hi, lo);
        if (slot < 0) return null;
        moveToFirst(slot);
        return value(slot);
    }
    public V put(long hi, long lo, V value) { return put(hi, lo, value, false); }
    public V putAndMoveToFirst(long hi, long lo, V value) { return put(hi, lo, value, true); }
    private V put(long hi, long lo, V value, boolean front) {
        int slot = find(hi, lo);
        if (slot >= 0) {
            V old = value(slot);
            values[slot] = value;
            if (front) moveToFirst(slot);
            return old;
        }
        // Grow before mutation so failed allocation leaves the old table intact.
        if (size + 1 >= HashCommon.maxFill(n, 0.75F)) {
            rehash(HashCommon.arraySize(size + 2, 0.75F));
            slot = find(hi, lo);
        }
        slot = -slot - 1;
        if (slot == n) containsNullKey = true;
        keyHi[slot] = hi;
        keyLo[slot] = lo;
        values[slot] = value;
        if (size == 0) {
            first = last = slot;
            prev[slot] = next[slot] = -1;
        } else if (front) {
            prev[first] = slot;
            next[slot] = first;
            prev[slot] = -1;
            first = slot;
        } else {
            next[last] = slot;
            prev[slot] = last;
            next[slot] = -1;
            last = slot;
        }
        ++size;
        ++version;
        return null;
    }
    private void unlink(int slot) {
        int p = prev[slot], q = next[slot];
        if (p == -1) first = q; else next[p] = q;
        if (q == -1) last = p; else prev[q] = p;
    }
    private void moveToFirst(int slot) {
        if (first == slot) return;
        unlink(slot);
        prev[first] = slot;
        next[slot] = first;
        prev[slot] = -1;
        first = slot;
        ++version;
    }
    public V remove(long hi, long lo) {
        int slot = find(hi, lo);
        return slot < 0 ? null : removeSlot(slot);
    }
    /** Removes the first value; throws when empty, matching fastutil. */
    public V removeFirst() {
        if (size == 0) throw new NoSuchElementException();
        return removeSlot(first);
    }
    /** Removes the last value; throws when empty, matching fastutil. */
    public V removeLast() {
        if (size == 0) throw new NoSuchElementException();
        return removeSlot(last);
    }
    private V removeSlot(int slot) {
        V old = value(slot);
        unlink(slot);
        if (slot == n) {
            containsNullKey = false;
            values[n] = null;
        } else shiftKeys(slot);
        --size;
        ++version;
        return old;
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
                keyHi[hole] = keyLo[hole] = 0L;
                values[hole] = null;
                return;
            }
            keyHi[hole] = keyHi[slot];
            keyLo[hole] = keyLo[slot];
            values[hole] = values[slot];
            // Repair neighbors when a probe-chain entry changes physical index.
            int p = prev[slot], q = next[slot];
            if (p == -1) first = hole; else next[p] = hole;
            if (q == -1) last = hole; else prev[q] = hole;
            prev[hole] = p;
            next[hole] = q;
            hole = slot;
        }
    }
    private void rehash(int capacity) {
        long[] hi = new long[capacity + 1], lo = new long[capacity + 1];
        Object[] data = new Object[capacity + 1];
        int[] before = new int[capacity + 1], after = new int[capacity + 1];
        int head = -1, tail = -1;
        for (int old = first; old != -1; old = next[old]) {
            int slot = capacity;
            if (old != n) {
                slot = (int)hash(keyHi[old], keyLo[old]) & (capacity - 1);
                while ((hi[slot] | lo[slot]) != 0L) slot = (slot + 1) & (capacity - 1);
            }
            hi[slot] = keyHi[old];
            lo[slot] = keyLo[old];
            data[slot] = values[old];
            before[slot] = tail;
            after[slot] = -1;
            if (tail == -1) head = slot; else after[tail] = slot;
            tail = slot;
        }
        keyHi = hi;
        keyLo = lo;
        values = data;
        prev = before;
        next = after;
        first = head;
        last = tail;
        n = capacity;
    }
    public void clear() {
        if (size == 0) return;
        Arrays.fill(keyHi, 0L);
        Arrays.fill(keyLo, 0L);
        Arrays.fill(values, null);
        containsNullKey = false;
        size = 0;
        first = last = -1;
        ++version;
    }
    /** One allocation per traversal; order or structural changes fail fast. */
    public Cursor cursor() { return new Cursor(); }
    public final class Cursor {
        private final int expectedVersion = version;
        private int upcoming = first, current = -1;
        private void check() {
            if (expectedVersion != version) throw new ConcurrentModificationException();
        }
        public boolean advance() {
            check();
            current = upcoming;
            if (current == -1) return false;
            upcoming = next[current];
            return true;
        }
        private int slot() {
            check();
            if (current == -1) throw new NoSuchElementException();
            return current;
        }
        public long hi() { return keyHi[slot()]; }
        public long lo() { return keyLo[slot()]; }
        public V value() { return Int128LinkedMap.this.value(slot()); }
    }
}
