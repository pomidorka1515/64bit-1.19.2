package net.minecraft.core;

import net.minecraft.world.level.WorldBounds;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    private final long originX;
    private final int originY;
    private final long originZ;
    private final long width;
    private final int height;
    private final long depth;
    private final long end;
    private long index;
    private long x;
    private int y;
    private long z;

    public Cursor3D(long pOriginX, int pOriginY, long pOriginZ, long pEndX, int pEndY, long pEndZ) {
        this.originX = pOriginX;
        this.originY = pOriginY;
        this.originZ = pOriginZ;
        if (pEndX < pOriginX || pEndY < pOriginY || pEndZ < pOriginZ) {
            this.width = 0L;
            this.height = 0;
            this.depth = 0L;
            this.end = 0L;
            return;
        }

        this.width = Math.addExact(Math.subtractExact(pEndX, pOriginX), 1L);
        this.height = Math.addExact(Math.subtractExact(pEndY, pOriginY), 1);
        this.depth = Math.addExact(Math.subtractExact(pEndZ, pOriginZ), 1L);
        this.end = Math.multiplyExact(Math.multiplyExact(this.width, this.height), this.depth);
    }

    public boolean advance() {
        if (this.index == this.end) {
            return false;
        } else {
            this.x = this.index % this.width;
            long i = this.index / this.width;
            this.y = (int) (i % this.height); // TODO: int casting
            this.z = i / this.height;
            ++this.index;
            return true;
        }
    }

    public long nextX() {
        return WorldBounds.addBlockOffset(this.originX, this.x);
    }

    public int nextY() {
        return Math.addExact(this.originY, this.y);
    }

    public long nextZ() {
        return WorldBounds.addBlockOffset(this.originZ, this.z);
    }

    public int getNextType() {
        int i = 0;
        if (this.x == 0 || this.x == this.width - 1) {
            ++i;
        }

        if (this.y == 0 || this.y == this.height - 1) {
            ++i;
        }

        if (this.z == 0 || this.z == this.depth - 1) {
            ++i;
        }

        return i;
    }
}
