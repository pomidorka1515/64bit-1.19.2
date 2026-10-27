package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
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
            Int1088.CODEC.fieldOf("block_x").forGetter(SectorVec3::blockX),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("sub_x").forGetter(SectorVec3::subX),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("y").forGetter(SectorVec3::y),
            Int1088.CODEC.fieldOf("block_z").forGetter(SectorVec3::blockZ),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("sub_z").forGetter(SectorVec3::subZ)
    ).apply(instance, SectorVec3::fromBlockAndFraction));
    private static final double TWO_TO_THE_63 = 0x1.0p63;

    private final Int1088 blockX;
    private final double subX;
    private final double y;
    private final Int1088 blockZ;
    private final double subZ;

    private SectorVec3(Int1088 blockX, double subX, double y, Int1088 blockZ, double subZ) {
        this.blockX = blockX;
        this.subX = subX;
        this.y = y;
        this.blockZ = blockZ;
        this.subZ = subZ;
    }

    public static SectorVec3 fromBlockAndFraction(long blockX, double subX, double y, long blockZ, double subZ) {
        return fromBlockAndFraction(Int1088.fromLong(blockX), subX, y, Int1088.fromLong(blockZ), subZ);
    }

    public static SectorVec3 fromBlockAndFraction(Int1088 blockX, double subX, double y, Int1088 blockZ, double subZ) {
        Axis x = normalize(blockX, finiteFractionOrZero(subX));
        Axis z = normalize(blockZ, finiteFractionOrZero(subZ));
        return new SectorVec3(x.block, x.fraction, Double.isFinite(y) ? y : 0.0D, z.block, z.fraction);
    }

    private record Axis(Int1088 block, double fraction) {}

    private static Axis normalize(Int1088 block, double fraction) {
        double floor = Math.floor(fraction);
        Int1088 carry = Int1088.fromDoubleFloorSaturated(floor);
        if (carry.signum() > 0 && block.compareTo(Int1088.MAX.subtract(carry)) > 0)
            return new Axis(Int1088.MAX, Math.nextDown(1.0D));
        if (carry.signum() < 0 && block.compareTo(Int1088.MIN.subtract(carry)) < 0)
            return new Axis(Int1088.MIN, 0.0D);
        return new Axis(block.addSaturated(carry), clampFraction(fraction - floor));
    }

    private static double finiteFractionOrZero(double fraction) {
        return Double.isNaN(fraction) || Double.isInfinite(fraction) ? 0.0D : fraction;
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
        private final Int1088 block;
        private final double fraction;

        private DecimalCoordinate(Int1088 block, double fraction) {
            this.block = block;
            this.fraction = fraction;
        }

        private static DecimalCoordinate parse(String text) {
            try {
                boolean negative = text.startsWith("-");
                int start = text.startsWith("-") || text.startsWith("+") ? 1 : 0;
                int point = text.indexOf('.', start);
                String integer = point < 0 ? text.substring(start) : text.substring(start, point);
                String digits = point < 0 ? "" : text.substring(point + 1);
                if (integer.isEmpty() && digits.isEmpty()) throw new NumberFormatException(text);
                for (int i = 0; i < integer.length(); i++)
                    if (integer.charAt(i) < '0' || integer.charAt(i) > '9') throw new NumberFormatException(text);
                for (int i = 0; i < digits.length(); i++)
                    if (digits.charAt(i) < '0' || digits.charAt(i) > '9') throw new NumberFormatException(text);
                Int1088 block = Int1088.parseDecimal((negative ? "-" : "") + (integer.isEmpty() ? "0" : integer));
                double fraction = digits.isEmpty() ? 0.0D : Double.parseDouble("0." + digits);
                if (negative && fraction != 0.0D) {
                    if (block.equals(Int1088.MIN)) throw new ArithmeticException("coordinate below minimum");
                    block = block.subtractSaturated(Int1088.ONE);
                    fraction = 1.0D - fraction;
                } else if (fraction >= 1.0D) {
                    block = block.addSaturated(Int1088.ONE);
                    fraction = 0.0D;
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
        return fromBlockAndFraction(Int1088.fromDoubleFloorSaturated(x), approximateFraction(x), y,
                Int1088.fromDoubleFloorSaturated(z), approximateFraction(z));
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

    public Int1088 blockX() {
        return this.blockX;
    }

    public Int1088 blockZ() {
        return this.blockZ;
    }

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
        return formatCoordinate(Int1088.fromLong(sector), sub, fractionalDigits);
    }

    public static String formatCoordinate(Int1088 sector, double sub, int fractionalDigits) {
        if (!Double.isFinite(sub)) {
            throw new IllegalArgumentException("sub-coordinate must be finite: " + sub);
        }
        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractionalDigits must not be negative");
        }
        Axis axis = normalize(sector, sub);
        boolean negative = axis.block.signum() < 0;
        // Work on decimal digits, not 1 - fraction in floating point: that
        // subtraction loses tiny fractions and rounding ties at negative blocks.
        String decimal = Double.toString(axis.fraction);
        int exponentAt = decimal.indexOf('E');
        int exponent = exponentAt < 0 ? 0 : Integer.parseInt(decimal.substring(exponentAt + 1));
        String significand = exponentAt < 0 ? decimal : decimal.substring(0, exponentAt);
        int point = significand.indexOf('.');
        String digits = significand.replace(".", "");
        int decimalPoint = point + exponent;
        int length = Math.max(fractionalDigits + 1, digits.length() - decimalPoint);
        char[] fraction = new char[length];
        java.util.Arrays.fill(fraction, '0');
        for (int i = 0; i < digits.length(); ++i) {
            int index = i - decimalPoint;
            if (index >= 0 && index < length) fraction[index] = digits.charAt(i);
        }
        String magnitude;
        if (negative && axis.fraction != 0.0D) {
            magnitude = axis.block.not().toDecimalString(); // abs(block) - 1, even at MIN
            for (int i = 0; i < length; ++i) fraction[i] = (char) ('9' - fraction[i] + '0');
            incrementDigits(fraction);
        } else {
            String whole = axis.block.toDecimalString();
            magnitude = negative ? whole.substring(1) : whole;
        }
        boolean roundUp = fraction[fractionalDigits] >= '5';
        char[] rounded = java.util.Arrays.copyOf(fraction, fractionalDigits);
        if (roundUp && incrementDigits(rounded)) magnitude = incrementMagnitude(magnitude);
        boolean zero = magnitude.equals("0");
        for (char digit : rounded) zero &= digit == '0';
        return (negative && !zero ? "-" : "") + magnitude
                + (fractionalDigits == 0 ? "" : "." + new String(rounded));
    }

    private static boolean incrementDigits(char[] digits) {
        for (int i = digits.length - 1; i >= 0; --i) {
            if (digits[i] != '9') {
                ++digits[i];
                return false;
            }
            digits[i] = '0';
        }
        return true;
    }

    private static String incrementMagnitude(String magnitude) {
        char[] digits = magnitude.toCharArray();
        return incrementDigits(digits) ? "1" + new String(digits) : new String(digits);
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
        if (Double.isNaN(dx)) dx = 0.0D;
        requireFinite(dy, "dy");
        if (Double.isNaN(dz)) dz = 0.0D;
        double newSubX = this.subX + dx;
        double newSubZ = this.subZ + dz;
        double newY = this.y + dy;
        requireFinite(newY, "normalized y movement");
        Axis x = Double.isInfinite(newSubX) ? new Axis(newSubX > 0 ? Int1088.MAX : Int1088.MIN, newSubX > 0 ? Math.nextDown(1.0D) : 0.0D) : normalize(this.blockX, newSubX);
        Axis z = Double.isInfinite(newSubZ) ? new Axis(newSubZ > 0 ? Int1088.MAX : Int1088.MIN, newSubZ > 0 ? Math.nextDown(1.0D) : 0.0D) : normalize(this.blockZ, newSubZ);
        return new SectorVec3(x.block, x.fraction, newY, z.block, z.fraction);
    }

    public SectorVec3 withY(double y) {
        requireFinite(y, "y");
        return new SectorVec3(this.blockX, this.subX, y, this.blockZ, this.subZ);
    }

    /**
     * Render-only displacement. Retains any displacement beyond the world
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
        return this.withX(Int1088.fromLong(blockX), subX);
    }

    public SectorVec3 withX(Int1088 blockX, double subX) {
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
        return this.withZ(Int1088.fromLong(blockZ), subZ);
    }

    public SectorVec3 withZ(Int1088 blockZ, double subZ) {
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
        return this.toLocal(Int1088.fromLong(originBlockX), originBlockY, Int1088.fromLong(originBlockZ));
    }

    public Vec3 toLocal(Int1088 originBlockX, int originBlockY, Int1088 originBlockZ) {
        return new Vec3(signedDifference(this.blockX, originBlockX) + this.subX,
                this.y - (double) originBlockY, signedDifference(this.blockZ, originBlockZ) + this.subZ);
    }

    /** Compatibility carrier that retains the authoritative split position. */
    public Vec3 toExactVec3() {
        return new ExactVec3(this);
    }

    private static double signedDifference(Int1088 value, Int1088 origin) {
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
