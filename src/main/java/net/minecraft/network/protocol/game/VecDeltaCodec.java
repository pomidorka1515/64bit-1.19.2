package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class VecDeltaCodec {
   private static final double TRUNCATION_STEPS = 4096.0D;
   private Vec3 base = Vec3.ZERO;
   @Nullable
   private SectorVec3 sectorBase;

   private static long encode(double p_238018_) {
      return Mth.lfloor(p_238018_ * 4096.0D);
   }

   private static double decode(long p_238020_) {
      return (double)p_238020_ / 4096.0D;
   }

   public Vec3 decode(long p_238022_, long p_238023_, long p_238024_) {
      if (p_238022_ == 0L && p_238023_ == 0L && p_238024_ == 0L) {
         return this.base;
      } else {
         double d0 = p_238022_ == 0L ? this.base.x : decode(encode(this.base.x) + p_238022_);
         double d1 = p_238023_ == 0L ? this.base.y : decode(encode(this.base.y) + p_238023_);
         double d2 = p_238024_ == 0L ? this.base.z : decode(encode(this.base.z) + p_238024_);
         return new Vec3(d0, d1, d2);
      }
   }

   /**
    * Decodes a relative movement into the exact frame established by
    * {@link #setBase(SectorVec3)}.
    *
    * <p>The base is the last network target, not the entity's current render
    * interpolation position. Keeping those two concepts separate is essential:
    * replacing this base with an in-between render position makes each incoming
    * delta start from stale coordinates, so remote entities appear frozen and
    * then jump when an absolute teleport packet arrives.</p>
    */
   public SectorVec3 decodeExact(long x, long y, long z) {
      if (this.sectorBase == null) {
         throw new IllegalStateException("Sector position codec base is not initialized");
      }
      if (x == 0L && y == 0L && z == 0L) {
         return this.sectorBase;
      }
      return this.sectorBase.add(decode(x), decode(y), decode(z));
   }

   public long encodeX(Vec3 p_238026_) {
      return encode(p_238026_.x - this.base.x);
   }

   public long encodeX(SectorVec3 position) {
      return this.sectorBase == null ? this.encodeX(position.toApproximateVec3())
            : encode(position.relativeX(this.sectorBase));
   }

   public long encodeY(Vec3 p_238028_) {
      return encode(p_238028_.y - this.base.y);
   }

   public long encodeY(SectorVec3 position) {
      return this.sectorBase == null ? this.encodeY(position.toApproximateVec3())
            : encode(position.y() - this.sectorBase.y());
   }

   public long encodeZ(Vec3 p_238030_) {
      return encode(p_238030_.z - this.base.z);
   }

   public long encodeZ(SectorVec3 position) {
      return this.sectorBase == null ? this.encodeZ(position.toApproximateVec3())
            : encode(position.relativeZ(this.sectorBase));
   }

   public Vec3 delta(Vec3 p_238032_) {
      return p_238032_.subtract(this.base);
   }

   public void setBase(Vec3 p_238034_) {
      this.base = p_238034_;
      this.sectorBase = null;
   }

   public void setBase(SectorVec3 position) {
      this.sectorBase = position;
      this.base = position.toApproximateVec3();
   }

   @Nullable
   public SectorVec3 getSectorBase() {
      return this.sectorBase;
   }
}