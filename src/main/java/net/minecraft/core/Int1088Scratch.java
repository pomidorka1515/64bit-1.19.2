package net.minecraft.core;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Scoped, thread-confined temporary limbs. Four nested borrows are allocation-free
 * after initialization; up to eight buffers are retained per thread. Deeper nesting
 * uses disposable buffers, never an already borrowed buffer.
 * Borrowed arrays must not escape the scope, including across close/reacquire.
 */
public final class Int1088Scratch implements AutoCloseable {
    public static final int CAPACITY = 20;
    private static final int PREALLOCATED = 4;
    private static final int RETAINED_LIMIT = 8;
    private static final ThreadLocal<ArrayDeque<Int1088Scratch>> POOL =
        ThreadLocal.withInitial(Pool::new);

    private final long[] limbs = new long[CAPACITY];
    private final Thread owner;
    private final ArrayDeque<Int1088Scratch> pool;
    private int length;
    private boolean borrowed;

    private Int1088Scratch(ArrayDeque<Int1088Scratch> pool) {
        this.owner = Thread.currentThread();
        this.pool = pool;
    }

    private static final class Pool extends ArrayDeque<Int1088Scratch> {
        private int allocated = PREALLOCATED;

        private Pool() {
            super(RETAINED_LIMIT);
            for (int i = 0; i < PREALLOCATED; ++i) this.push(new Int1088Scratch(this));
        }
    }

    public static Int1088Scratch acquire() {
        Pool pool = (Pool)POOL.get();
        Int1088Scratch scratch = pool.pollFirst();
        if (scratch == null) {
            boolean retain = pool.allocated < RETAINED_LIMIT;
            if (retain) ++pool.allocated;
            scratch = new Int1088Scratch(retain ? pool : null);
        }
        scratch.borrowed = true;
        return scratch;
    }

    private void checkOwner() {
        if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("Scratch belongs to another thread");
        }
    }

    private void checkBorrowed() {
        this.checkOwner();
        if (!this.borrowed) throw new IllegalStateException("Scratch is closed");
    }

    public long[] limbs() {
        this.checkBorrowed();
        return this.limbs;
    }

    public int offset() {
        this.checkBorrowed();
        return 0;
    }

    /** Zero means empty; otherwise the canonical signed length, up to CAPACITY. */
    public int length() {
        this.checkBorrowed();
        return this.length;
    }

    public void setLength(int length) {
        this.checkBorrowed();
        this.length = Int1088Math.canonicalLength(this.limbs, 0, length);
    }

    public void copyFrom(Int1088 value) {
        this.checkBorrowed();
        value.copyFixed(this.limbs, 0);
        Arrays.fill(this.limbs, Int1088.LIMBS, CAPACITY, value.limb(Int1088.LIMBS - 1) >> 63);
        this.length = value.limbCount();
    }

    public void copyFrom(long[] src, int off, int len) {
        this.checkBorrowed();
        int canonical = Int1088Math.canonicalLength(src, off, len);
        long sign = src[off + len - 1] >> 63;
        System.arraycopy(src, off, this.limbs, 0, len);
        Arrays.fill(this.limbs, len, CAPACITY, sign);
        this.length = canonical;
    }

    /** Copies a nonempty, in-domain result; wider intermediates must be reduced first. */
    public Int1088 toValue() {
        this.checkBorrowed();
        return Int1088.fromLimbs(this.limbs, 0, this.length);
    }

    public void clear() {
        this.checkBorrowed();
        Arrays.fill(this.limbs, 0L);
        this.length = 0;
    }

    @Override
    public void close() {
        this.checkOwner();
        if (!this.borrowed) return;
        this.clear();
        this.borrowed = false;
        if (this.pool != null) this.pool.push(this);
    }
}
