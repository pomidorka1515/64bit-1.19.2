package net.minecraft.core;

/** Insertion-ordered full-width set. */
public final class Int1088LinkedSet {
    private static final Object PRESENT = new Object();
    private final Int1088LinkedMap<Object> map;
    public Int1088LinkedSet() { this(16); }
    public Int1088LinkedSet(int expected) { map = new Int1088LinkedMap<>(expected); }
    public boolean add(Int1088 key) { return map.put(key, PRESENT) == null; }
    public boolean add(long key) { return map.put(key, PRESENT) == null; }
    public boolean addAndMoveToFirst(Int1088 key) { return map.putAndMoveToFirst(key, PRESENT) == null; }
    public boolean addAndMoveToFirst(long key) { return map.putAndMoveToFirst(key, PRESENT) == null; }
    public boolean containsAndMoveToFirst(Int1088 key) { return map.getAndMoveToFirst(key) != null; }
    public boolean containsAndMoveToFirst(long key) { return map.getAndMoveToFirst(key) != null; }
    public boolean contains(Int1088 key) { return map.containsKey(key); }
    public boolean contains(long key) { return map.containsKey(key); }
    public boolean remove(Int1088 key) { return map.remove(key) != null; }
    public boolean remove(long key) { return map.remove(key) != null; }
    public void removeFirst() { map.removeFirst(); }
    public void removeLast() { map.removeLast(); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }
    public Int1088LinkedMap<Object>.Cursor cursor() { return map.cursor(); }
}
