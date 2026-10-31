package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Function;
import java.util.stream.LongStream;

import javax.annotation.concurrent.Immutable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;

@Immutable
public class Vec3i implements Comparable<Vec3i> {
    /** Exactly thirty-five longs [x limb 0..16, y, z limb 0..16]; the old five-long pair form is rejected. */
    public static final Codec<Vec3i> CODEC = Codec.LONG_STREAM.comapFlatMap(stream -> {
        long[] coordinates = stream.limit(36L).toArray();
        if (coordinates.length != 2 * Int1088.LIMBS + 1) {
            return DataResult.error("Vec3i requires exactly 35 longs [17 x limbs, y, 17 z limbs]");
        }
        long y = coordinates[Int1088.LIMBS];
        if (y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) {
            return DataResult.error("Vec3i y coordinate must fit in an int");
        }
        long[] xLimbs = new long[Int1088.LIMBS];
        long[] zLimbs = new long[Int1088.LIMBS];
        System.arraycopy(coordinates, 0, xLimbs, 0, Int1088.LIMBS);
        System.arraycopy(coordinates, Int1088.LIMBS + 1, zLimbs, 0, Int1088.LIMBS);
        return DataResult.success(new Vec3i(Int1088.fromFixedLimbs(xLimbs), (int) y, Int1088.fromFixedLimbs(zLimbs)));
    }, value -> {
        long[] coordinates = new long[2 * Int1088.LIMBS + 1];
        value.getX().copyFixed(coordinates, 0);
        coordinates[Int1088.LIMBS] = value.getY();
        value.getZ().copyFixed(coordinates, Int1088.LIMBS + 1);
        return LongStream.of(coordinates);
    });
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    private Int1088 xValue;
    private int y;
    private Int1088 zValue;

    private static Function<Vec3i, DataResult<Vec3i>> checkOffsetAxes(int p_194646_) {
        return (p_194649_) -> {
            Int1088 min = Int1088.fromLong(-(long) p_194646_);
            Int1088 max = Int1088.fromLong(p_194646_);
            return p_194649_.getX().compareTo(min) > 0 && p_194649_.getX().compareTo(max) < 0 && p_194649_.getY() > -(long) p_194646_ && p_194649_.getY() < p_194646_ && p_194649_.getZ().compareTo(min) > 0 && p_194649_.getZ().compareTo(max) < 0 ? DataResult.success(p_194649_) : DataResult.error("Position out of range, expected at most " + p_194646_ + ": " + p_194649_);
        };
    }

    public static Codec<Vec3i> offsetCodec(int p_194651_) {
        return CODEC.flatXmap(checkOffsetAxes(p_194651_), checkOffsetAxes(p_194651_));
    }

    public Vec3i(long pX, int pY, long pZ) {
        this(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    public Vec3i(Int1088 pX, int pY, Int1088 pZ) {
        this.xValue = pX;
        this.y = pY;
        this.zValue = pZ;
    }

    /** Copies a fixed-width little-endian limb view, sign-extending any unused words. */
    protected Vec3i(long[] pX, int pY, long[] pZ) {
        this(Int1088.fromFixedLimbs(pX), pY, Int1088.fromFixedLimbs(pZ));
    }

    /** Full-width primitive factory; no temporary coordinate objects at the call site. */
    public static Vec3i fromLimbs(long[] pX, int pY, long[] pZ) {
        return new Vec3i(pX, pY, pZ);
    }

    public Vec3i(double pX, double pY, double pZ) {
        this(Int1088.fromDoubleFloorSaturated(pX), Mth.floor(pY), Int1088.fromDoubleFloorSaturated(pZ));
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof Vec3i)) {
            return false;
        } else {
            Vec3i vec3i = (Vec3i) pOther;
            return this.getY() == vec3i.getY() && this.sameX(vec3i) && this.sameZ(vec3i);
        }
    }

    public int hashCode() {
        return (this.getY() + this.zHash() * 31) * 31 + this.xHash();
    }

    public int compareTo(Vec3i pOther) {
        int result = compareX(this, pOther);
        if (result == 0) result = Integer.compare(this.getY(), pOther.getY());
        return result == 0 ? compareZ(this, pOther) : result;
    }

    /** Value-object API boundary; internal arithmetic should use the limb accessors. */
    public Int1088 getX() {
        return this.xValue;
    }

    public int getY() {
        return this.y;
    }

    /** Value-object API boundary; internal arithmetic should use the limb accessors. */
    public Int1088 getZ() {
        return this.zValue;
    }

    public Int1088 x() { return this.getX(); }
    public int y() { return this.getY(); }
    public Int1088 z() { return this.getZ(); }

    public long xLimb(int pIndex) { return this.getX().limb(pIndex); }
    public long zLimb(int pIndex) { return this.getZ().limb(pIndex); }
    public int xLimbCount() { return this.getX().limbCount(); }
    public int zLimbCount() { return this.getZ().limbCount(); }
    public boolean xFitsLong() { return this.getX().fitsLong(); }
    public boolean zFitsLong() { return this.getZ().fitsLong(); }
    public long xLow() { return this.getX().lowLimb(); }
    public long zLow() { return this.getZ().lowLimb(); }

    protected Vec3i setX(long pX) {
        this.xValue = Int1088.fromLong(pX);
        return this;
    }

    protected Vec3i setX(Int1088 pX) {
        this.xValue = pX;
        return this;
    }

    protected Vec3i setY(int pY) {
        this.y = pY;
        return this;
    }

    protected Vec3i setZ(long pZ) {
        this.zValue = Int1088.fromLong(pZ);
        return this;
    }

    protected Vec3i setZ(Int1088 pZ) {
        this.zValue = pZ;
        return this;
    }

    public Vec3i offset(double pDx, double pDy, double pDz) {
        return pDx == 0.0D && pDy == 0.0D && pDz == 0.0D ? this : new Vec3i(
                WorldBounds.addDoubleBlockOffset(this.getX(), pDx), Double.isFinite(pDy) ? Mth.floor((double) this.getY() + pDy) : this.getY(),
                WorldBounds.addDoubleBlockOffset(this.getZ(), pDz));
    }

    public Vec3i offset(long pDx, int pDy, long pDz) {
        return this.offset(Int1088.fromLong(pDx), pDy, Int1088.fromLong(pDz));
    }

    public Vec3i offset(Vec3i pVector) {
        return this.offset(pVector.getX(), pVector.getY(), pVector.getZ());
    }

    public Vec3i offset(Int1088 pDx, int pDy, Int1088 pDz) {
        if (pDx.signum() == 0 && pDy == 0 && pDz.signum() == 0) {
            return this;
        }
        return new Vec3i(this.getX().addSaturated(pDx), WorldBounds.addSaturated(this.getY(), pDy),
                this.getZ().addSaturated(pDz));
    }

    public Vec3i subtract(Vec3i pVector) {
        return new Vec3i(this.getX().subtractSaturated(pVector.getX()),
                (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) this.getY() - pVector.getY())),
                this.getZ().subtractSaturated(pVector.getZ()));
    }

    public Vec3i multiply(int pScalar) {
        if (pScalar == 1) {
            return this;
        } else {
            return pScalar == 0 ? ZERO : new Vec3i(this.getX().multiplySaturated(pScalar),
                    WorldBounds.multiplySaturated(this.getY(), pScalar), this.getZ().multiplySaturated(pScalar));
        }
    }

    public Vec3i above() {
        return this.above(1);
    }

    public Vec3i above(int pDistance) {
        return this.relative(Direction.UP, pDistance);
    }

    public Vec3i below() {
        return this.below(1);
    }

    public Vec3i below(int pDistance) {
        return this.relative(Direction.DOWN, pDistance);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int pDistance) {
        return this.relative(Direction.NORTH, pDistance);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int pDistance) {
        return this.relative(Direction.SOUTH, pDistance);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int pDistance) {
        return this.relative(Direction.WEST, pDistance);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int pDistance) {
        return this.relative(Direction.EAST, pDistance);
    }

    public Vec3i relative(Direction pDirection) {
        return this.relative(pDirection, 1);
    }

    public Vec3i relative(Direction pDirection, int pDistance) {
        return this.offset((long) pDirection.getStepX() * pDistance, pDirection.getStepY() * pDistance,
                (long) pDirection.getStepZ() * pDistance);
    }

    public Vec3i relative(Direction.Axis pAxis, int pAmount) {
        if (pAmount == 0) {
            return this;
        } else {
            int i = pAxis == Direction.Axis.X ? pAmount : 0;
            int j = pAxis == Direction.Axis.Y ? pAmount : 0;
            int k = pAxis == Direction.Axis.Z ? pAmount : 0;
            return this.offset((long) i, j, (long) k);
        }
    }

    public Vec3i cross(Vec3i pVector) {
        return new Vec3i(crossComponent(pVector.getZ(), this.getY(), this.getZ(), pVector.getY()),
                crossY(this.getZ(), pVector.getX(), this.getX(), pVector.getZ()),
                crossComponent(this.getX(), pVector.getY(), pVector.getX(), this.getY()));
    }

    /**
     * Full a*b - c*d determinant, reduced only at the final 1088-bit boundary.
     * Saturating each product separately would lose cancellation.
     */
    private static Int1088 crossComponent(Int1088 a, long b, Int1088 c, long d) {
        return saturateWide(wideDifference(wideProduct(a, Int1088.fromLong(b)),
                wideProduct(c, Int1088.fromLong(d))));
    }

    /** Keep the complete 2176-bit determinant until the final vertical int clamp. */
    private static int crossY(Int1088 a, Int1088 b, Int1088 c, Int1088 d) {
        return clampWideToInt(wideDifference(wideProduct(a, b), wideProduct(c, d)));
    }

    private static final int WIDE_LIMBS = 2 * Int1088.LIMBS;
    private static final int DIGITS = 4 * Int1088.LIMBS;

    /** Exact signed 1088 x 1088 product in 34 little-endian limbs (two's complement). */
    private static long[] wideProduct(Int1088 a, Int1088 b) {
        int[] ad = new int[DIGITS];
        int[] bd = new int[DIGITS];
        digits(a, ad);
        digits(b, bd);
        long[] digits = new long[2 * DIGITS];
        for (int i = 0; i < DIGITS; ++i) {
            int ai = ad[i];
            if (ai == 0) continue;
            for (int j = 0; j < DIGITS; ++j) {
                int bj = bd[j];
                if (bj != 0) digits[i + j] += (long) ai * bj;
            }
        }
        long carry = 0L;
        for (int i = 0; i < digits.length; ++i) {
            long value = digits[i] + carry;
            digits[i] = value & 0xffffL;
            carry = value >>> 16;
        }
        long[] out = new long[WIDE_LIMBS];
        for (int word = 0; word < WIDE_LIMBS; ++word) {
            int base = word * 4;
            out[word] = digits[base] | digits[base + 1] << 16 | digits[base + 2] << 32 | digits[base + 3] << 48;
        }
        // The digit multiplication treats both operands as unsigned. Correct the
        // upper half for each signed operand, modulo the exact double width.
        if (a.signum() < 0) subtractUpper(out, b);
        if (b.signum() < 0) subtractUpper(out, a);
        return out;
    }

    private static void subtractUpper(long[] product, Int1088 operand) {
        long borrow = 0L;
        for (int i = 0; i < Int1088.LIMBS; ++i) {
            int index = i + Int1088.LIMBS;
            long a = product[index], b = operand.limb(i);
            long difference = a - b;
            long result = difference - borrow;
            borrow = Long.compareUnsigned(a, b) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1L : 0L;
            product[index] = result;
        }
    }

    private static void digits(Int1088 value, int[] out) {
        for (int word = 0; word < Int1088.LIMBS; ++word) {
            long limb = value.limb(word);
            int base = word * 4;
            out[base] = (int) limb & 0xffff;
            out[base + 1] = (int) (limb >>> 16) & 0xffff;
            out[base + 2] = (int) (limb >>> 32) & 0xffff;
            out[base + 3] = (int) (limb >>> 48) & 0xffff;
        }
    }

    private static long[] wideDifference(long[] first, long[] second) {
        long[] out = new long[WIDE_LIMBS];
        long borrow = 0L;
        for (int i = 0; i < WIDE_LIMBS; ++i) {
            long a = first[i];
            long b = second[i];
            long difference = a - b;
            long nextBorrow = Long.compareUnsigned(a, b) < 0 ? 1L : 0L;
            if (Long.compareUnsigned(difference, borrow) < 0) nextBorrow = 1L;
            out[i] = difference - borrow;
            borrow = nextBorrow;
        }
        return out;
    }

    private static Int1088 saturateWide(long[] wide) {
        long extension = wide[Int1088.LIMBS - 1] >> 63;
        boolean fits = true;
        for (int i = Int1088.LIMBS; i < WIDE_LIMBS; ++i) {
            if (wide[i] != extension) {
                fits = false;
                break;
            }
        }
        if (fits) {
            long[] limbs = new long[Int1088.LIMBS];
            System.arraycopy(wide, 0, limbs, 0, Int1088.LIMBS);
            return Int1088.fromFixedLimbs(limbs);
        }
        return wide[WIDE_LIMBS - 1] < 0L ? Int1088.MIN : Int1088.MAX;
    }

    private static int clampWideToInt(long[] wide) {
        long extension = wide[0] >> 63;
        boolean fits = (long) (int) wide[0] == wide[0];
        for (int i = 1; fits && i < WIDE_LIMBS; ++i) {
            fits = wide[i] == extension;
        }
        if (fits) {
            return (int) wide[0];
        }
        return wide[WIDE_LIMBS - 1] < 0L ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }

    public boolean closerThan(Vec3i pVector, double pDistance) {
        return this.distSqr(pVector) < Mth.square(pDistance);
    }

    public boolean closerToCenterThan(Position pPosition, double pDistance) {
        return this.distToCenterSqr(pPosition) < Mth.square(pDistance);
    }

    public boolean closerToCenterThan(net.minecraft.world.phys.SectorVec3 position, double distance) {
        return this.distToCenterSqr(position) < Mth.square(distance);
    }

    public double distToCenterSqr(net.minecraft.world.phys.SectorVec3 position) {
        double dx = WorldBounds.signedDifference(this.getX(), position.blockX()) + 0.5D - position.subX();
        double dy = this.getY() + 0.5D - position.y();
        double dz = WorldBounds.signedDifference(this.getZ(), position.blockZ()) + 0.5D - position.subZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public double distSqr(Vec3i pVector) {
        double dx = WorldBounds.signedDifference(this.getX(), pVector.getX());
        double dy = (double) this.getY() - (double) pVector.getY();
        double dz = WorldBounds.signedDifference(this.getZ(), pVector.getZ());
        return dx * dx + dy * dy + dz * dz;
    }

    public double distToCenterSqr(Position pPosition) {
        return this.distToCenterSqr(pPosition.exactPosition());
    }

    public double distToCenterSqr(double pX, double pY, double pZ) {
        double d0 = differenceFromDouble(this.getX(), pX) + 0.5D;
        double d1 = (double) this.getY() + 0.5D - pY;
        double d2 = differenceFromDouble(this.getZ(), pZ) + 0.5D;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double distToLowCornerSqr(double pX, double pY, double pZ) {
        double d0 = differenceFromDouble(this.getX(), pX);
        double d1 = (double) this.getY() - pY;
        double d2 = differenceFromDouble(this.getZ(), pZ);
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public int distManhattan(Vec3i pVector) {
        double f = WorldBounds.distance(pVector.getX(), this.getX());
        double f1 = Math.abs((double) pVector.getY() - this.getY());
        double f2 = WorldBounds.distance(pVector.getZ(), this.getZ());
        return (int) (f + f1 + f2);
    }

    private static double differenceFromDouble(Int1088 coordinate, double value) {
        if (!Double.isFinite(value)) {
            return -value;
        }
        return WorldBounds.signedDifference(coordinate, Int1088.fromDoubleFloorSaturated(value)) - (value - Math.floor(value));
    }

    public Int1088 get(Direction.Axis pAxis) {
        return pAxis == Direction.Axis.X ? this.getX() : pAxis == Direction.Axis.Z ? this.getZ() : Int1088.fromLong(this.getY());
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }

    /** Double dispatch keeps immutable comparisons on Int1088's identity/array fast path. */
    protected boolean sameX(Vec3i other) {
        return other.hasX(this.getX());
    }

    protected boolean sameZ(Vec3i other) {
        return other.hasZ(this.getZ());
    }

    protected boolean hasX(Int1088 value) {
        return this.getX().equals(value);
    }

    protected boolean hasZ(Int1088 value) {
        return this.getZ().equals(value);
    }

    private static int compareX(Vec3i first, Vec3i second) {
        int result = Long.compare(first.xLimb(Int1088.LIMBS - 1), second.xLimb(Int1088.LIMBS - 1));
        for (int i = Int1088.LIMBS - 2; result == 0 && i >= 0; --i) {
            result = Long.compareUnsigned(first.xLimb(i), second.xLimb(i));
        }
        return result;
    }

    private static int compareZ(Vec3i first, Vec3i second) {
        int result = Long.compare(first.zLimb(Int1088.LIMBS - 1), second.zLimb(Int1088.LIMBS - 1));
        for (int i = Int1088.LIMBS - 2; result == 0 && i >= 0; --i) {
            result = Long.compareUnsigned(first.zLimb(i), second.zLimb(i));
        }
        return result;
    }

    protected int xHash() {
        return this.getX().hashXZ();
    }

    protected int zHash() {
        return this.getZ().hashXZ();
    }

}
