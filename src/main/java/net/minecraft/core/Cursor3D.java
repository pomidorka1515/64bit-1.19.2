package net.minecraft.core;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    private final long originX;
    private final int originY;
    private final long originZ;
    private final long endX;
    private final int endY;
    private final long endZ;
    private long x;
    private int y;
    private long z;
    private boolean started;
    private boolean done;

    public Cursor3D(long pOriginX, int pOriginY, long pOriginZ, long pEndX, int pEndY, long pEndZ) {
        this.originX = this.x = pOriginX;
        this.originY = this.y = pOriginY;
        this.originZ = this.z = pOriginZ;
        this.endX = pEndX;
        this.endY = pEndY;
        this.endZ = pEndZ;
        this.done = pEndX < pOriginX || pEndY < pOriginY || pEndZ < pOriginZ;
    }

    public boolean advance() {
        if (this.done) {
            return false;
        }
        if (!this.started) {
            this.started = true;
        } else if (this.x < this.endX) {
            ++this.x;
        } else if (this.y < this.endY) {
            this.x = this.originX;
            ++this.y;
        } else if (this.z < this.endZ) {
            this.x = this.originX;
            this.y = this.originY;
            ++this.z;
        } else {
            this.done = true;
            return false;
        }
        return true;
    }

    public long nextX() {
        return this.x;
    }

    public int nextY() {
        return this.y;
    }

    public long nextZ() {
        return this.z;
    }

    public int getNextType() {
        int type = 0;
        if (this.x == this.originX || this.x == this.endX) ++type;
        if (this.y == this.originY || this.y == this.endY) ++type;
        if (this.z == this.originZ || this.z == this.endZ) ++type;
        return type;
    }
}
