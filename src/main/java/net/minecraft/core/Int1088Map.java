package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Open-addressed full-width keys; narrow lookups do not allocate. */
public final class Int1088Map<V> {
    private Int1088[] keys;
    private Object[] values;
    private int size, version;

    public Int1088Map() { this(16); }
    public Int1088Map(int expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        int capacity = HashCommon.arraySize(expected, 0.75F);
        keys = new Int1088[capacity];
        values = new Object[capacity];
    }

    static long narrowHash(long value) {
        long h = 0xcbf29ce484222325L, sign = value >> 63;
        for (int i = 0; i < Int1088.LIMBS; i++) {
            long word = i == 0 ? value : sign;
            h = (h ^ HashCommon.murmurHash3(word + 0x9e3779b97f4a7c15L * (i + 1))) * 0x100000001b3L;
        }
        return HashCommon.murmurHash3(h);
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
        int mask = keys.length - 1, slot = (int)narrowHash(key) & mask;
        while (keys[slot] != null) {
            if (keys[slot].fitsLong() && keys[slot].lowLimb() == key) return slot;
            slot = (slot + 1) & mask;
        }
        return -slot - 1;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public boolean containsKey(Int1088 key) { return find(key) >= 0; }
    public boolean containsKey(long key) { return find(key) >= 0; }
    @SuppressWarnings("unchecked")
    private V value(int slot) { return (V)values[slot]; }
    public V get(Int1088 key) { int slot = find(key); return slot < 0 ? null : value(slot); }
    public V get(long key) { int slot = find(key); return slot < 0 ? null : value(slot); }
    public V put(long key, V value) {
        int slot = find(key);
        if (slot >= 0) { V old = value(slot); values[slot] = value; return old; }
        return put(Int1088.fromLong(key), value);
    }
    public V put(Int1088 key, V value) {
        int slot = find(key);
        if (slot >= 0) { V old = value(slot); values[slot] = value; return old; }
        if (size + 1 >= HashCommon.maxFill(keys.length, 0.75F)) {
            rehash(HashCommon.arraySize(size + 2, 0.75F));
            slot = find(key);
        }
        slot = -slot - 1;
        keys[slot] = key;
        values[slot] = value;
        size++;
        version++;
        return null;
    }
    public V remove(Int1088 key) { return removeSlot(find(key)); }
    public V remove(long key) { return removeSlot(find(key)); }
    private V removeSlot(int hole) {
        if (hole < 0) return null;
        V old = value(hole);
        int mask = keys.length - 1;
        for (;;) {
            int slot = (hole + 1) & mask;
            while (keys[slot] != null) {
                int home = (int)keys[slot].hash() & mask;
                if (hole <= slot ? hole >= home || home > slot : hole >= home && home > slot) break;
                slot = (slot + 1) & mask;
            }
            if (keys[slot] == null) { keys[hole] = null; values[hole] = null; break; }
            keys[hole] = keys[slot];
            values[hole] = values[slot];
            hole = slot;
        }
        size--;
        version++;
        return old;
    }
    private void rehash(int capacity) {
        Int1088[] nextKeys = new Int1088[capacity];
        Object[] nextValues = new Object[capacity];
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) continue;
            int slot = (int)keys[i].hash() & (capacity - 1);
            while (nextKeys[slot] != null) slot = (slot + 1) & (capacity - 1);
            nextKeys[slot] = keys[i];
            nextValues[slot] = values[i];
        }
        keys = nextKeys;
        values = nextValues;
    }
    public void clear() {
        if (size == 0) return;
        Arrays.fill(keys, null);
        Arrays.fill(values, null);
        size = 0;
        version++;
    }
    public Cursor cursor() { return new Cursor(); }
    public final class Cursor {
        private final int expectedVersion = version;
        private int next, current = -1;
        private void check() { if (version != expectedVersion) throw new ConcurrentModificationException(); }
        public boolean advance() {
            check();
            while (next < keys.length && keys[next] == null) next++;
            current = next < keys.length ? next++ : -1;
            return current >= 0;
        }
        private int slot() { check(); if (current < 0) throw new NoSuchElementException(); return current; }
        public Int1088 key() { return keys[slot()]; }
        public long limb(int index) { return keys[slot()].limb(index); }
        public void copyKey(long[] destination, int offset) { keys[slot()].copyFixed(destination, offset); }
        public V value() { return Int1088Map.this.value(slot()); }
    }
}
