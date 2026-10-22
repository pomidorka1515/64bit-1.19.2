package net.minecraft.core;

/** Primitive two-long arithmetic. Hot callers keep results in their own locals. */
public final class Int128Math {
    /** Shared physics span; hard ceiling is (1L << 52) - 1 to retain fractional precision. */
    public static final long MAX_SAFE_LOCAL_SPAN = 1L << 20;

    private Int128Math() {
    }

    public static int compare(long aHi, long aLo, long bHi, long bLo) {
        int high = Long.compare(aHi, bHi);
        return high != 0 ? high : Long.compareUnsigned(aLo, bLo);
    }

    public static long addCarry(long aLo, long bLo, long resultLo) {
        return Long.compareUnsigned(resultLo, aLo) < 0 ? 1L : 0L;
    }

    public static long subtractBorrow(long aLo, long bLo) {
        return Long.compareUnsigned(aLo, bLo) < 0 ? 1L : 0L;
    }

    /** High limb of a signed, saturating 128-bit sum. Pair with {@link #addSaturatedLo}. */
    public static long addSaturatedHi(long aHi, long aLo, long bHi, long bLo) {
        long low = aLo + bLo;
        long high = aHi + bHi + addCarry(aLo, bLo, low);
        return ((aHi ^ high) & (bHi ^ high)) < 0L
                ? (aHi < 0L ? Long.MIN_VALUE : Long.MAX_VALUE) : high;
    }

    /** Low limb of the same sum; both helpers must receive the original operands. */
    public static long addSaturatedLo(long aHi, long aLo, long bHi, long bLo) {
        long low = aLo + bLo;
        long high = aHi + bHi + addCarry(aLo, bLo, low);
        return ((aHi ^ high) & (bHi ^ high)) < 0L ? (aHi < 0L ? 0L : -1L) : low;
    }

    /** High limb of a signed, saturating difference; use the original operands for both halves. */
    public static long subtractSaturatedHi(long aHi, long aLo, long bHi, long bLo) {
        long high = aHi - bHi - subtractBorrow(aLo, bLo);
        return ((aHi ^ bHi) & (aHi ^ high)) < 0L
                ? (aHi < 0L ? Long.MIN_VALUE : Long.MAX_VALUE) : high;
    }

    /** Low limb paired with {@link #subtractSaturatedHi}. */
    public static long subtractSaturatedLo(long aHi, long aLo, long bHi, long bLo) {
        long high = aHi - bHi - subtractBorrow(aLo, bLo);
        return ((aHi ^ bHi) & (aHi ^ high)) < 0L ? (aHi < 0L ? 0L : -1L) : aLo - bLo;
    }

    /** Primitive equivalent of WorldBounds.addDoubleBlockOffset, including non-finite no-ops. */
    public static long addFlooredBlockOffsetHi(long hi, long lo, double offset) {
        return !Double.isFinite(offset) || offset == 0.0D ? hi
                : addSaturatedHi(hi, lo, doubleFloorHi(offset), doubleFloorLo(offset));
    }

    public static long addFlooredBlockOffsetLo(long hi, long lo, double offset) {
        return !Double.isFinite(offset) || offset == 0.0D ? lo
                : addSaturatedLo(hi, lo, doubleFloorHi(offset), doubleFloorLo(offset));
    }

    /** resultHi already includes carry, which must be zero or one. */
    public static boolean addOverflowed(long aHi, long bHi, long carry, long resultHi) {
        return ((aHi ^ resultHi) & (bHi ^ resultHi)) < 0L;
    }

    /** resultHi already includes borrow, which must be zero or one. */
    public static boolean subtractOverflowed(long aHi, long bHi, long borrow, long resultHi) {
        return ((aHi ^ bHi) & (aHi ^ resultHi)) < 0L;
    }

    public static long rightShiftHi(long hi) {
        return rightShiftHi(hi, 4);
    }

    public static long rightShiftLo(long hi, long lo) {
        return rightShiftLo(hi, lo, 4);
    }

    public static long rightShiftHi(long hi, int bits) {
        checkShift(bits);
        return bits < 64 ? hi >> bits : hi >> 63;
    }

    /** Reads the original high half. Counts above 127 sign-fill. */
    public static long rightShiftLo(long hi, long lo, int bits) {
        checkShift(bits);
        if (bits == 0) return lo;
        if (bits < 64) return (lo >>> bits) | (hi << (64 - bits));
        return bits < 128 ? hi >> (bits - 64) : hi >> 63;
    }

    public static long leftShiftLo(long lo, int bits) {
        checkShift(bits);
        return bits < 64 ? lo << bits : 0L;
    }

    /** Wrapping high half; saturation is determined by a right-shift round trip. */
    public static long leftShiftHi(long hi, long lo, int bits) {
        checkShift(bits);
        if (bits == 0) return hi;
        if (bits < 64) return (hi << bits) | (lo >>> (64 - bits));
        return bits < 128 ? lo << (bits - 64) : 0L;
    }

    private static void checkShift(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
    }

    /** Only valid after proving that the true signed span fits the local bound. */
    public static double lowSignedDifference(long aLo, long bLo) {
        return (double) (aLo - bLo);
    }

    /** Saturates to a signed long, including differences requiring 129 signed bits. */
    public static long signedDifferenceAsLong(long aHi, long aLo, long bHi, long bLo) {
        long lo = aLo - bLo;
        long borrow = subtractBorrow(aLo, bLo);
        long hi = aHi - bHi - borrow;
        if (subtractOverflowed(aHi, bHi, borrow, hi)) {
            return aHi < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        if (hi == (lo < 0L ? -1L : 0L)) return lo;
        return hi < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    public static boolean signedDifferenceExact(long aHi, long aLo, long bHi, long bLo) {
        long difference = signedDifferenceAsLong(aHi, aLo, bHi, bLo);
        return difference >= -MAX_SAFE_LOCAL_SPAN && difference <= MAX_SAFE_LOCAL_SPAN;
    }

    /** Out-of-span values are signed long-limit sentinels for hints only. Reuse exactOut. */
    public static double signedDifference(long aHi, long aLo, long bHi, long bLo, boolean[] exactOut) {
        long difference = signedDifferenceAsLong(aHi, aLo, bHi, bLo);
        boolean exact = difference >= -MAX_SAFE_LOCAL_SPAN && difference <= MAX_SAFE_LOCAL_SPAN;
        exactOut[0] = exact;
        return exact ? (double) difference : difference < 0L ? (double) Long.MIN_VALUE : (double) Long.MAX_VALUE;
    }

    public static double signedDifference(Int128 a, Int128 b, boolean[] exactOut) {
        return signedDifference(a.hi(), a.lo(), b.hi(), b.lo(), exactOut);
    }

    public static boolean signedDifferenceExact(Int128 a, Int128 b) {
        return signedDifferenceExact(a.hi(), a.lo(), b.hi(), b.lo());
    }

    /** Nonnegative distance, saturated at Long.MAX_VALUE. */
    public static long distance(long aHi, long aLo, long bHi, long bLo) {
        return compare(aHi, aLo, bHi, bLo) >= 0
            ? signedDifferenceAsLong(aHi, aLo, bHi, bLo)
            : signedDifferenceAsLong(bHi, bLo, aHi, aLo);
    }

    public static long distancePlusOne(long aHi, long aLo, long bHi, long bLo) {
        long distance = distance(aHi, aLo, bHi, bLo);
        return distance == Long.MAX_VALUE ? distance : distance + 1L;
    }

    /** Nonnegative span product; negative operands are caller errors. */
    public static long safeProduct(long first, long second) {
        if (first < 0L || second < 0L) throw new IllegalArgumentException("Negative span");
        return second != 0L && first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    /**
     * Raw high half of {@link Int128#fromDoubleFloorSaturated(double)}. Hot callers
     * keep results in their own locals; the low half comes from {@link #doubleFloorLo(double)}.
     */
    public static long doubleFloorHi(double value) {
        return doubleFloor(value, true);
    }

    /** Raw low half of {@link Int128#fromDoubleFloorSaturated(double)}; see {@link #doubleFloorHi(double)}. */
    public static long doubleFloorLo(double value) {
        return doubleFloor(value, false);
    }

    /** Mirrors {@link Int128#fromDoubleFloorSaturated(double)} without allocation. */
    private static long doubleFloor(double value, boolean high) {
        if (Double.isNaN(value)) {
            return 0L;
        }
        if (value >= 0x1p127) {
            return high ? Long.MAX_VALUE : -1L;
        }
        if (value <= -0x1p127) {
            return high ? Long.MIN_VALUE : 0L;
        }
        if (value >= -0x1p63 && value < 0x1p63) {
            // The long-range cast truncates toward zero and is then corrected; it is not the floor.
            long truncated = (long) value;
            long floored = value < truncated ? truncated - 1L : truncated;
            return high ? (floored < 0L ? -1L : 0L) : floored;
        }
        // |value| >= 2^63 and |value| < 2^127, so the double is already an integer.
        long bits = Double.doubleToRawLongBits(value);
        int shift = (int) ((bits >>> 52) & 0x7FFL) - 1075;
        long significand = (bits & 0xFFFFFFFFFFFFFL) | 0x10000000000000L;
        long magnitudeHi;
        long magnitudeLo;
        if (shift >= 64) {
            magnitudeHi = significand << (shift - 64);
            magnitudeLo = 0L;
        } else {
            magnitudeHi = significand >>> (64 - shift);
            magnitudeLo = significand << shift;
        }
        if (bits < 0L) {
            // Two's complement negate of the exact magnitude.
            long low = -magnitudeLo;
            long top = ~magnitudeHi + (magnitudeLo == 0L ? 1L : 0L);
            return high ? top : low;
        }
        return high ? magnitudeHi : magnitudeLo;
    }

    /** Cold boundary returning a value object. Floors before saturated addition. */
    public static Int128 addDoubleBlockOffset(long hi, long lo, double offset) {
        if (Double.isNaN(offset)) return Int128.of(hi, lo);
        if (offset >= 0x1.0p128) return Int128.MAX;
        if (offset <= -0x1.0p128) return Int128.MIN;
        // A delta outside the signed domain can still cancel the original coordinate.
        if (offset >= 0x1.0p127) {
            Int128 remainder = Int128.fromDoubleFloorSaturated(offset - 0x1.0p127);
            if (hi >= 0L) return Int128.MAX;
            return Int128.of(hi ^ Long.MIN_VALUE, lo).addSaturated(remainder);
        }
        if (offset < -0x1.0p127) {
            Int128 remainder = Int128.fromDoubleFloorSaturated(offset + 0x1.0p127);
            if (hi < 0L) return Int128.MIN;
            return Int128.of(hi ^ Long.MIN_VALUE, lo).addSaturated(remainder);
        }
        return Int128.of(hi, lo).addSaturated(Int128.fromDoubleFloorSaturated(offset));
    }
}
