package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.annotation.concurrent.Immutable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;

@Immutable
public class Vec3i implements Comparable<Vec3i> {
    /** Exactly five longs [xHi, xLo, y, zHi, zLo]; legacy three-long positions are rejected. */
    public static final Codec<Vec3i> CODEC = Codec.LONG_STREAM.comapFlatMap(stream -> {
        long[] coordinates = stream.limit(6L).toArray();
        if (coordinates.length != 5) {
            return DataResult.error("Vec3i requires exactly 5 longs [xHi, xLo, y, zHi, zLo]");
        }
        if (coordinates[2] < Integer.MIN_VALUE || coordinates[2] > Integer.MAX_VALUE) {
            return DataResult.error("Vec3i y coordinate must fit in an int");
        }
        return DataResult.success(new Vec3i(coordinates[0], coordinates[1], (int) coordinates[2], coordinates[3], coordinates[4]));
    }, value -> LongStream.of(value.getXHi(), value.getXLo(), value.getY(), value.getZHi(), value.getZLo()));
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    private long xHi;
    private long xLo;
    private int y;
    private long zHi;
    private long zLo;

    private static Function<Vec3i, DataResult<Vec3i>> checkOffsetAxes(int p_194646_) {
        return (p_194649_) -> {
            Int128 min = Int128.fromLong(-(long) p_194646_);
            Int128 max = Int128.fromLong(p_194646_);
            return p_194649_.getX().compareTo(min) > 0 && p_194649_.getX().compareTo(max) < 0 && p_194649_.getY() > -(long) p_194646_ && p_194649_.getY() < p_194646_ && p_194649_.getZ().compareTo(min) > 0 && p_194649_.getZ().compareTo(max) < 0 ? DataResult.success(p_194649_) : DataResult.error("Position out of range, expected at most " + p_194646_ + ": " + p_194649_);
        };
    }

    public static Codec<Vec3i> offsetCodec(int p_194651_) {
        return CODEC.flatXmap(checkOffsetAxes(p_194651_), checkOffsetAxes(p_194651_));
    }

    public Vec3i(long pX, int pY, long pZ) {
        this(pX >> 63, pX, pY, pZ >> 63, pZ);
    }

    public Vec3i(Int128 pX, int pY, Int128 pZ) {
        this(pX.hi(), pX.lo(), pY, pZ.hi(), pZ.lo());
    }

    /** Full-width primitive constructor; no temporary coordinate objects. */
    public Vec3i(long xHi, long xLo, int pY, long zHi, long zLo) {
        this.xHi = xHi;
        this.xLo = xLo;
        this.y = pY;
        this.zHi = zHi;
        this.zLo = zLo;
    }

    public Vec3i(double pX, double pY, double pZ) {
        this(Int128Math.doubleFloorHi(pX), Int128Math.doubleFloorLo(pX), Mth.floor(pY),
                Int128Math.doubleFloorHi(pZ), Int128Math.doubleFloorLo(pZ));
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof Vec3i)) {
            return false;
        } else {
            Vec3i vec3i = (Vec3i) pOther;
            if (this.getXHi() != vec3i.getXHi() || this.getXLo() != vec3i.getXLo()) {
                return false;
            } else if (this.getY() != vec3i.getY()) {
                return false;
            } else {
                return this.getZHi() == vec3i.getZHi() && this.getZLo() == vec3i.getZLo();
            }
        }
    }

    public int hashCode() {
        int xHash = 31 * Long.hashCode(this.xHi()) + Long.hashCode(this.xLo());
        int zHash = 31 * Long.hashCode(this.zHi()) + Long.hashCode(this.zLo());
        return (this.getY() + zHash * 31) * 31 + xHash;
    }

    public int compareTo(Vec3i pOther) {
        int result = Int128Math.compare(this.xHi(), this.xLo(), pOther.xHi(), pOther.xLo());
        if (result == 0) result = Integer.compare(this.getY(), pOther.getY());
        return result == 0 ? Int128Math.compare(this.zHi(), this.zLo(), pOther.zHi(), pOther.zLo()) : result;
    }

    /** Value-object API boundary; internal arithmetic should use the raw limb accessors. */
    public Int128 getX() {
        return Int128.of(this.xHi, this.xLo);
    }

    public int getY() {
        return this.y;
    }

    /** Value-object API boundary; internal arithmetic should use the raw limb accessors. */
    public Int128 getZ() {
        return Int128.of(this.zHi, this.zLo);
    }

    public Int128 x() { return this.getX(); }
    public int y() { return this.getY(); }
    public Int128 z() { return this.getZ(); }
    public long getXHi() { return this.xHi; }
    public long getXLo() { return this.xLo; }
    public long getZHi() { return this.zHi; }
    public long getZLo() { return this.zLo; }
    public long xHi() { return this.getXHi(); }
    public long xLo() { return this.getXLo(); }
    public long zHi() { return this.getZHi(); }
    public long zLo() { return this.getZLo(); }

    protected Vec3i setX(long pX) {
        return this.setX(pX >> 63, pX);
    }

    protected Vec3i setX(Int128 pX) {
        this.xHi = pX.hi();
        this.xLo = pX.lo();
        return this;
    }

    /** Allocation-free variant of {@link #setX(Int128)} for hot callers holding raw halves. */
    protected Vec3i setX(long pHi, long pLo) {
        this.xHi = pHi;
        this.xLo = pLo;
        return this;
    }

    protected Vec3i setY(int pY) {
        this.y = pY;
        return this;
    }

    protected Vec3i setZ(long pZ) {
        return this.setZ(pZ >> 63, pZ);
    }

    protected Vec3i setZ(Int128 pZ) {
        this.zHi = pZ.hi();
        this.zLo = pZ.lo();
        return this;
    }

    /** Allocation-free variant of {@link #setZ(Int128)} for hot callers holding raw halves. */
    protected Vec3i setZ(long pHi, long pLo) {
        this.zHi = pHi;
        this.zLo = pLo;
        return this;
    }

    public Vec3i offset(double pDx, double pDy, double pDz) {
        return pDx == 0.0D && pDy == 0.0D && pDz == 0.0D ? this : new Vec3i(
                Int128Math.addFlooredBlockOffsetHi(this.xHi(), this.xLo(), pDx),
                Int128Math.addFlooredBlockOffsetLo(this.xHi(), this.xLo(), pDx), Mth.floor((double) this.getY() + pDy),
                Int128Math.addFlooredBlockOffsetHi(this.zHi(), this.zLo(), pDz),
                Int128Math.addFlooredBlockOffsetLo(this.zHi(), this.zLo(), pDz));
    }

    public Vec3i offset(long pDx, int pDy, long pDz) {
        return this.offset(pDx >> 63, pDx, pDy, pDz >> 63, pDz);
    }

    public Vec3i offset(Vec3i pVector) {
        return this.offset(pVector.xHi(), pVector.xLo(), pVector.getY(), pVector.zHi(), pVector.zLo());
    }

    public Vec3i offset(Int128 pDx, int pDy, Int128 pDz) {
        return this.offset(pDx.hi(), pDx.lo(), pDy, pDz.hi(), pDz.lo());
    }

    private Vec3i offset(long dxHi, long dxLo, int dy, long dzHi, long dzLo) {
        return dxHi == 0L && dxLo == 0L && dy == 0 && dzHi == 0L && dzLo == 0L ? this
                : new Vec3i(Int128Math.addSaturatedHi(this.xHi(), this.xLo(), dxHi, dxLo),
                        Int128Math.addSaturatedLo(this.xHi(), this.xLo(), dxHi, dxLo), this.getY() + dy,
                        Int128Math.addSaturatedHi(this.zHi(), this.zLo(), dzHi, dzLo),
                        Int128Math.addSaturatedLo(this.zHi(), this.zLo(), dzHi, dzLo));
    }

    public Vec3i subtract(Vec3i pVector) {
        return new Vec3i(Int128Math.subtractSaturatedHi(this.xHi(), this.xLo(), pVector.xHi(), pVector.xLo()),
                Int128Math.subtractSaturatedLo(this.xHi(), this.xLo(), pVector.xHi(), pVector.xLo()), this.getY() - pVector.getY(),
                Int128Math.subtractSaturatedHi(this.zHi(), this.zLo(), pVector.zHi(), pVector.zLo()),
                Int128Math.subtractSaturatedLo(this.zHi(), this.zLo(), pVector.zHi(), pVector.zLo()));
    }

    public Vec3i multiply(int pScalar) {
        if (pScalar == 1) {
            return this;
        } else {
            return pScalar == 0 ? ZERO : new Vec3i(WorldBounds.multiplySaturated(this.getX(), pScalar), this.getY() * pScalar, WorldBounds.multiplySaturated(this.getZ(), pScalar));
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
        return new Vec3i(WorldBounds.crossComponent(pVector.getZ(), this.getY(), this.getZ(), pVector.getY()), crossY(this.getZ(), pVector.getX(), this.getX(), pVector.getZ()), WorldBounds.crossComponent(this.getX(), pVector.getY(), pVector.getX(), this.getY()));
    }

    // Keep the complete 256-bit determinant until the final vertical int clamp.
    private static int crossY(Int128 a, Int128 b, Int128 c, Int128 d) {
        long[] first = wideProduct(a, b);
        long[] second = wideProduct(c, d);
        long borrow = 0L;
        for (int i = 0; i < 4; i++) {
            long difference = first[i] - second[i];
            long nextBorrow = Long.compareUnsigned(first[i], second[i]) < 0 ? 1L : 0L;
            long result = difference - borrow;
            if (Long.compareUnsigned(difference, borrow) < 0) nextBorrow = 1L;
            first[i] = result;
            borrow = nextBorrow;
        }
        long sign = first[3] >> 63;
        if (first[3] != sign || first[2] != sign || first[1] != sign || first[0] != (long) (int) first[0]) {
            return sign < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        return (int) first[0];
    }

    private static long[] wideProduct(Int128 a, Int128 b) {
        long[] result = new long[4];
        long[] shifted = {a.lo(), a.hi(), a.hi() >> 63, a.hi() >> 63};
        for (int bit = 0; bit < 128; bit++) {
            if (b.testBit(bit)) {
                long carry = 0L;
                for (int i = 0; i < 4; i++) {
                    long operand = bit == 127 ? ~shifted[i] : shifted[i];
                    if (bit == 127 && i == 0) carry = 1L;
                    long sum = result[i] + operand;
                    long nextCarry = Long.compareUnsigned(sum, result[i]) < 0 ? 1L : 0L;
                    long total = sum + carry;
                    if (Long.compareUnsigned(total, sum) < 0) nextCarry = 1L;
                    result[i] = total;
                    carry = nextCarry;
                }
            }
            for (int i = 3; i > 0; i--) shifted[i] = (shifted[i] << 1) | (shifted[i - 1] >>> 63);
            shifted[0] <<= 1;
        }
        return result;
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
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(this.getX(), pVector.getX());
        double dy = (double) this.getY() - (double) pVector.getY();
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(this.getZ(), pVector.getZ());
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

    private static double differenceFromDouble(Int128 coordinate, double value) {
        if (!Double.isFinite(value)) {
            return -value;
        }
        if (value >= 0x1.0p127) {
            // Subtract the exact edge first: MAX is one block below 2^127.
            return WorldBounds.signedDifference(coordinate, Int128.MAX) - 1.0D - (value - 0x1.0p127);
        }
        if (value < -0x1.0p127) {
            return WorldBounds.signedDifference(coordinate, Int128.MIN) + (-0x1.0p127 - value);
        }
        return WorldBounds.signedDifference(coordinate, Int128.fromDoubleFloorSaturated(value)) - (value - Math.floor(value));
    }

    public Int128 get(Direction.Axis pAxis) {
        return pAxis == Direction.Axis.X ? this.getX() : pAxis == Direction.Axis.Z ? this.getZ() : Int128.fromLong(this.getY());
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }
}
