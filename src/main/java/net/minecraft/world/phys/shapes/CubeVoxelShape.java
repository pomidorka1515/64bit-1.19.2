package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class CubeVoxelShape extends VoxelShape {
    private final DoubleList xs;
    private final DoubleList ys;
    private final DoubleList zs;

    protected CubeVoxelShape(DiscreteVoxelShape pShape) {
        super(pShape);
        this.xs = new CubePointRange(pShape.getXSize());
        this.ys = new CubePointRange(pShape.getYSize());
        this.zs = new CubePointRange(pShape.getZSize());
    }

    protected DoubleList getCoords(Direction.Axis pAxis) {
        return switch (pAxis) {
            case X -> this.xs;
            case Y -> this.ys;
            case Z -> this.zs;
        };
    }

    protected int findIndex(Direction.Axis pAxis, double pPosition) {
        int i = this.shape.getSize(pAxis);
        return Mth.floor(Mth.clamp(pPosition * (double) i, -1.0D, (double) i));
    }
}
