package net.minecraft.world.phys;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.Int128Math;
import net.minecraft.world.level.WorldBounds;

/**
 * An exact X/Z position split into a block coordinate and a local fraction.
 *
 * <p>{@link Vec3} remains the ordinary (and deliberately inexact at very large
 * coordinates) vector type.  This type must be used whenever a player's exact
 * world X/Z coordinates participate in physics. {@link #toExactVec3()}
 * retains the split position when crossing a Vec3 API boundary.</p>
 */
public final class SectorVec3 {
    public static final com.mojang.serialization.Codec<SectorVec3> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
            Int128.CODEC.fieldOf("block_x").forGetter(SectorVec3::blockX),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("sub_x").forGetter(SectorVec3::subX),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("y").forGetter(SectorVec3::y),
            Int128.CODEC.fieldOf("block_z").forGetter(SectorVec3::blockZ),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("sub_z").forGetter(SectorVec3::subZ)
    ).apply(instance, SectorVec3::fromBlockAndFraction));
    private static final double TWO_TO_THE_63 = 0x1.0p63;

    private final Int128 blockX;
    private final double subX;
    private final double y;
    private final Int128 blockZ;
    private final double subZ;

    private SectorVec3(Int128 blockX, double subX, double y, Int128 blockZ, double subZ) {
        this.blockX = blockX;
        this.subX = subX;
        this.y = y;
        this.blockZ = blockZ;
        this.subZ = subZ;
    }

    public static SectorVec3 fromBlockAndFraction(long blockX, double subX, double y, long blockZ, double subZ) {
        return fromRaw(blockX >> 63, blockX, subX, y, blockZ >> 63, blockZ, subZ);
    }

    public static SectorVec3 fromBlockAndFraction(Int128 blockX, double subX, double y, Int128 blockZ, double subZ) {
        return fromRaw(blockX.hi(), blockX.lo(), subX, y, blockZ.hi(), blockZ.lo(), subZ);
    }

    /**
     * Allocation-free core of {@link #fromBlockAndFraction(long, double, double, long, double)}.
     * The only heap objects produced are the finished value and its two {@link Int128}
     * halves; every intermediate stays in the caller's locals.
     */
    private static SectorVec3 fromRaw(long blockXHi, long blockXLo, double subX, double y, long blockZHi, long blockZLo, double subZ) {
        // Fractions are produced by ray/voxel arithmetic and can become non-finite
        // at a degenerate boundary.  A malformed fraction must not take down the
        // render thread; treating it as the beginning of the supplied block is the
        // safest recoverable position.
        double fractionX = finiteFractionOrZero(subX);
        double fractionZ = finiteFractionOrZero(subZ);
        double normalizedY = Double.isFinite(y) ? y : 0.0D;

        double carryX = Math.floor(fractionX);
        double carryZ = Math.floor(fractionZ);

        // Cold exit: a carry at or beyond the 128-bit span can cancel a large
        // opposite block, so saturation alone is not exact; the wide path
        // keeps the historical BigDecimal arithmetic.
        if (!carryIn128Bits(carryX) || !carryIn128Bits(carryZ)) {
            return fromRawCold(Int128.of(blockXHi, blockXLo), fractionX, normalizedY,
                    Int128.of(blockZHi, blockZLo), fractionZ);
        }

        long deltaXHi = Int128Math.doubleFloorHi(carryX);
        long deltaXLo = Int128Math.doubleFloorLo(carryX);
        long deltaYHi = Int128Math.doubleFloorHi(carryZ);
        long deltaYLo = Int128Math.doubleFloorLo(carryZ);

        long sumXLo = blockXLo + deltaXLo;
        long carryOutX = Int128Math.addCarry(blockXLo, deltaXLo, sumXLo);
        long sumXHi = blockXHi + deltaXHi + carryOutX;
        long normalizedBlockXHi;
        long normalizedBlockXLo;
        double normalizedFractionX;
        if (Int128Math.addOverflowed(blockXHi, deltaXHi, carryOutX, sumXHi)) {
            // Int128.addSaturated clamps at the edge in the direction of the delta sign.
            boolean upwardX = Int128Math.compare(deltaXHi, deltaXLo, 0L, 0L) > 0;
            normalizedBlockXHi = upwardX ? WorldBounds.MAX_BLOCK.hi() : WorldBounds.MIN_BLOCK.hi();
            normalizedBlockXLo = upwardX ? WorldBounds.MAX_BLOCK.lo() : WorldBounds.MIN_BLOCK.lo();
            normalizedFractionX = upwardX ? Math.nextDown(1.0D) : 0.0D;
        } else {
            normalizedBlockXHi = sumXHi;
            normalizedBlockXLo = sumXLo;
            normalizedFractionX = clampFraction(fractionX - carryX);
        }

        long sumZLo = blockZLo + deltaYLo;
        long carryOutZ = Int128Math.addCarry(blockZLo, deltaYLo, sumZLo);
        long sumZHi = blockZHi + deltaYHi + carryOutZ;
        long normalizedBlockZHi;
        long normalizedBlockZLo;
        double normalizedFractionZ;
        if (Int128Math.addOverflowed(blockZHi, deltaYHi, carryOutZ, sumZHi)) {
            boolean upwardZ = Int128Math.compare(deltaYHi, deltaYLo, 0L, 0L) > 0;
            normalizedBlockZHi = upwardZ ? WorldBounds.MAX_BLOCK.hi() : WorldBounds.MIN_BLOCK.hi();
            normalizedBlockZLo = upwardZ ? WorldBounds.MAX_BLOCK.lo() : WorldBounds.MIN_BLOCK.lo();
            normalizedFractionZ = upwardZ ? Math.nextDown(1.0D) : 0.0D;
        } else {
            normalizedBlockZHi = sumZHi;
            normalizedBlockZLo = sumZLo;
            normalizedFractionZ = clampFraction(fractionZ - carryZ);
        }

        return new SectorVec3(Int128.of(normalizedBlockXHi, normalizedBlockXLo), normalizedFractionX, normalizedY,
                Int128.of(normalizedBlockZHi, normalizedBlockZLo), normalizedFractionZ);
    }

    private static boolean carryIn128Bits(double carry) {
        return carry >= -0x1.0p127 && carry < 0x1.0p127;
    }

    /** Historical exact path for carries beyond the 128-bit span. Cold by contract. */
    private static SectorVec3 fromRawCold(Int128 blockX, double fractionX, double normalizedY, Int128 blockZ, double fractionZ) {
        double carryX = Math.floor(fractionX);
        Int128 normalizedBlockX;
        double normalizedFractionX;
        if (overflows(blockX, carryX, false)) {
            normalizedBlockX = WorldBounds.MIN_BLOCK;
            normalizedFractionX = 0.0D;
        } else if (overflows(blockX, carryX, true)) {
            normalizedBlockX = WorldBounds.MAX_BLOCK;
            normalizedFractionX = Math.nextDown(1.0D);
        } else {
            normalizedBlockX = addCarry(blockX, carryX);
            normalizedFractionX = clampFraction(fractionX - carryX);
        }

        double carryZ = Math.floor(fractionZ);
        Int128 normalizedBlockZ;
        double normalizedFractionZ;
        if (overflows(blockZ, carryZ, false)) {
            normalizedBlockZ = WorldBounds.MIN_BLOCK;
            normalizedFractionZ = 0.0D;
        } else if (overflows(blockZ, carryZ, true)) {
            normalizedBlockZ = WorldBounds.MAX_BLOCK;
            normalizedFractionZ = Math.nextDown(1.0D);
        } else {
            normalizedBlockZ = addCarry(blockZ, carryZ);
            normalizedFractionZ = clampFraction(fractionZ - carryZ);
        }

        return new SectorVec3(normalizedBlockX, normalizedFractionX, normalizedY,
                normalizedBlockZ, normalizedFractionZ);
    }

    private static double finiteFractionOrZero(double fraction) {
        return Double.isNaN(fraction) || Double.isInfinite(fraction) ? 0.0D : fraction;
    }

    private static boolean overflows(Int128 block, double carry, boolean upper) {
        if (carry >= -0x1.0p127 && carry < 0x1.0p127) {
            Int128 delta = Int128.fromDoubleFloorSaturated(carry);
            return upper ? delta.compareTo(Int128.ZERO) > 0 && block.compareTo(Int128.MAX.subtract(delta)) > 0
                    : delta.compareTo(Int128.ZERO) < 0 && block.compareTo(Int128.MIN.subtract(delta)) < 0;
        }
        // Outside the signed domain a carry can still cancel the opposite block.
        // Detect overflow in an unsigned 128-bit magnitude; never allocate decimal arithmetic.
        if (carry >= 0x1.0p128) return upper;
        if (carry <= -0x1.0p128) return !upper;
        if (carry >= 0x1.0p127) {
            if (!upper) return false;
            if (block.hi() >= 0L) return true;
            Int128 shifted = Int128.of(block.hi() ^ Long.MIN_VALUE, block.lo());
            Int128 remainder = Int128.fromDoubleFloorSaturated(carry - 0x1.0p127);
            return shifted.compareTo(Int128.MAX.subtract(remainder)) > 0;
        }
        if (upper) return false;
        if (block.hi() < 0L) return true;
        Int128 shifted = Int128.of(block.hi() ^ Long.MIN_VALUE, block.lo());
        Int128 remainder = Int128.fromDoubleFloorSaturated(carry + 0x1.0p127);
        return shifted.compareTo(Int128.MIN.subtract(remainder)) < 0;
    }

    private static Int128 addCarry(Int128 block, double carry) {
        return Int128Math.addDoubleBlockOffset(block.hi(), block.lo(), carry);
    }

    /** Keeps round-off at a voxel boundary from escaping the split-coordinate invariant. */
    private static double clampFraction(double fraction) {
        if (!(fraction >= 0.0D))
            return 0.0D;
        if (fraction >= 1.0D)
            return Math.nextDown(1.0D);
        return fraction == 0.0D ? 0.0D : fraction;
    }

    /** Creates an exact split position from decimal text without passing through a double. */
    public static SectorVec3 fromDecimal(String x, double y, String z) {
        if (!Double.isFinite(y))
            throw new IllegalArgumentException("y must be finite: " + y);
        DecimalCoordinate decimalX = DecimalCoordinate.parse(x);
        DecimalCoordinate decimalZ = DecimalCoordinate.parse(z);
        return fromBlockAndFraction(decimalX.block, decimalX.fraction, y, decimalZ.block, decimalZ.fraction);
    }

    /** Creates an exact split position from decimal text for one horizontal coordinate. */
    public static SectorVec3 fromDecimal(String x, String z) {
        return fromDecimal(x, 0.0D, z);
    }

    private static final class DecimalCoordinate {
        private final Int128 block;
        private final double fraction;

        private DecimalCoordinate(Int128 block, double fraction) {
            this.block = block;
            this.fraction = fraction;
        }

        private static DecimalCoordinate parse(String text) {
            try {
                BigDecimal value = new BigDecimal(text);
                BigInteger floor = value.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
                Int128 block = Int128.parseDecimal(floor.toString());
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
    public static SectorVec3 fromApproximate(Vec3 position) {
        if (position instanceof ExactVec3) {
            return ExactVec3.position(position);
        }
        return fromApproximate(position.x, position.y, position.z);
    }

    public static SectorVec3 fromApproximate(double x, double y, double z) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        return fromRaw(Int128Math.doubleFloorHi(x), Int128Math.doubleFloorLo(x), approximateFraction(x), y,
                Int128Math.doubleFloorHi(z), Int128Math.doubleFloorLo(z), approximateFraction(z));
    }

    /**
    * Splits a legacy absolute double at the nearest representable world edge.
    * Legacy particle and sound code still supplies these doubles, so rejecting
    * a finite coordinate here can crash the render thread before it can use the
    * exact sector-relative path.
    */
    private static double approximateFraction(double coordinate) {
        double clamped = WorldBounds.clampAbsoluteDouble(coordinate);
        return clamped - Math.floor(clamped);
    }

    public Int128 blockX() {
        return this.blockX;
    }

    public Int128 blockZ() {
        return this.blockZ;
    }

    public long blockXHi() { return this.blockX.hi(); }
    public long blockXLo() { return this.blockX.lo(); }
    public long blockZHi() { return this.blockZ.hi(); }
    public long blockZLo() { return this.blockZ.lo(); }

    /** The integral block component of the ordinary Y coordinate. */
    public long sectorY() {
        double blockY = Math.floor(this.y);
        if (blockY < -TWO_TO_THE_63 || blockY >= TWO_TO_THE_63) {
            throw new IllegalStateException("Y coordinate is outside the representable sector range: " + this.y);
        }
        return (long) blockY;
    }

    public double subX() {
        return this.subX;
    }

    /** The fractional component of the ordinary Y coordinate. */
    public double subY() {
        return this.y - (double) this.sectorY();
    }

    /**
    * Formats a split coordinate without first reconstructing it as a double.
    * This is important for coordinates beyond the 53-bit precision of doubles.
    */
    public static String formatCoordinate(long sector, double sub, int fractionalDigits) {
        return formatCoordinate(Int128.fromLong(sector), sub, fractionalDigits);
    }

    public static String formatCoordinate(Int128 sector, double sub, int fractionalDigits) {
        if (!Double.isFinite(sub)) {
            throw new IllegalArgumentException("sub-coordinate must be finite: " + sub);
        }
        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractionalDigits must not be negative");
        }
        BigDecimal coordinate = new BigDecimal(sector.toString()).add(BigDecimal.valueOf(sub));
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
        if (blockY < Integer.MIN_VALUE || blockY > (double) Integer.MAX_VALUE) {
            throw new IllegalStateException("Y coordinate is outside BlockPos's integer range: " + this.y);
        }
        return new BlockPos(this.blockX, (int) blockY, this.blockZ);
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

    /**
     * Render-only displacement. Retains any displacement beyond the 128-bit world
     * edge in the local component; never use this to move an entity.
     */
    public SectorVec3 offsetCamera(double dx, double dy, double dz) {
        SectorVec3 bounded = this.add(dx, dy, dz);
        Vec3 applied = bounded.relativeTo(this);
        return new SectorVec3(bounded.blockX, bounded.subX + (dx - applied.x),
                bounded.y, bounded.blockZ, bounded.subZ + (dz - applied.z));
    }

    /** Whether this render-only position has a representable block address. */
    public boolean isInsideWorld() {
        return this.subX >= 0.0D && this.subX < 1.0D
                && this.subZ >= 0.0D && this.subZ < 1.0D;
    }

    /** Returns this position with an independently supplied normalized X component. */
    public SectorVec3 withX(long blockX, double subX) {
        return this.withX(Int128.fromLong(blockX), subX);
    }

    public SectorVec3 withX(Int128 blockX, double subX) {
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
        return this.withZ(Int128.fromLong(blockZ), subZ);
    }

    public SectorVec3 withZ(Int128 blockZ, double subZ) {
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
        if (target == null)
            throw new NullPointerException("target");
        if (!Double.isFinite(amount))
            throw new IllegalArgumentException("amount must be finite");
        Vec3 delta = target.relativeTo(this);
        return this.add(delta.x * amount, delta.y * amount, delta.z * amount);
    }

    public double relativeX(SectorVec3 other) {
        if (other == null)
            throw new NullPointerException("other");
        return signedDifference(this.blockX, other.blockX) + (this.subX - other.subX);
    }

    public double relativeY(SectorVec3 other) {
        if (other == null)
            throw new NullPointerException("other");
        return this.y - other.y;
    }

    public double relativeZ(SectorVec3 other) {
        if (other == null)
            throw new NullPointerException("other");
        return signedDifference(this.blockZ, other.blockZ) + (this.subZ - other.subZ);
    }

    /** Returns this position minus {@code other}, in the small/local Vec3 representation. */
    public Vec3 relativeTo(SectorVec3 other) {
        return new Vec3(this.relativeX(other), this.relativeY(other), this.relativeZ(other));
    }

    /** Converts an exact position to a local physics frame. Integer subtraction precedes conversion to double. */
    public Vec3 toLocal(long originBlockX, int originBlockY, long originBlockZ) {
        return this.toLocal(Int128.fromLong(originBlockX), originBlockY, Int128.fromLong(originBlockZ));
    }

    public Vec3 toLocal(Int128 originBlockX, int originBlockY, Int128 originBlockZ) {
        return new Vec3(signedDifference(this.blockX, originBlockX) + this.subX,
                this.y - (double) originBlockY, signedDifference(this.blockZ, originBlockZ) + this.subZ);
    }

    /** Compatibility carrier that retains the authoritative split position. */
    public Vec3 toExactVec3() {
        return new ExactVec3(this);
    }

    private static double signedDifference(Int128 value, Int128 origin) {
        return WorldBounds.signedDifference(value, origin);
    }

    /** Precision guard for local physics; the shared limit is span-based, not high-word-based. */
    public boolean localSpanExceeds(SectorVec3 other) {
        return WorldBounds.localSpanExceeds(this.blockX, other.blockX)
                || WorldBounds.localSpanExceeds(this.blockZ, other.blockZ);
    }

    public boolean isLocalTo(SectorVec3 other) {
        return !this.localSpanExceeds(other);
    }

    public boolean isFinite() {
        return Double.isFinite(this.subX) && Double.isFinite(this.subZ) && Double.isFinite(this.y);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof SectorVec3))
            return false;
        SectorVec3 other = (SectorVec3) object;
        return this.blockX.equals(other.blockX) && Double.compare(this.subX, other.subX) == 0
                && Double.compare(this.y, other.y) == 0 && this.blockZ.equals(other.blockZ)
                && Double.compare(this.subZ, other.subZ) == 0;
    }

    @Override
    public int hashCode() {
        int result = this.blockX.hashCode();
        result = 31 * result + Double.hashCode(this.subX);
        result = 31 * result + Double.hashCode(this.y);
        result = 31 * result + this.blockZ.hashCode();
        return 31 * result + Double.hashCode(this.subZ);
    }

    @Override
    public String toString() {
        return "SectorVec3[blockX=" + this.blockX + ", subX=" + this.subX + ", y=" + this.y
                + ", blockZ=" + this.blockZ + ", subZ=" + this.subZ + "]";
    }
}
