package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.NoSuchElementException;

/**
 * Lighting queue with full-width X/Z and integer Y. Outer 4x4x4 groups retain insertion
 * order; removal takes their lowest set bit, ordered Y, then Z, then X.
 */
public final class Int128SpatialSet {
    private long[] xHi, xLo, zHi, zLo, bits;
    private int[] y, prev, next;
    private int n, size, first = -1, last = -1, lastPos = -1;
    private final int minSize;
    private final float loadFactor;

    public Int128SpatialSet() { this(16, 0.75F); }
    public Int128SpatialSet(int expected) { this(expected, 0.75F); }
    public Int128SpatialSet(int expected, float loadFactor) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        if (!(loadFactor > 0 && loadFactor < 1)) throw new IllegalArgumentException("Invalid load factor");
        this.loadFactor = loadFactor;
        n = minSize = HashCommon.arraySize(expected / 64, loadFactor);
        allocate(n);
    }

    private void allocate(int capacity) {
        xHi = new long[capacity + 1]; xLo = new long[capacity + 1];
        zHi = new long[capacity + 1]; zLo = new long[capacity + 1];
        bits = new long[capacity + 1]; y = new int[capacity + 1];
        prev = new int[capacity + 1]; next = new int[capacity + 1];
    }
    private static long hash(long xh, long xl, int y, long zh, long zl) {
        long x = HashCommon.mix(xh) ^ Long.rotateLeft(HashCommon.mix(xl), 32);
        long z = HashCommon.mix(zh) ^ Long.rotateLeft(HashCommon.mix(zl), 32);
        return x ^ Long.rotateLeft(z, 21) ^ HashCommon.mix((long)y);
    }
    private boolean matches(int slot, long xh, long xl, int yy, long zh, long zl) {
        return xHi[slot] == xh && xLo[slot] == xl && y[slot] == yy
            && zHi[slot] == zh && zLo[slot] == zl;
    }
    private int find(long xh, long xl, int yy, long zh, long zl) {
        if ((xh | xl | yy | zh | zl) == 0) return n;
        if (lastPos != -1 && matches(lastPos, xh, xl, yy, zh, zl)) return lastPos;
        int slot = (int)hash(xh, xl, yy, zh, zl) & (n - 1);
        while (bits[slot] != 0) {
            if (matches(slot, xh, xl, yy, zh, zl)) {
                lastPos = slot;
                return slot;
            }
            slot = (slot + 1) & (n - 1);
        }
        return slot;
    }
    private static long bit(long xl, int y, long zl) {
        return 1L << ((int)(xl & 3) << 4 | (int)(zl & 3) << 2 | y & 3);
    }

    /** Returns true when the coordinate was already present, matching SpatialLongSet. */
    public boolean add(long xh, long xl, int yy, long zh, long zl) {
        long bit = bit(xl, yy, zl);
        xl &= ~3L; yy &= ~3; zl &= ~3L;
        int slot = find(xh, xl, yy, zh, zl);
        if (bits[slot] != 0) {
            boolean present = (bits[slot] & bit) != 0;
            bits[slot] |= bit;
            return present;
        }
        if (size + 1 >= HashCommon.maxFill(n, loadFactor)) {
            rehash(HashCommon.arraySize(size + 2, loadFactor));
            slot = find(xh, xl, yy, zh, zl);
        }
        xHi[slot] = xh; xLo[slot] = xl; y[slot] = yy;
        zHi[slot] = zh; zLo[slot] = zl; bits[slot] = bit;
        prev[slot] = last; next[slot] = -1;
        if (last == -1) first = slot; else next[last] = slot;
        last = slot;
        ++size;
        return false;
    }

    public boolean rem(long xh, long xl, int yy, long zh, long zl) {
        long bit = bit(xl, yy, zl);
        int slot = find(xh, xl & ~3L, yy & ~3, zh, zl & ~3L);
        if ((bits[slot] & bit) == 0) return false;
        bits[slot] &= ~bit;
        if (bits[slot] == 0) removeGroup(slot);
        return true;
    }
    public boolean isEmpty() { return size == 0; }

    /** Reusable primitive output; no coordinate object is allocated by queue operations. */
    public static final class Position {
        public long xHi, xLo, zHi, zLo;
        public int y;
    }

    /** Writes the first coordinate to out; throws when empty. No packed long is returned. */
    public void removeFirstLong(Position out) {
        if (isEmpty()) throw new NoSuchElementException();
        int slot = first, index = Long.numberOfTrailingZeros(bits[slot]);
        out.xHi = xHi[slot]; out.xLo = xLo[slot] | (index >>> 4 & 3);
        out.zHi = zHi[slot]; out.zLo = zLo[slot] | (index >>> 2 & 3);
        out.y = y[slot] | (index & 3);
        bits[slot] &= ~(1L << index);
        if (bits[slot] == 0) removeGroup(slot);
    }

    private void removeGroup(int slot) {
        lastPos = -1;
        int p = prev[slot], q = next[slot];
        if (p == -1) first = q; else next[p] = q;
        if (q == -1) last = p; else prev[q] = p;
        if (slot != n) shiftKeys(slot);
        --size;
        if (n > minSize && size < HashCommon.maxFill(n, loadFactor) / 4) {
            rehash(Math.max(minSize, n / 2));
        }
    }
    private void shiftKeys(int hole) {
        for (;;) {
            int slot = (hole + 1) & (n - 1);
            while (bits[slot] != 0) {
                int home = (int)hash(xHi[slot], xLo[slot], y[slot], zHi[slot], zLo[slot]) & (n - 1);
                if (hole <= slot ? hole >= home || home > slot : hole >= home && home > slot) break;
                slot = (slot + 1) & (n - 1);
            }
            if (bits[slot] == 0) { bits[hole] = 0; return; }
            xHi[hole] = xHi[slot]; xLo[hole] = xLo[slot]; y[hole] = y[slot];
            zHi[hole] = zHi[slot]; zLo[hole] = zLo[slot]; bits[hole] = bits[slot];
            int p = prev[slot], q = next[slot];
            if (p == -1) first = hole; else next[p] = hole;
            if (q == -1) last = hole; else prev[q] = hole;
            prev[hole] = p; next[hole] = q;
            hole = slot;
        }
    }
    private void rehash(int capacity) {
        long[] oldXh = xHi, oldXl = xLo, oldZh = zHi, oldZl = zLo, oldBits = bits;
        int[] oldY = y, oldNext = next;
        int oldFirst = first;
        allocate(capacity);
        n = capacity;
        first = last = lastPos = -1;
        for (int old = oldFirst; old != -1; old = oldNext[old]) {
            int slot = find(oldXh[old], oldXl[old], oldY[old], oldZh[old], oldZl[old]);
            xHi[slot] = oldXh[old]; xLo[slot] = oldXl[old]; y[slot] = oldY[old];
            zHi[slot] = oldZh[old]; zLo[slot] = oldZl[old]; bits[slot] = oldBits[old];
            prev[slot] = last; next[slot] = -1;
            if (last == -1) first = slot; else next[last] = slot;
            last = slot;
        }
    }
}
