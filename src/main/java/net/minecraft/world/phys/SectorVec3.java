package net.minecraft.world.phys;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;

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
      // Fractions are produced by ray/voxel arithmetic and can become non-finite
      // at a degenerate boundary.  A malformed fraction must not take down the
      // render thread; treating it as the beginning of the supplied block is the
      // safest recoverable position.
      double fractionX = finiteFractionOrZero(subX);
      double fractionZ = finiteFractionOrZero(subZ);
      double normalizedY = Double.isFinite(y) ? y : 0.0D;

      Coordinate normalizedX = normalizeCoordinate(blockX, fractionX);
      Coordinate normalizedZ = normalizeCoordinate(blockZ, fractionZ);
      return new SectorVec3(normalizedX.block, normalizedX.fraction, normalizedY,
            normalizedZ.block, normalizedZ.fraction);
   }

   private static double finiteFractionOrZero(double fraction) {
      return Double.isNaN(fraction) || Double.isInfinite(fraction) ? 0.0D : fraction;
   }

   /** Keeps round-off at a voxel boundary from escaping the split-coordinate invariant. */
   private static double clampFraction(double fraction) {
      if (!(fraction >= 0.0D)) return 0.0D;
      if (fraction >= 1.0D) return Math.nextDown(1.0D);
      return fraction == 0.0D ? 0.0D : fraction;
   }

   /** A normalized horizontal coordinate, clamped at the two world edges. */
   private static final class Coordinate {
      private final long block;
      private final double fraction;

      private Coordinate(long block, double fraction) {
         this.block = block;
         this.fraction = fraction;
      }
   }

   private static Coordinate normalizeCoordinate(long block, double fraction) {
      double carryDouble = Math.floor(fraction);
      // A hostile packet can contain a finite but enormous fraction. It has no
      // meaningful long block carry; clamp it to the nearest legal edge rather
      // than allowing a narrowing conversion to wrap.
      if (carryDouble < -0x1.0p63) return new Coordinate(Long.MIN_VALUE, 0.0D);
      if (carryDouble >= 0x1.0p63) return new Coordinate(Long.MAX_VALUE, Math.nextDown(1.0D));

      long carry = (long)carryDouble;
      if (carry > 0L && block > Long.MAX_VALUE - carry) {
         return new Coordinate(Long.MAX_VALUE, Math.nextDown(1.0D));
      }
      if (carry < 0L && block < Long.MIN_VALUE - carry) {
         return new Coordinate(Long.MIN_VALUE, 0.0D);
      }
      return new Coordinate(block + carry, clampFraction(fraction - carryDouble));
   }

   /** Creates an exact split position from decimal text without passing through a double. */
   public static SectorVec3 fromDecimal(String x, double y, String z) {
      if (!Double.isFinite(y)) throw new IllegalArgumentException("y must be finite: " + y);
      DecimalCoordinate decimalX = DecimalCoordinate.parse(x);
      DecimalCoordinate decimalZ = DecimalCoordinate.parse(z);
      return fromBlockAndFraction(decimalX.block, decimalX.fraction, y, decimalZ.block, decimalZ.fraction);
   }

   /** Creates an exact split position from decimal text for one horizontal coordinate. */
   public static SectorVec3 fromDecimal(String x, String z) {
      return fromDecimal(x, 0.0D, z);
   }

   private static final class DecimalCoordinate {
      private final long block;
      private final double fraction;

      private DecimalCoordinate(long block, double fraction) {
         this.block = block;
         this.fraction = fraction;
      }

      private static DecimalCoordinate parse(String text) {
         try {
            BigDecimal value = new BigDecimal(text);
            BigInteger floor = value.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            long block = floor.longValueExact();
            double fraction = value.subtract(new BigDecimal(floor)).doubleValue();
            if (!Double.isFinite(fraction) || fraction < 0.0D || fraction >= 1.0D) {
               throw new IllegalArgumentException("coordinate fraction is not representable: " + text);
            }
            return new DecimalCoordinate(block, fraction);
         } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("coordinate is not representable: " + text, exception);
         }
      }
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

   /** Alias for the split-coordinate name used by debug and network displays. */
   public long sectorX() {
      return this.blockX;
   }

   public long blockZ() {
      return this.blockZ;
   }

   /** Alias for the split-coordinate name used by debug and network displays. */
   public long sectorZ() {
      return this.blockZ;
   }

   /** The integral block component of the ordinary Y coordinate. */
   public long sectorY() {
      double blockY = Math.floor(this.y);
      if (blockY < -TWO_TO_THE_63 || blockY >= TWO_TO_THE_63) {
         throw new IllegalStateException("Y coordinate is outside the representable sector range: " + this.y);
      }
      return (long)blockY;
   }

   public double subX() {
      return this.subX;
   }

   /** The fractional component of the ordinary Y coordinate. */
   public double subY() {
      return this.y - (double)this.sectorY();
   }

   /**
    * Formats a split coordinate without first reconstructing it as a double.
    * This is important for coordinates beyond the 53-bit precision of doubles.
    */
   public static String formatCoordinate(long sector, double sub, int fractionalDigits) {
      if (!Double.isFinite(sub)) {
         throw new IllegalArgumentException("sub-coordinate must be finite: " + sub);
      }
      if (fractionalDigits < 0) {
         throw new IllegalArgumentException("fractionalDigits must not be negative");
      }
      BigDecimal coordinate = BigDecimal.valueOf(sector).add(BigDecimal.valueOf(sub));
      return coordinate.setScale(fractionalDigits, RoundingMode.HALF_UP).toPlainString();
   }

   public String formatX(int fractionalDigits) {
      return formatCoordinate(this.blockX, this.subX, fractionalDigits);
   }

   public String formatZ(int fractionalDigits) {
      return formatCoordinate(this.blockZ, this.subZ, fractionalDigits);
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
      double newY = this.y + dy;
      requireFinite(newSubX, "normalized x movement");
      requireFinite(newSubZ, "normalized z movement");
      requireFinite(newY, "normalized y movement");
      return fromBlockAndFraction(this.blockX, newSubX, newY, this.blockZ, newSubZ);
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

   /** Replaces X from its decimal command spelling without reconstructing the other axis. */
   public SectorVec3 withXDecimal(String coordinate) {
      DecimalCoordinate value = DecimalCoordinate.parse(coordinate);
      return withX(value.block, value.fraction);
   }

   /** Returns this position with an independently supplied normalized Z component. */
   public SectorVec3 withZ(long blockZ, double subZ) {
      SectorVec3 normalized = fromBlockAndFraction(this.blockX, this.subX, this.y, blockZ, subZ);
      return new SectorVec3(this.blockX, this.subX, this.y, normalized.blockZ, normalized.subZ);
   }

   /** Replaces Z from its decimal command spelling without reconstructing the other axis. */
   public SectorVec3 withZDecimal(String coordinate) {
      DecimalCoordinate value = DecimalCoordinate.parse(coordinate);
      return withZ(value.block, value.fraction);
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

   
   public double relativeX(SectorVec3 other) {
      if (other == null) throw new NullPointerException("other");
      return signedDifference(this.blockX, other.blockX) + (this.subX - other.subX);
   }

   public double relativeY(SectorVec3 other) {
      if (other == null) throw new NullPointerException("other");
      return this.y - other.y;
   }

   public double relativeZ(SectorVec3 other) {
      if (other == null) throw new NullPointerException("other");
      return signedDifference(this.blockZ, other.blockZ) + (this.subZ - other.subZ);
   }

   /** Returns this position minus {@code other}, in the small/local Vec3 representation. */
   public Vec3 relativeTo(SectorVec3 other) {
      return new Vec3(this.relativeX(other), this.relativeY(other), this.relativeZ(other));
   }

   /** Converts an exact position to a local physics frame. Integer subtraction precedes conversion to double. */
   public Vec3 toLocal(long originBlockX, int originBlockY, long originBlockZ) {
      return new Vec3(signedDifference(this.blockX, originBlockX) + this.subX,
            this.y - (double)originBlockY, signedDifference(this.blockZ, originBlockZ) + this.subZ);
   }

   /** Lossy conversion for legacy/render/network compatibility only; never use this for exact physics. */
   public Vec3 toApproximateVec3() {
      // A double cannot represent Long.MAX_VALUE as a finite value below 2^63.
      // Keep this compatibility mirror finite and on the legal side of the edge;
      // exact callers must continue to use this SectorVec3 directly.
      return new Vec3(approximateCoordinate(this.blockX, this.subX), this.y,
            approximateCoordinate(this.blockZ, this.subZ));
   }

   private static double approximateCoordinate(long block, double fraction) {
      double value = (double)block + fraction;
      return value >= 0x1.0p63 ? Math.nextDown(0x1.0p63) : value;
   }

   private static double signedDifference(long value, long origin) {
      return WorldBounds.signedDifference(value, origin);
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
