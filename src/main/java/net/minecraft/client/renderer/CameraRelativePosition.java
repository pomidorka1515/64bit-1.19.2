package net.minecraft.client.renderer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
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

   private CameraRelativePosition(double p_234500_, double p_234501_, double p_234502_) {
      this(Mth.lfloor(p_234500_), p_234500_ - (double)Mth.lfloor(p_234500_),
            Mth.floor(p_234501_), p_234501_ - (double)Mth.floor(p_234501_),
            Mth.lfloor(p_234502_), p_234502_ - (double)Mth.lfloor(p_234502_));
   }

   private CameraRelativePosition(long baseX, double fractionX, int baseY, double fractionY,
                                  long baseZ, double fractionZ) {
      this.baseX = baseX;
      this.baseY = baseY;
      this.baseZ = baseZ;
      this.fractionX = fractionX;
      this.fractionY = fractionY;
      this.fractionZ = fractionZ;
   }

   public static CameraRelativePosition of(Vec3 p_234503_) {
      return new CameraRelativePosition(p_234503_.x, p_234503_.y, p_234503_.z);
   }

   public static CameraRelativePosition of(double p_234504_, double p_234505_, double p_234506_) {
      return new CameraRelativePosition(p_234504_, p_234505_, p_234506_);
   }

   public static CameraRelativePosition of(SectorVec3 position) {
      if (position == null) throw new NullPointerException("position");
      int baseY = Mth.floor(position.y());
      return new CameraRelativePosition(position.blockX(), position.subX(), baseY,
            position.y() - (double)baseY, position.blockZ(), position.subZ());
   }

   public long baseX() { return this.baseX; }
   public int baseY() { return this.baseY; }
   public long baseZ() { return this.baseZ; }
   public double fractionX() { return this.fractionX; }
   public double fractionY() { return this.fractionY; }
   public double fractionZ() { return this.fractionZ; }

   public double relativeX(long p_234507_) {
      return relativeLong(p_234507_, this.baseX, this.fractionX);
   }

   public double relativeY(int p_234508_) {
      return (double)(p_234508_ - this.baseY) - this.fractionY;
   }

   public double relativeZ(long p_234509_) {
      return relativeLong(p_234509_, this.baseZ, this.fractionZ);
   }

   public double cameraRelativeToX(long p_234510_) {
      return -this.relativeX(p_234510_);
   }

   public double cameraRelativeToY(int p_234511_) {
      return -this.relativeY(p_234511_);
   }

   public double cameraRelativeToZ(long p_234512_) {
      return -this.relativeZ(p_234512_);
   }

   private static double relativeLong(long p_234513_, long p_234514_, double p_234515_) {
      long i = p_234513_ - p_234514_;
      // Opposite signs on the operands plus a changed result sign means subtraction overflowed.
      if (((p_234513_ ^ p_234514_) & (p_234513_ ^ i)) < 0L) {
         return (double)p_234513_ - (double)p_234514_ - p_234515_;
      }

      return (double)i - p_234515_;
   }
}
