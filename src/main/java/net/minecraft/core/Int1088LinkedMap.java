package net.minecraft.core;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** Full-width map with insertion order and fail-fast traversal. */
public final class Int1088LinkedMap<V> {
    private final Int1088Map<Node<V>> map;
    private Node<V> first, last;
    private int version;
    private static final class Node<V> {
        final Int1088 key;
        V value;
        Node<V> prev, next;
        Node(Int1088 key, V value) { this.key = key; this.value = value; }
    }
    public Int1088LinkedMap() { this(16); }
    public Int1088LinkedMap(int expected) { map = new Int1088Map<>(expected); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public boolean containsKey(Int1088 key) { return map.containsKey(key); }
    public boolean containsKey(long key) { return map.containsKey(key); }
    public V get(Int1088 key) { Node<V> n = map.get(key); return n == null ? null : n.value; }
    public V get(long key) { Node<V> n = map.get(key); return n == null ? null : n.value; }
    public V getAndMoveToFirst(Int1088 key) { return getFirst(map.get(key)); }
    public V getAndMoveToFirst(long key) { return getFirst(map.get(key)); }
    private V getFirst(Node<V> node) { if (node == null) return null; moveFirst(node); return node.value; }
    public V put(Int1088 key, V value) { return put(key, value, false); }
    public V put(long key, V value) { return putNarrow(key, value, false); }
    public V putAndMoveToFirst(Int1088 key, V value) { return put(key, value, true); }
    public V putAndMoveToFirst(long key, V value) { return putNarrow(key, value, true); }
    private V putNarrow(long key, V value, boolean front) {
        Node<V> node = map.get(key);
        return node == null ? put(Int1088.fromLong(key), value, front) : update(node, value, front);
    }
    private V put(Int1088 key, V value, boolean front) {
        Node<V> node = map.get(key);
        if (node != null) return update(node, value, front);
        node = new Node<>(key, value);
        map.put(key, node);
        if (first == null) first = last = node;
        else if (front) { node.next = first; first.prev = node; first = node; }
        else { node.prev = last; last.next = node; last = node; }
        version++;
        return null;
    }
    private V update(Node<V> node, V value, boolean front) {
        V old = node.value; node.value = value; if (front) moveFirst(node); return old;
    }
    private void unlink(Node<V> node) {
        if (node.prev == null) first = node.next; else node.prev.next = node.next;
        if (node.next == null) last = node.prev; else node.next.prev = node.prev;
    }
    private void moveFirst(Node<V> node) {
        if (node == first) return;
        unlink(node); node.prev = null; node.next = first; first.prev = node; first = node; version++;
    }
    public V remove(Int1088 key) { return removeNode(map.get(key)); }
    public V remove(long key) { return removeNode(map.get(key)); }
    public V removeFirst() { if (first == null) throw new NoSuchElementException(); return removeNode(first); }
    public V removeLast() { if (last == null) throw new NoSuchElementException(); return removeNode(last); }
    private V removeNode(Node<V> node) {
        if (node == null) return null;
        map.remove(node.key); unlink(node); version++; return node.value;
    }
    public void clear() {
        if (isEmpty()) return;
        map.clear(); first = last = null; version++;
    }
    public Cursor cursor() { return new Cursor(); }
    public final class Cursor {
        private final int expectedVersion = version;
        private Node<V> upcoming = first, current;
        private void check() { if (version != expectedVersion) throw new ConcurrentModificationException(); }
        public boolean advance() {
            check(); current = upcoming; if (current == null) return false;
            upcoming = current.next; return true;
        }
        private Node<V> node() { check(); if (current == null) throw new NoSuchElementException(); return current; }
        public Int1088 key() { return node().key; }
        public long limb(int index) { return node().key.limb(index); }
        public void copyKey(long[] dst, int offset) { node().key.copyFixed(dst, offset); }
        public V value() { return node().value; }
    }
}
