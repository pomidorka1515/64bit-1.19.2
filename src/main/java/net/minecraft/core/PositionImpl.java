package net.minecraft.core;

public class PositionImpl implements Position {
    public net.minecraft.world.phys.SectorVec3 exactPosition() {
        return net.minecraft.world.phys.SectorVec3.fromApproximate(this.x, this.y, this.z);
    }
    protected final double x;
    protected final double y;
    protected final double z;

    public PositionImpl(double pX, double pY, double pZ) {
        this.x = pX;
        this.y = pY;
        this.z = pZ;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }
}
