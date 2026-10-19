package net.minecraft.core;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable signed 128-bit two's-complement value.
 *
 * <p>The high half is a signed {@code long}. The low half is an unsigned
 * magnitude in the same {@code long}. Allocating one at an API boundary is
 * fine. Do not allocate one inside block iteration, collision, pathfinding,
 * lighting, or chunk scheduling.</p>
 */
@Immutable
public final class Int128 implements Comparable<Int128> {
    public static final Int128 ZERO = new Int128(0L, 0L);
    public static final Int128 ONE = new Int128(0L, 1L);
    /** {@code 2^127 - 1}. */
    public static final Int128 MAX = new Int128(Long.MAX_VALUE, -1L);
    /** {@code -2^127}. */
    public static final Int128 MIN = new Int128(Long.MIN_VALUE, 0L);

    private final long hi;
    private final long lo;

    private Int128(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    public static Int128 of(long hi, long lo) {
        return new Int128(hi, lo);
    }

    /** Sign-extends a Java {@code long} into 128 bits. */
    public static Int128 fromLong(long value) {
        return new Int128(value < 0L ? -1L : 0L, value);
    }

    /** Low 64 bits as an unsigned magnitude. A negative Java long is not sign-extended. */
    public static Int128 fromUnsignedLong(long value) {
        return new Int128(0L, value);
    }

    public long hi() {
        return this.hi;
    }

    public long lo() {
        return this.lo;
    }

    /** Signed high half, then unsigned low half. */
    @Override
    public int compareTo(Int128 other) {
        int high = Long.compare(this.hi, other.hi);
        return high != 0 ? high : Long.compareUnsigned(this.lo, other.lo);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Int128)) {
            return false;
        }
        Int128 that = (Int128) other;
        return this.hi == that.hi && this.lo == that.lo;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(this.hi) + Long.hashCode(this.lo);
    }

    /** Decimal form. Cold path; not for hot loops. */
    @Override
    public String toString() {
        return toDecimal(this.hi, this.lo);
    }

    private static String toDecimal(long hi, long lo) {
        if (hi == 0L) {
            return Long.toUnsignedString(lo);
        }
        if (hi == -1L && lo < 0L) {
            return Long.toString(lo);
        }
        boolean negative = hi < 0L;
        if (negative && !(hi == Long.MIN_VALUE && lo == 0L)) {
            long carry = lo == 0L ? 1L : 0L;
            lo = -lo;
            hi = ~hi + carry;
        }
        String magnitude = unsignedDecimal(hi, lo);
        return negative ? "-" + magnitude : magnitude;
    }

    private static String unsignedDecimal(long hi, long lo) {
        if (hi == 0L) {
            return Long.toUnsignedString(lo);
        }
        char[] digits = new char[40];
        int pos = digits.length;
        long[] quotient = new long[2];
        while (hi != 0L || Long.compareUnsigned(lo, 10L) >= 0) {
            long remainder = unsignedDivide10(hi, lo, quotient);
            hi = quotient[0];
            lo = quotient[1];
            digits[--pos] = (char) ('0' + remainder);
        }
        digits[--pos] = (char) ('0' + lo);
        return new String(digits, pos, digits.length - pos);
    }

    /** Divides an unsigned 128-bit pair by 10. Writes the quotient and returns the remainder. */
    private static long unsignedDivide10(long hi, long lo, long[] quotient) {
        long quotientHi = Long.divideUnsigned(hi, 10L);
        long remainderHi = Long.remainderUnsigned(hi, 10L);
        long acc = (remainderHi << 32) | (lo >>> 32);
        long q1 = acc / 10L;
        long remainder = acc - q1 * 10L;
        acc = (remainder << 32) | (lo & 0xFFFFFFFFL);
        long q0 = acc / 10L;
        remainder = acc - q0 * 10L;
        quotient[0] = quotientHi;
        quotient[1] = (q1 << 32) | q0;
        return remainder;
    }
}
