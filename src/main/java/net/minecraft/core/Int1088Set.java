package net.minecraft.core;

/** Full-width set backed by the object map. */
public final class Int1088Set {
    private static final Object PRESENT = new Object();
    private final Int1088Map<Object> map;
    public Int1088Set() { this(16); }
    public Int1088Set(int expected) { map = new Int1088Map<>(expected); }
    public boolean add(Int1088 key) { return map.put(key, PRESENT) == null; }
    public boolean add(long key) { return map.put(key, PRESENT) == null; }
    public boolean contains(Int1088 key) { return map.containsKey(key); }
    public boolean contains(long key) { return map.containsKey(key); }
    public boolean remove(Int1088 key) { return map.remove(key) != null; }
    public boolean remove(long key) { return map.remove(key) != null; }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }
    public Int1088Map<Object>.Cursor cursor() { return map.cursor(); }
}
