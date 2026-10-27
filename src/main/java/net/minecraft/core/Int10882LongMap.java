package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Open-addressed full-width keys and primitive long values. */
public final class Int10882LongMap {
    private Int1088[] keys;
    private long[] values;
    private int size, version;
    private long defaultReturnValue;
    public Int10882LongMap() { this(16); }
    public Int10882LongMap(int expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        int capacity = HashCommon.arraySize(expected, 0.75F);
        keys = new Int1088[capacity]; values = new long[capacity];
    }
    private int find(Int1088 key) {
        Objects.requireNonNull(key, "key");
        int mask = keys.length - 1, slot = (int)key.hash() & mask;
        while (keys[slot] != null) {
            if (keys[slot].equals(key)) return slot;
            slot = (slot + 1) & mask;
        }
        return -slot - 1;
    }
    private int find(long key) {
        int mask = keys.length - 1, slot = (int)Int1088Map.narrowHash(key) & mask;
        while (keys[slot] != null) {
            if (keys[slot].fitsLong() && keys[slot].lowLimb() == key) return slot;
            slot = (slot + 1) & mask;
        }
        return -slot - 1;
    }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public long defaultReturnValue() { return defaultReturnValue; }
    public void defaultReturnValue(long value) { defaultReturnValue = value; }
    public boolean containsKey(Int1088 key) { return find(key) >= 0; }
    public boolean containsKey(long key) { return find(key) >= 0; }
    public long get(Int1088 key) { int slot = find(key); return slot < 0 ? defaultReturnValue : values[slot]; }
    public long get(long key) { int slot = find(key); return slot < 0 ? defaultReturnValue : values[slot]; }
    public long put(long key, long value) {
        int slot = find(key);
        if (slot >= 0) { long old = values[slot]; values[slot] = value; return old; }
        return put(Int1088.fromLong(key), value);
    }
    public long put(Int1088 key, long value) {
        int slot = find(key);
        if (slot >= 0) { long old = values[slot]; values[slot] = value; return old; }
        if (size + 1 >= HashCommon.maxFill(keys.length, 0.75F)) {
            rehash(HashCommon.arraySize(size + 2, 0.75F)); slot = find(key);
        }
        slot = -slot - 1; keys[slot] = key; values[slot] = value; size++; version++;
        return defaultReturnValue;
    }
    public long remove(Int1088 key) { return removeSlot(find(key)); }
    public long remove(long key) { return removeSlot(find(key)); }
    private long removeSlot(int hole) {
        if (hole < 0) return defaultReturnValue;
        long old = values[hole]; int mask = keys.length - 1;
        for (;;) {
            int slot = (hole + 1) & mask;
            while (keys[slot] != null) {
                int home = (int)keys[slot].hash() & mask;
                if (hole <= slot ? hole >= home || home > slot : hole >= home && home > slot) break;
                slot = (slot + 1) & mask;
            }
            if (keys[slot] == null) { keys[hole] = null; break; }
            keys[hole] = keys[slot]; values[hole] = values[slot]; hole = slot;
        }
        size--; version++; return old;
    }
    private void rehash(int capacity) {
        Int1088[] newKeys = new Int1088[capacity]; long[] newValues = new long[capacity];
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) continue;
            int slot = (int)keys[i].hash() & (capacity - 1);
            while (newKeys[slot] != null) slot = (slot + 1) & (capacity - 1);
            newKeys[slot] = keys[i]; newValues[slot] = values[i];
        }
        keys = newKeys; values = newValues;
    }
    public void clear() { if (size != 0) { Arrays.fill(keys, null); size = 0; version++; } }
    public Cursor cursor() { return new Cursor(); }
    public final class Cursor {
        private final int expectedVersion = version;
        private int next, current = -1;
        private void check() { if (version != expectedVersion) throw new ConcurrentModificationException(); }
        public boolean advance() {
            check(); while (next < keys.length && keys[next] == null) next++;
            current = next < keys.length ? next++ : -1; return current >= 0;
        }
        private int slot() { check(); if (current < 0) throw new NoSuchElementException(); return current; }
        public Int1088 key() { return keys[slot()]; }
        public long limb(int index) { return keys[slot()].limb(index); }
        public void copyKey(long[] dst, int offset) { keys[slot()].copyFixed(dst, offset); }
        public long value() { return values[slot()]; }
    }
}
