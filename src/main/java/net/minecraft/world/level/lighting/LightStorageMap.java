package net.minecraft.world.level.lighting;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Vec3i;

/**
 * Single-writer, bucket-copy-on-write map for published light snapshots. A copy
 * shares immutable buckets, not a chain of earlier snapshots. DataLayer values
 * still use the storage's existing per-section copy-on-write protocol.
 */
final class LightStorageMap<K, V> extends AbstractMap<K, V> {
    private static final int BUCKET_COUNT = 256;
    private final Bucket<K, V>[] buckets;
    private final boolean[] owned = new boolean[BUCKET_COUNT];
    private int size;

    @SuppressWarnings("unchecked")
    LightStorageMap(Map<K, V> initial) {
        this.buckets = new Bucket[BUCKET_COUNT];
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

    private static int hash(Object key) {
        // Vec3i's additive hash has systematic collisions across section grids.
        // Mix each full-width axis separately; never truncate world coordinates.
        int hash;
        if (key instanceof Vec3i pos) {
            hash = pos.getX().hashCode() * 0x9e3779b9;
            hash = Integer.rotateLeft(hash, 13) ^ pos.getZ().hashCode() * 0x85ebca6b;
            hash = Integer.rotateLeft(hash, 11) ^ pos.getY() * 0xc2b2ae35;
        } else {
            hash = Objects.hashCode(key);
        }
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        return hash ^ (hash >>> 15);
    }

    private Bucket<K, V> writable(int index) {
        if (!this.owned[index]) {
            Bucket<K, V> previous = this.buckets[index];
            this.buckets[index] = previous == null ? new Bucket<>() : new Bucket<>(previous);
            this.owned[index] = true;
        }
        return this.buckets[index];
    }

    @Override
    public V get(Object key) {
        return this.getOrDefault(key, null);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        int hash = hash(key);
        Bucket<K, V> bucket = this.buckets[hash & (BUCKET_COUNT - 1)];
        if (bucket == null) return defaultValue;
        int slot = bucket.find(key, hash);
        return slot < 0 ? defaultValue : bucket.value(slot);
    }

    @Override
    public boolean containsKey(Object key) {
        int hash = hash(key);
        Bucket<K, V> bucket = this.buckets[hash & (BUCKET_COUNT - 1)];
        return bucket != null && bucket.find(key, hash) >= 0;
    }

    @Override
    public V put(K key, V value) {
        int hash = hash(key);
        Bucket<K, V> bucket = this.writable(hash & (BUCKET_COUNT - 1));
        int slot = bucket.find(key, hash);
        if (slot >= 0) {
            V previous = bucket.value(slot);
            bucket.values[slot] = value;
            return previous;
        }
        bucket.insert(key, value, hash);
        ++this.size;
        return null;
    }

    @Override
    public V remove(Object key) {
        int hash = hash(key);
        int index = hash & (BUCKET_COUNT - 1);
        Bucket<K, V> bucket = this.buckets[index];
        if (bucket == null) return null;
        int slot = bucket.find(key, hash);
        if (slot < 0) return null;
        bucket = this.writable(index);
        V previous = bucket.value(slot);
        bucket.keys[slot] = Bucket.DELETED;
        bucket.values[slot] = null;
        --bucket.size;
        --this.size;
        if (bucket.size == 0) this.buckets[index] = null;
        if (this.buckets[index] == null) this.owned[index] = false;
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
                            Bucket<K, V> bucket = buckets[this.index++];
                            if (bucket != null) this.entries = bucket.iterator();
                        }
                        return this.entries.hasNext();
                    }

                    public Entry<K, V> next() {
                        if (!this.hasNext()) throw new java.util.NoSuchElementException();
                        return this.entries.next();
                    }
                };
            }
        };
    }

    /** Flat tables keep lookups and snapshot detachment free of per-entry nodes. */
    private static final class Bucket<K, V> {
        private static final Object NULL_KEY = new Object();
        private static final Object DELETED = new Object();
        private Object[] keys;
        private Object[] values;
        private int[] hashes;
        private int size;
        private int used;

        Bucket() {
            this.keys = new Object[8];
            this.values = new Object[8];
            this.hashes = new int[8];
        }

        Bucket(Bucket<K, V> source) {
            this.keys = source.keys.clone();
            this.values = source.values.clone();
            this.hashes = source.hashes.clone();
            this.size = source.size;
            this.used = source.used;
        }

        int find(Object key, int hash) {
            Object masked = key == null ? NULL_KEY : key;
            int mask = this.keys.length - 1;
            // Low bits selected the bucket; use independent bits within it.
            int slot = (hash >>> 8) & mask;
            Object stored;
            while ((stored = this.keys[slot]) != null) {
                if (stored == masked || (stored != DELETED && this.hashes[slot] == hash
                        && masked.equals(stored))) return slot;
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        V value(int slot) { return (V) this.values[slot]; }

        void insert(Object key, Object value, int hash) {
            if ((this.used + 1) * 3 >= this.keys.length * 2) {
                this.rehash((this.size + 1) * 3 >= this.keys.length * 2 ? this.keys.length * 2 : this.keys.length);
            }
            int mask = this.keys.length - 1;
            int slot = (hash >>> 8) & mask;
            while (this.keys[slot] != null && this.keys[slot] != DELETED) slot = (slot + 1) & mask;
            if (this.keys[slot] == null) ++this.used;
            this.keys[slot] = key == null ? NULL_KEY : key;
            this.values[slot] = value;
            this.hashes[slot] = hash;
            ++this.size;
        }

        private void rehash(int capacity) {
            Object[] oldKeys = this.keys;
            Object[] oldValues = this.values;
            int[] oldHashes = this.hashes;
            this.keys = new Object[capacity];
            this.values = new Object[capacity];
            this.hashes = new int[capacity];
            this.size = this.used = 0;
            for (int i = 0; i < oldKeys.length; ++i) {
                if (oldKeys[i] != null && oldKeys[i] != DELETED) this.insert(oldKeys[i], oldValues[i], oldHashes[i]);
            }
        }

        Iterator<Entry<K, V>> iterator() {
            return new Iterator<>() {
                private int slot;
                public boolean hasNext() {
                    while (this.slot < keys.length && (keys[this.slot] == null || keys[this.slot] == DELETED)) ++this.slot;
                    return this.slot < keys.length;
                }
                @SuppressWarnings("unchecked")
                public Entry<K, V> next() {
                    if (!this.hasNext()) throw new java.util.NoSuchElementException();
                    int index = this.slot++;
                    return new SimpleImmutableEntry<>(keys[index] == NULL_KEY ? null : (K) keys[index], value(index));
                }
            };
        }
    }
}
