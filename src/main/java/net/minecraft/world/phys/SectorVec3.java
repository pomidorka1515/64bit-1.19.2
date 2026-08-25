package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;

/**
 * An exact X/Z position split into a block coordinate and a local fraction.
 *
 * <p>{@link Vec3} remains the ordinary (and deliberately inexact at very large
 * coordinates) vector type.  This type must be used whenever a player's exact
 * world X/Z coordinates participate in physics.  {@link #toApproximateVec3()}
 * is explicitly a lossy compatibility conversion.</p>
 */
public final class SectorVec3 {
   private static final double TWO_TO_THE_63 = 0x1.0p63;

   private final long blockX;
   private final double subX;
   private final double y;
   private final long blockZ;
   private final double subZ;

   private SectorVec3(long blockX, double subX, double y, long blockZ, double subZ) {
      this.blockX = blockX;
      this.subX = subX;
      this.y = y;
      this.blockZ = blockZ;
      this.subZ = subZ;
   }

   public static SectorVec3 fromBlockAndFraction(long blockX, double subX, double y, long blockZ, double subZ) {
      requireFinite(subX, "subX");
      requireFinite(subZ, "subZ");
      requireFinite(y, "y");
      double xCarryDouble = Math.floor(subX);
      double zCarryDouble = Math.floor(subZ);
      long xCarry = checkedIntegralLong(xCarryDouble, "subX carry");
      long zCarry = checkedIntegralLong(zCarryDouble, "subZ carry");
      long normalizedBlockX = Math.addExact(blockX, xCarry);
      long normalizedBlockZ = Math.addExact(blockZ, zCarry);
      double normalizedSubX = subX - xCarryDouble;
      double normalizedSubZ = subZ - zCarryDouble;
      // The subtraction above is done only on the small local remainder.
      if (!(normalizedSubX >= 0.0D && normalizedSubX < 1.0D)
            || !(normalizedSubZ >= 0.0D && normalizedSubZ < 1.0D)) {
         throw new IllegalArgumentException("Fractions could not be normalized");
      }
      return new SectorVec3(normalizedBlockX, normalizedSubX == 0.0D ? 0.0D : normalizedSubX, y,
            normalizedBlockZ, normalizedSubZ == 0.0D ? 0.0D : normalizedSubZ);
   }

   /** Creates an exact split position from an ordinary double position. This is necessarily limited by the input doubles. */
   public static SectorVec3 fromApproximate(double x, double y, double z) {
      requireFinite(x, "x");
      requireFinite(y, "y");
      requireFinite(z, "z");
      double blockXDouble = Math.floor(x);
      double blockZDouble = Math.floor(z);
      long blockX = checkedIntegralLong(blockXDouble, "x block");
      long blockZ = checkedIntegralLong(blockZDouble, "z block");
      return fromBlockAndFraction(blockX, x - blockXDouble, y, blockZ, z - blockZDouble);
   }

   public long blockX() {
      return this.blockX;
   }

   public long blockZ() {
      return this.blockZ;
   }

   public double subX() {
      return this.subX;
   }

   public double subZ() {
      return this.subZ;
   }

   public double y() {
      return this.y;
   }

   public BlockPos blockPosition() {
      double blockY = Math.floor(this.y);
      if (blockY < Integer.MIN_VALUE || blockY > (double)Integer.MAX_VALUE) {
         throw new IllegalStateException("Y coordinate is outside BlockPos's integer range: " + this.y);
      }
      return new BlockPos(this.blockX, (int)blockY, this.blockZ);
   }

   /** Adds a local movement delta; no large global double is ever reconstructed. */
   public SectorVec3 add(double dx, double dy, double dz) {
      requireFinite(dx, "dx");
      requireFinite(dy, "dy");
      requireFinite(dz, "dz");
      double newSubX = this.subX + dx;
      double newSubZ = this.subZ + dz;
      requireFinite(newSubX, "normalized x movement");
      requireFinite(newSubZ, "normalized z movement");
      return fromBlockAndFraction(this.blockX, newSubX, this.y + dy, this.blockZ, newSubZ);
   }

   public SectorVec3 withY(double y) {
      requireFinite(y, "y");
      return new SectorVec3(this.blockX, this.subX, y, this.blockZ, this.subZ);
   }

   /** Returns this position with an independently supplied normalized X component. */
   public SectorVec3 withX(long blockX, double subX) {
      SectorVec3 normalized = fromBlockAndFraction(blockX, subX, this.y, this.blockZ, this.subZ);
      return new SectorVec3(normalized.blockX, normalized.subX, this.y, this.blockZ, this.subZ);
   }

   /** Returns this position with an independently supplied normalized Z component. */
   public SectorVec3 withZ(long blockZ, double subZ) {
      SectorVec3 normalized = fromBlockAndFraction(this.blockX, this.subX, this.y, blockZ, subZ);
      return new SectorVec3(this.blockX, this.subX, this.y, normalized.blockZ, normalized.subZ);
   }

   /**
    * Interpolates in split-coordinate space. The interpolation delta is formed
    * before any conversion to an absolute double, so rendering cannot turn a
    * large-coordinate player into a staircase of representable doubles.
    */
   public SectorVec3 lerpTo(SectorVec3 target, double amount) {
      if (target == null) throw new NullPointerException("target");
      if (!Double.isFinite(amount)) throw new IllegalArgumentException("amount must be finite");
      Vec3 delta = target.relativeTo(this);
      return this.add(delta.x * amount, delta.y * amount, delta.z * amount);
   }

   /** Returns this position minus {@code other}, in the small/local Vec3 representation. */
   public Vec3 relativeTo(SectorVec3 other) {
      if (other == null) {
         throw new NullPointerException("other");
      }
      long blockDeltaX = Math.subtractExact(this.blockX, other.blockX);
      long blockDeltaZ = Math.subtractExact(this.blockZ, other.blockZ);
      return new Vec3((double)blockDeltaX + (this.subX - other.subX), this.y - other.y,
            (double)blockDeltaZ + (this.subZ - other.subZ));
   }

   /** Converts an exact position to a local physics frame. Integer subtraction precedes conversion to double. */
   public Vec3 toLocal(long originBlockX, int originBlockY, long originBlockZ) {
      long blockDeltaX = Math.subtractExact(this.blockX, originBlockX);
      long blockDeltaZ = Math.subtractExact(this.blockZ, originBlockZ);
      return new Vec3((double)blockDeltaX + this.subX, this.y - (double)originBlockY,
            (double)blockDeltaZ + this.subZ);
   }

   /** Lossy conversion for legacy/render/network compatibility only; never use this for exact physics. */
   public Vec3 toApproximateVec3() {
      return new Vec3((double)this.blockX + this.subX, this.y, (double)this.blockZ + this.subZ);
   }

   public boolean isFinite() {
      return Double.isFinite(this.subX) && Double.isFinite(this.subZ) && Double.isFinite(this.y);
   }

   private static void requireFinite(double value, String name) {
      if (!Double.isFinite(value)) {
         throw new IllegalArgumentException(name + " must be finite: " + value);
      }
   }

   private static long checkedIntegralLong(double value, String name) {
      if (!Double.isFinite(value) || value < -TWO_TO_THE_63 || value >= TWO_TO_THE_63 || value != Math.rint(value)) {
         throw new IllegalArgumentException(name + " is outside the representable long range: " + value);
      }
      return (long)value;
   }

   @Override
   public boolean equals(Object object) {
      if (this == object) return true;
      if (!(object instanceof SectorVec3)) return false;
      SectorVec3 other = (SectorVec3)object;
      return this.blockX == other.blockX && Double.compare(this.subX, other.subX) == 0
            && Double.compare(this.y, other.y) == 0 && this.blockZ == other.blockZ
            && Double.compare(this.subZ, other.subZ) == 0;
   }

   @Override
   public int hashCode() {
      int result = Long.hashCode(this.blockX);
      result = 31 * result + Double.hashCode(this.subX);
      result = 31 * result + Double.hashCode(this.y);
      result = 31 * result + Long.hashCode(this.blockZ);
      return 31 * result + Double.hashCode(this.subZ);
   }

   @Override
   public String toString() {
      return "SectorVec3[blockX=" + this.blockX + ", subX=" + this.subX + ", y=" + this.y
            + ", blockZ=" + this.blockZ + ", subZ=" + this.subZ + "]";
   }
}
