package net.minecraft.core;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    private Int128 originX;
    private int originY;
    private Int128 originZ;
    private Int128 endX;
    private int endY;
    private Int128 endZ;
    private long xHi;
    private long xLo;
    private int y;
    private long zHi;
    private long zLo;
    private boolean started;
    private boolean done;

    public Cursor3D(long pOriginX, int pOriginY, long pOriginZ, long pEndX, int pEndY, long pEndZ) {
        this(Int128.fromLong(pOriginX), pOriginY, Int128.fromLong(pOriginZ),
            Int128.fromLong(pEndX), pEndY, Int128.fromLong(pEndZ));
    }

    public Cursor3D(Int128 pOriginX, int pOriginY, Int128 pOriginZ, Int128 pEndX, int pEndY, Int128 pEndZ) {
        this.reset(pOriginX, pOriginY, pOriginZ, pEndX, pEndY, pEndZ);
    }

    public void reset(Int128 pOriginX, int pOriginY, Int128 pOriginZ, Int128 pEndX, int pEndY, Int128 pEndZ) {
        this.originX = pOriginX;
        this.xHi = pOriginX.hi();
        this.xLo = pOriginX.lo();
        this.originY = this.y = pOriginY;
        this.originZ = pOriginZ;
        this.zHi = pOriginZ.hi();
        this.zLo = pOriginZ.lo();
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
        } else if (this.xHi != this.endX.hi() || this.xLo != this.endX.lo()) {
            if (++this.xLo == 0) ++this.xHi;
        } else if (this.y < this.endY) {
            this.xHi = this.originX.hi();
            this.xLo = this.originX.lo();
            ++this.y;
        } else if (this.zHi != this.endZ.hi() || this.zLo != this.endZ.lo()) {
            this.xHi = this.originX.hi();
            this.xLo = this.originX.lo();
            this.y = this.originY;
            if (++this.zLo == 0) ++this.zHi;
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    public Int128 nextX() {
        return Int128.of(this.xHi, this.xLo);
    }

    public long nextXHi() {
        return this.xHi;
    }

    public long nextXLo() {
        return this.xLo;
    }

    public int nextY() {
        return this.y;
    }

    public Int128 nextZ() {
        return Int128.of(this.zHi, this.zLo);
    }

    public long nextZHi() {
        return this.zHi;
    }

    public long nextZLo() {
        return this.zLo;
    }

    public int getNextType() {
        int type = 0;
        if (this.xHi == this.originX.hi() && this.xLo == this.originX.lo()
                || this.xHi == this.endX.hi() && this.xLo == this.endX.lo()) ++type;
        if (this.y == this.originY || this.y == this.endY) ++type;
        if (this.zHi == this.originZ.hi() && this.zLo == this.originZ.lo()
                || this.zHi == this.endZ.hi() && this.zLo == this.endZ.lo()) ++type;
        return type;
    }
}
