package net.minecraft.world.phys.shapes;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class SubShape extends DiscreteVoxelShape {
    private final DiscreteVoxelShape parent;
    private final long startX;
    private final int startY;
    private final long startZ;
    private final long endX;
    private final int endY;
    private final long endZ;

    protected SubShape(DiscreteVoxelShape pParent, long pStartX, int pStartY, long pStartZ, long pEndX, int pEndY, long pEndZ) {
        super((int) (pEndX - pStartX), pEndY - pStartY, (int) (pEndZ - pStartZ));
        this.parent = pParent;
        this.startX = pStartX;
        this.startY = pStartY;
        this.startZ = pStartZ;
        this.endX = pEndX;
        this.endY = pEndY;
        this.endZ = pEndZ;
    }

    public boolean isFull(long pX, int pY, long pZ) {
        return this.parent.isFull(this.startX + pX, this.startY + pY, this.startZ + pZ);
    }

    public void fill(long pX, int pY, long pZ) {
        this.parent.fill(this.startX + pX, this.startY + pY, this.startZ + pZ);
    }

    public int firstFull(Direction.Axis pAxis) {
        return this.clampToShape(pAxis, this.parent.firstFull(pAxis));
    }

    public int lastFull(Direction.Axis pAxis) {
        return this.clampToShape(pAxis, this.parent.lastFull(pAxis));
    }

    private int clampToShape(Direction.Axis pAxis, int pValue) {
        long i = pAxis.choose(this.startX, this.startY, this.startZ);
        long j = pAxis.choose(this.endX, this.endY, this.endZ);
        return (int) (Mth.clamp(pValue, i, j) - i);
    }
}
