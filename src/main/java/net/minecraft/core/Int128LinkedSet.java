package net.minecraft.core;

/** Insertion-ordered primitive set backed by the linked map's two-long table. */
public final class Int128LinkedSet {
    private static final Object PRESENT = new Object();
    private final Int128LinkedMap<Object> map;

    public Int128LinkedSet() { this(16); }
    public Int128LinkedSet(int expected) { map = new Int128LinkedMap<>(expected); }

    public boolean add(long hi, long lo) { return map.put(hi, lo, PRESENT) == null; }
    /** Returns true only for a new key, matching fastutil's addAndMoveToFirst. */
    public boolean addAndMoveToFirst(long hi, long lo) {
        return map.putAndMoveToFirst(hi, lo, PRESENT) == null;
    }
    /** Returns whether the key exists, moving it to the front when present. */
    public boolean containsAndMoveToFirst(long hi, long lo) {
        return map.getAndMoveToFirst(hi, lo) != null;
    }
    public boolean contains(long hi, long lo) { return map.containsKey(hi, lo); }
    public boolean remove(long hi, long lo) { return map.remove(hi, lo) != null; }
    /** Discards the first key without allocating; throws when empty. */
    public void removeFirst() { map.removeFirst(); }
    /** Discards the last key without allocating; throws when empty. */
    public void removeLast() { map.removeLast(); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }

    /** One cursor allocation per traversal; primitive key access allocates nothing. */
    public Cursor cursor() { return new Cursor(); }
    public final class Cursor {
        private final Int128LinkedMap<Object>.Cursor cursor = map.cursor();
        public boolean advance() { return cursor.advance(); }
        public long hi() { return cursor.hi(); }
        public long lo() { return cursor.lo(); }
    }
}
