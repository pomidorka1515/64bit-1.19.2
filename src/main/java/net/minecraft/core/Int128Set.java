package net.minecraft.core;

/** Unordered primitive set sharing the map's parallel-key table and backward-shift deletion. */
public final class Int128Set {
    private static final Object PRESENT = new Object();
    private final Int128Map<Object> map;

    public Int128Set() {
        this(16);
    }

    public Int128Set(int expected) {
        map = new Int128Map<>(expected);
    }

    public boolean add(long hi, long lo) {
        return map.put(hi, lo, PRESENT) == null;
    }

    public boolean contains(long hi, long lo) {
        return map.containsKey(hi, lo);
    }

    public boolean remove(long hi, long lo) {
        return map.remove(hi, lo) != null;
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
    }

    /** Allocate once per traversal; advance and key access allocate nothing. */
    public Cursor cursor() {
        return new Cursor();
    }

    public final class Cursor {
        private final Int128Map<Object>.Cursor cursor = map.cursor();

        public boolean advance() { return cursor.advance(); }
        public long hi() { return cursor.hi(); }
        public long lo() { return cursor.lo(); }
    }
}
