package net.minecraft.world.level.lighting;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Single-writer, bucket-copy-on-write map for published light snapshots. A copy
 * shares immutable buckets, not a chain of earlier snapshots. DataLayer values
 * still use the storage's existing per-section copy-on-write protocol.
 */
final class LightStorageMap<K, V> extends AbstractMap<K, V> {
    private static final int BUCKET_COUNT = 256;
    private final Map<K, V>[] buckets;
    private final boolean[] owned = new boolean[BUCKET_COUNT];
    private int size;

    @SuppressWarnings("unchecked")
    LightStorageMap(Map<K, V> initial) {
        this.buckets = new Map[BUCKET_COUNT];
        this.putAll(initial);
    }

    private LightStorageMap(LightStorageMap<K, V> source) {
        this.buckets = source.buckets.clone();
        this.size = source.size;
        Arrays.fill(source.owned, false);
    }

    LightStorageMap<K, V> copy() {
        return new LightStorageMap<>(this);
    }

    private int bucket(Object key) {
        int hash = key == null ? 0 : key.hashCode();
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        return (hash ^ (hash >>> 15)) & (BUCKET_COUNT - 1);
    }

    private Map<K, V> writable(int index) {
        if (!this.owned[index]) {
            Map<K, V> previous = this.buckets[index];
            this.buckets[index] = previous == null ? new HashMap<>() : new HashMap<>(previous);
            this.owned[index] = true;
        }
        return this.buckets[index];
    }

    @Override
    public V get(Object key) {
        Map<K, V> bucket = this.buckets[this.bucket(key)];
        return bucket == null ? null : bucket.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        Map<K, V> bucket = this.buckets[this.bucket(key)];
        return bucket != null && bucket.containsKey(key);
    }

    @Override
    public V put(K key, V value) {
        Map<K, V> bucket = this.writable(this.bucket(key));
        boolean present = bucket.containsKey(key);
        V previous = bucket.put(key, value);
        if (!present) ++this.size;
        return previous;
    }

    @Override
    public V remove(Object key) {
        int index = this.bucket(key);
        if (this.buckets[index] == null || !this.buckets[index].containsKey(key)) return null;
        V previous = this.writable(index).remove(key);
        --this.size;
        return previous;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public void clear() {
        Arrays.fill(this.buckets, null);
        Arrays.fill(this.owned, false);
        this.size = 0;
    }

    /** Read-only entries: mutations must go through put/remove to detach buckets. */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            public int size() { return LightStorageMap.this.size; }

            public Iterator<Entry<K, V>> iterator() {
                return new Iterator<>() {
                    private int index;
                    private Iterator<Entry<K, V>> entries = Collections.emptyIterator();

                    public boolean hasNext() {
                        while (!this.entries.hasNext() && this.index < BUCKET_COUNT) {
                            Map<K, V> bucket = buckets[this.index++];
                            if (bucket != null) this.entries = bucket.entrySet().iterator();
                        }
                        return this.entries.hasNext();
                    }

                    public Entry<K, V> next() {
                        if (!this.hasNext()) throw new java.util.NoSuchElementException();
                        return new SimpleImmutableEntry<>(this.entries.next());
                    }
                };
            }
        };
    }
}
