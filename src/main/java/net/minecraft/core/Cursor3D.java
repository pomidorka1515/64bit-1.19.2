package net.minecraft.core;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    private final Int128 originX;
    private final int originY;
    private final Int128 originZ;
    private final Int128 endX;
    private final int endY;
    private final Int128 endZ;
    private Int128 x;
    private int y;
    private Int128 z;
    private boolean started;
    private boolean done;

    public Cursor3D(long pOriginX, int pOriginY, long pOriginZ, long pEndX, int pEndY, long pEndZ) {
        this(Int128.fromLong(pOriginX), pOriginY, Int128.fromLong(pOriginZ),
            Int128.fromLong(pEndX), pEndY, Int128.fromLong(pEndZ));
    }

    public Cursor3D(Int128 pOriginX, int pOriginY, Int128 pOriginZ, Int128 pEndX, int pEndY, Int128 pEndZ) {
        this.originX = this.x = pOriginX;
        this.originY = this.y = pOriginY;
        this.originZ = this.z = pOriginZ;
        this.endX = pEndX;
        this.endY = pEndY;
        this.endZ = pEndZ;
        this.done = pEndX.compareTo(pOriginX) < 0 || pEndY < pOriginY || pEndZ.compareTo(pOriginZ) < 0;
    }

    public boolean advance() {
        if (this.done) {
            return false;
        }
        if (!this.started) {
            this.started = true;
        } else if (this.x.compareTo(this.endX) < 0) {
            this.x = this.x.add(Int128.ONE);
        } else if (this.y < this.endY) {
            this.x = this.originX;
            ++this.y;
        } else if (this.z.compareTo(this.endZ) < 0) {
            this.x = this.originX;
            this.y = this.originY;
            this.z = this.z.add(Int128.ONE);
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    public Int128 nextX() {
        return this.x;
    }

    public long nextXHi() {
        return this.x.hi();
    }

    public long nextXLo() {
        return this.x.lo();
    }

    public int nextY() {
        return this.y;
    }

    public Int128 nextZ() {
        return this.z;
    }

    public long nextZHi() {
        return this.z.hi();
    }

    public long nextZLo() {
        return this.z.lo();
    }

    public int getNextType() {
        int type = 0;
        if (this.x.equals(this.originX) || this.x.equals(this.endX)) ++type;
        if (this.y == this.originY || this.y == this.endY) ++type;
        if (this.z.equals(this.originZ) || this.z.equals(this.endZ)) ++type;
        return type;
    }
}
