package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.NoSuchElementException;

/** Insertion-ordered 4x4x4 spatial groups with full-width horizontal identity. */
public final class Int1088SpatialSet {
    private Int1088[] x, z;
    private int[] y, prev, next;
    private long[] bits;
    private int n, size, first = -1, last = -1;
    private final int minSize;
    private final float loadFactor;
    public Int1088SpatialSet() { this(16, 0.75F); }
    public Int1088SpatialSet(int expected) { this(expected, 0.75F); }
    public Int1088SpatialSet(int expected, float loadFactor) {
        if (expected < 0) throw new IllegalArgumentException("Negative expected size: " + expected);
        if (!(loadFactor > 0 && loadFactor < 1)) throw new IllegalArgumentException("Invalid load factor");
        this.loadFactor = loadFactor;
        n = minSize = HashCommon.arraySize(expected / 64, loadFactor);
        allocate(n);
    }
    private void allocate(int capacity) {
        x = new Int1088[capacity]; z = new Int1088[capacity]; y = new int[capacity];
        prev = new int[capacity]; next = new int[capacity]; bits = new long[capacity];
    }
    private static long hash(long hx, int yy, long hz) {
        return hx ^ Long.rotateLeft(hz, 21) ^ HashCommon.mix((long)yy);
    }
    private static boolean matches(Int1088 value, long[] words) {
        for (int i = 0; i < Int1088.LIMBS; i++) if (value.limb(i) != words[i]) return false;
        return true;
    }
    private int find(long[] xx, int yy, long[] zz) {
        int mask = n - 1;
        int slot = (int)hash(Int1088Math.hash(xx, 0, 17), yy, Int1088Math.hash(zz, 0, 17)) & mask;
        while (bits[slot] != 0) {
            if (y[slot] == yy && matches(x[slot], xx) && matches(z[slot], zz)) return slot;
            slot = (slot + 1) & mask;
        }
        return slot;
    }
    private static long bit(long xx, int yy, long zz) {
        return 1L << ((int)(xx & 3) << 4 | (int)(zz & 3) << 2 | yy & 3);
    }
    public boolean add(Int1088 xx, int yy, Int1088 zz) {
        try (Int1088Scratch xs = Int1088Scratch.acquire(); Int1088Scratch zs = Int1088Scratch.acquire()) {
            xs.copyFrom(xx); zs.copyFrom(zz);
            return change(xs.limbs(), yy, zs.limbs(), true);
        }
    }
    public boolean rem(Int1088 xx, int yy, Int1088 zz) {
        try (Int1088Scratch xs = Int1088Scratch.acquire(); Int1088Scratch zs = Int1088Scratch.acquire()) {
            xs.copyFrom(xx); zs.copyFrom(zz);
            return change(xs.limbs(), yy, zs.limbs(), false);
        }
    }
    public boolean add(long[] xx, int xo, int xl, int yy, long[] zz, int zo, int zl) {
        return changeViews(xx, xo, xl, yy, zz, zo, zl, true);
    }
    public boolean rem(long[] xx, int xo, int xl, int yy, long[] zz, int zo, int zl) {
        return changeViews(xx, xo, xl, yy, zz, zo, zl, false);
    }
    private boolean changeViews(long[] xx, int xo, int xl, int yy, long[] zz, int zo, int zl, boolean add) {
        if (xl < 1 || xl > 17 || zl < 1 || zl > 17) throw new IllegalArgumentException("Invalid limb count");
        try (Int1088Scratch xs = Int1088Scratch.acquire(); Int1088Scratch zs = Int1088Scratch.acquire()) {
            xs.copyFrom(xx, xo, xl); zs.copyFrom(zz, zo, zl);
            return change(xs.limbs(), yy, zs.limbs(), add);
        }
    }
    private boolean change(long[] xx, int yy, long[] zz, boolean add) {
        long mask = bit(xx[0], yy, zz[0]);
        xx[0] &= ~3L; zz[0] &= ~3L; yy &= ~3;
        int slot = find(xx, yy, zz);
        if (!add) {
            if ((bits[slot] & mask) == 0) return false;
            bits[slot] &= ~mask;
            if (bits[slot] == 0) removeGroup(slot);
            return true;
        }
        if (bits[slot] != 0) {
            boolean present = (bits[slot] & mask) != 0;
            bits[slot] |= mask;
            return present;
        }
        if (size + 1 >= HashCommon.maxFill(n, loadFactor)) {
            rehash(HashCommon.arraySize(size + 2, loadFactor)); slot = find(xx, yy, zz);
        }
        x[slot] = Int1088.fromLimbs(xx, 0, 17); z[slot] = Int1088.fromLimbs(zz, 0, 17);
        y[slot] = yy; bits[slot] = mask; prev[slot] = last; next[slot] = -1;
        if (last == -1) first = slot; else next[last] = slot;
        last = slot; size++;
        return false;
    }
    public boolean isEmpty() { return size == 0; }
    public static final class Position {
        public final long[] x = new long[17], z = new long[17];
        public int xLength, y, zLength;
    }
    public void removeFirstLong(Position out) {
        if (isEmpty()) throw new NoSuchElementException();
        java.util.Objects.requireNonNull(out, "out");
        int slot = first, index = Long.numberOfTrailingZeros(bits[slot]);
        x[slot].copyFixed(out.x, 0); z[slot].copyFixed(out.z, 0);
        out.x[0] |= index >>> 4 & 3; out.z[0] |= index >>> 2 & 3;
        out.xLength = Int1088Math.canonicalLength(out.x, 0, 17);
        out.zLength = Int1088Math.canonicalLength(out.z, 0, 17);
        out.y = y[slot] | (index & 3);
        bits[slot] &= ~(1L << index);
        if (bits[slot] == 0) removeGroup(slot);
    }
    private void removeGroup(int slot) {
        int p = prev[slot], q = next[slot];
        if (p == -1) first = q; else next[p] = q;
        if (q == -1) last = p; else prev[q] = p;
        shiftKeys(slot); size--;
        if (n > minSize && size < HashCommon.maxFill(n, loadFactor) / 4) rehash(Math.max(minSize, n / 2));
    }
    private void shiftKeys(int hole) {
        int mask = n - 1;
        for (;;) {
            int slot = (hole + 1) & mask;
            while (bits[slot] != 0) {
                int home = (int)hash(x[slot].hash(), y[slot], z[slot].hash()) & mask;
                if (hole <= slot ? hole >= home || home > slot : hole >= home && home > slot) break;
                slot = (slot + 1) & mask;
            }
            if (bits[slot] == 0) { bits[hole] = 0; x[hole] = z[hole] = null; return; }
            x[hole] = x[slot]; z[hole] = z[slot]; y[hole] = y[slot]; bits[hole] = bits[slot];
            int p = prev[slot], q = next[slot];
            if (p == -1) first = hole; else next[p] = hole;
            if (q == -1) last = hole; else prev[q] = hole;
            prev[hole] = p; next[hole] = q; hole = slot;
        }
    }
    private void rehash(int capacity) {
        Int1088[] oldX = x, oldZ = z; int[] oldY = y, oldNext = next; long[] oldBits = bits;
        int oldFirst = first;
        allocate(capacity); n = capacity; first = last = -1;
        for (int old = oldFirst; old != -1; old = oldNext[old]) {
            int slot = (int)hash(oldX[old].hash(), oldY[old], oldZ[old].hash()) & (capacity - 1);
            while (bits[slot] != 0) slot = (slot + 1) & (capacity - 1);
            x[slot] = oldX[old]; z[slot] = oldZ[old]; y[slot] = oldY[old]; bits[slot] = oldBits[old];
            prev[slot] = last; next[slot] = -1;
            if (last == -1) first = slot; else next[last] = slot;
            last = slot;
        }
    }
}
