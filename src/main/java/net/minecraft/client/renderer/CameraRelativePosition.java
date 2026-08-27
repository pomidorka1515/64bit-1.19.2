package net.minecraft.client.renderer;

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
   private final long baseX;
   private final int baseY;
   private final long baseZ;
   private final double fractionX;
   private final double fractionY;
   private final double fractionZ;

   private CameraRelativePosition(long baseX, int baseY, long baseZ,
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
         exact.y() - (double)exact.blockPosition().getY(),
         exact.subZ()
      );
   }

   public long baseX() {
      return this.baseX;
   }

   public int baseY() {
      return this.baseY;
   }

   public long baseZ() {
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

   public double relativeX(long blockX) {
      return WorldBounds.signedDifference(blockX, this.baseX) - this.fractionX;
   }

   public double relativeY(int blockY) {
      return (double)(blockY - this.baseY) - this.fractionY;
   }

   public double relativeZ(long blockZ) {
      return WorldBounds.signedDifference(blockZ, this.baseZ) - this.fractionZ;
   }

   public double cameraRelativeToX(long blockX) {
      return -this.relativeX(blockX);
   }

   public double cameraRelativeToY(int blockY) {
      return -this.relativeY(blockY);
   }

   public double cameraRelativeToZ(long blockZ) {
      return -this.relativeZ(blockZ);
   }
}
