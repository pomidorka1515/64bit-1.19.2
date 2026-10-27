package net.minecraft.core;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    private Int1088 originX;
    private int originY;
    private Int1088 originZ;
    private Int1088 endX;
    private int endY;
    private Int1088 endZ;
    private final long[] x = new long[Int1088.LIMBS];
    private int y;
    private final long[] z = new long[Int1088.LIMBS];
    private final long[] spanScratch = new long[Int1088.LIMBS];
    private boolean started;
    private boolean done;
    private long spanX, spanZ;
    private long offsetX, offsetZ;
    private boolean narrow;

    public Cursor3D(long pOriginX, int pOriginY, long pOriginZ, long pEndX, int pEndY, long pEndZ) {
        this(Int1088.fromLong(pOriginX), pOriginY, Int1088.fromLong(pOriginZ),
            Int1088.fromLong(pEndX), pEndY, Int1088.fromLong(pEndZ));
    }

    public Cursor3D(Int1088 pOriginX, int pOriginY, Int1088 pOriginZ, Int1088 pEndX, int pEndY, Int1088 pEndZ) {
        this.reset(pOriginX, pOriginY, pOriginZ, pEndX, pEndY, pEndZ);
    }

    public void reset(Int1088 pOriginX, int pOriginY, Int1088 pOriginZ, Int1088 pEndX, int pEndY, Int1088 pEndZ) {
        this.originX = pOriginX;
        pOriginX.copyFixed(this.x, 0);
        this.originY = this.y = pOriginY;
        this.originZ = pOriginZ;
        pOriginZ.copyFixed(this.z, 0);
        this.endX = pEndX;
        this.endY = pEndY;
        this.endZ = pEndZ;
        this.narrow = pOriginX.fitsLong() && pEndX.fitsLong() && pOriginZ.fitsLong() && pEndZ.fitsLong();
        this.spanX = localSpan(this.x, pEndX);
        this.spanZ = localSpan(this.z, pEndZ);
        this.offsetX = this.offsetZ = 0;
        this.started = false;
        this.done = pEndX.compareTo(pOriginX) < 0 || pEndY < pOriginY || pEndZ.compareTo(pOriginZ) < 0;
    }

    public boolean advance() {
        return this.narrow ? this.advanceNarrow() : this.advanceWide();
    }

    private boolean advanceWide() {
        if (this.done) {
            return false;
        }
        if (!this.started) {
            this.started = true;
        } else if (!atEndX()) {
            increment(this.x);
            if (this.spanX >= 0) ++this.offsetX;
        } else if (this.y < this.endY) {
            this.originX.copyFixed(this.x, 0);
            this.offsetX = 0;
            ++this.y;
        } else if (!atEndZ()) {
            this.originX.copyFixed(this.x, 0);
            this.offsetX = 0;
            this.y = this.originY;
            increment(this.z);
            if (this.spanZ >= 0) ++this.offsetZ;
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    private boolean advanceNarrow() {
        if (this.done) return false;
        if (!this.started) {
            this.started = true;
        } else if (this.x[0] != this.endX.lowLimb()) {
            ++this.x[0];
        } else if (this.y < this.endY) {
            this.x[0] = this.originX.lowLimb();
            this.offsetX = 0;
            ++this.y;
        } else if (this.z[0] != this.endZ.lowLimb()) {
            this.x[0] = this.originX.lowLimb();
            this.offsetX = 0;
            this.y = this.originY;
            ++this.z[0];
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    public Int1088 nextX() { return this.narrow ? Int1088.fromLong(this.x[0]) : Int1088.fromFixedLimbs(this.x); }
    public long nextXLimb(int index) {
        java.util.Objects.checkIndex(index, Int1088.LIMBS);
        return this.narrow && index > 0 ? this.x[0] >> 63 : this.x[index];
    }
    public int nextY() { return this.y; }
    public Int1088 nextZ() { return this.narrow ? Int1088.fromLong(this.z[0]) : Int1088.fromFixedLimbs(this.z); }
    public long nextZLimb(int index) {
        java.util.Objects.checkIndex(index, Int1088.LIMBS);
        return this.narrow && index > 0 ? this.z[0] >> 63 : this.z[index];
    }

    /** Copies the full address into a reusable block iterator cursor. */
    void copyPosition(long[] x, long[] z) {
        if (this.narrow) {
            java.util.Arrays.fill(x, this.x[0] >> 63);
            java.util.Arrays.fill(z, this.z[0] >> 63);
            x[0] = this.x[0];
            z[0] = this.z[0];
        } else {
            System.arraycopy(this.x, 0, x, 0, Int1088.LIMBS);
            System.arraycopy(this.z, 0, z, 0, Int1088.LIMBS);
        }
    }

    // A negative sentinel selects endpoint-checked limb carry for spans larger
    // than a signed long. Bounded offsets never overflow, even at Long.MAX_VALUE.
    private long localSpan(long[] origin, Int1088 end) {
        end.copyFixed(this.spanScratch, 0);
        int length = Int1088Math.subtractSaturated(this.spanScratch, 0, Int1088.LIMBS,
                origin, 0, Int1088.LIMBS, this.spanScratch, 0);
        return length == 1 && this.spanScratch[0] >= 0 ? this.spanScratch[0] : -1;
    }

    private boolean atEndX() { return this.spanX >= 0 ? this.offsetX == this.spanX : equals(this.x, this.endX); }
    private boolean atEndZ() { return this.spanZ >= 0 ? this.offsetZ == this.spanZ : equals(this.z, this.endZ); }

    private static boolean equals(long[] words, Int1088 value) {
        for (int i = 0; i < Int1088.LIMBS; ++i) if (words[i] != value.limb(i)) return false;
        return true;
    }

    private static void increment(long[] words) {
        for (int i = 0; i < words.length; ++i) if (++words[i] != 0) break;
    }

    public int getNextType() {
        int type = 0;
        if (this.narrow) {
            if (this.x[0] == this.originX.lowLimb() || this.x[0] == this.endX.lowLimb()) ++type;
            if (this.y == this.originY || this.y == this.endY) ++type;
            if (this.z[0] == this.originZ.lowLimb() || this.z[0] == this.endZ.lowLimb()) ++type;
            return type;
        }
        if ((this.spanX >= 0 ? this.offsetX == 0 : equals(this.x, this.originX)) || atEndX()) ++type;
        if (this.y == this.originY || this.y == this.endY) ++type;
        if ((this.spanZ >= 0 ? this.offsetZ == 0 : equals(this.z, this.originZ)) || atEndZ()) ++type;
        return type;
    }
}
