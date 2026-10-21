package net.minecraft.client.renderer;

import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Immutable render-camera split used to rebase integral world coordinates before
 * they are narrowed to floating point. Instances are safe to capture in chunk
 * compilation tasks.
 */
@OnlyIn(Dist.CLIENT)
public final class CameraRelativePosition {
    private final Int128 baseX;
    private final int baseY;
    private final Int128 baseZ;
    private final double fractionX;
    private final double fractionY;
    private final double fractionZ;

    private CameraRelativePosition(Int128 baseX, int baseY, Int128 baseZ,
            double fractionX, double fractionY, double fractionZ) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.fractionX = fractionX;
        this.fractionY = fractionY;
        this.fractionZ = fractionZ;
    }

    public static CameraRelativePosition of(SectorVec3 exact) {
        return new CameraRelativePosition(
                exact.blockX(),
                exact.blockPosition().getY(),
                exact.blockZ(),
                exact.subX(),
                exact.y() - (double) exact.blockPosition().getY(),
                exact.subZ());
    }

    public Int128 baseX() {
        return this.baseX;
    }

    public int baseY() {
        return this.baseY;
    }

    public Int128 baseZ() {
        return this.baseZ;
    }

    public double fractionX() {
        return this.fractionX;
    }

    public double fractionY() {
        return this.fractionY;
    }

    public double fractionZ() {
        return this.fractionZ;
    }

    public double relativeX(Int128 blockX) {
        return WorldBounds.signedDifference(blockX, this.baseX) - this.fractionX;
    }

    public double relativeX(long blockX) {
        return this.relativeX(Int128.fromLong(blockX));
    }

    public double relativeY(int blockY) {
        return ((double) blockY - (double) this.baseY) - this.fractionY;
    }

    public double relativeZ(Int128 blockZ) {
        return WorldBounds.signedDifference(blockZ, this.baseZ) - this.fractionZ;
    }

    public double relativeZ(long blockZ) {
        return this.relativeZ(Int128.fromLong(blockZ));
    }

    public double cameraRelativeToX(Int128 blockX) {
        return -this.relativeX(blockX);
    }

    public double cameraRelativeToX(long blockX) {
        return this.cameraRelativeToX(Int128.fromLong(blockX));
    }

    public double cameraRelativeToY(int blockY) {
        return -this.relativeY(blockY);
    }

    public double cameraRelativeToZ(Int128 blockZ) {
        return -this.relativeZ(blockZ);
    }

    public double cameraRelativeToZ(long blockZ) {
        return this.cameraRelativeToZ(Int128.fromLong(blockZ));
    }
}
