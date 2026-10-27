package net.minecraft.client.renderer;

import net.minecraft.core.Int1088;
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
    private final Int1088 baseX;
    private final int baseY;
    private final Int1088 baseZ;
    private final double fractionX;
    private final double fractionY;
    private final double fractionZ;

    private CameraRelativePosition(Int1088 baseX, int baseY, Int1088 baseZ,
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

    public Int1088 baseX() {
        return this.baseX;
    }

    public int baseY() {
        return this.baseY;
    }

    public Int1088 baseZ() {
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

    public double relativeX(Int1088 blockX) {
        return WorldBounds.signedDifference(blockX, this.baseX) - this.fractionX;
    }

    public double relativeX(long blockX) {
        return this.relativeX(Int1088.fromLong(blockX));
    }

    public double relativeY(int blockY) {
        return ((double) blockY - (double) this.baseY) - this.fractionY;
    }

    public double relativeZ(Int1088 blockZ) {
        return WorldBounds.signedDifference(blockZ, this.baseZ) - this.fractionZ;
    }

    public double relativeZ(long blockZ) {
        return this.relativeZ(Int1088.fromLong(blockZ));
    }

    public double cameraRelativeToX(Int1088 blockX) {
        return -this.relativeX(blockX);
    }

    public double cameraRelativeToX(long blockX) {
        return this.cameraRelativeToX(Int1088.fromLong(blockX));
    }

    public double cameraRelativeToY(int blockY) {
        return -this.relativeY(blockY);
    }

    public double cameraRelativeToZ(Int1088 blockZ) {
        return -this.relativeZ(blockZ);
    }

    public double cameraRelativeToZ(long blockZ) {
        return this.cameraRelativeToZ(Int1088.fromLong(blockZ));
    }
}
