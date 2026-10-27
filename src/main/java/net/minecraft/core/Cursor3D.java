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
    private boolean started;
    private boolean done;

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
        this.started = false;
        this.done = pEndX.compareTo(pOriginX) < 0 || pEndY < pOriginY || pEndZ.compareTo(pOriginZ) < 0;
    }

    public boolean advance() {
        if (this.done) {
            return false;
        }
        if (!this.started) {
            this.started = true;
        } else if (!equals(this.x, this.endX)) {
            increment(this.x);
        } else if (this.y < this.endY) {
            this.originX.copyFixed(this.x, 0);
            ++this.y;
        } else if (!equals(this.z, this.endZ)) {
            this.originX.copyFixed(this.x, 0);
            this.y = this.originY;
            increment(this.z);
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    public Int1088 nextX() { return Int1088.fromFixedLimbs(this.x); }
    public long nextXLimb(int index) { return this.x[index]; }
    public int nextY() { return this.y; }
    public Int1088 nextZ() { return Int1088.fromFixedLimbs(this.z); }
    public long nextZLimb(int index) { return this.z[index]; }

    private static boolean equals(long[] words, Int1088 value) {
        for (int i = 0; i < Int1088.LIMBS; ++i) if (words[i] != value.limb(i)) return false;
        return true;
    }

    private static void increment(long[] words) {
        for (int i = 0; i < words.length; ++i) if (++words[i] != 0) break;
    }

    public int getNextType() {
        int type = 0;
        if (equals(this.x, this.originX) || equals(this.x, this.endX)) ++type;
        if (this.y == this.originY || this.y == this.endY) ++type;
        if (equals(this.z, this.originZ) || equals(this.z, this.endZ)) ++type;
        return type;
    }
}
