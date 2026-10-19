package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;
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

    /** Adds modulo {@code 2^128}. */
    public Int128 add(Int128 other) {
        long low = this.lo + other.lo;
        long carry = Long.compareUnsigned(low, this.lo) < 0 ? 1L : 0L;
        return new Int128(this.hi + other.hi + carry, low);
    }

    /** Subtracts modulo {@code 2^128}. */
    public Int128 subtract(Int128 other) {
        long low = this.lo - other.lo;
        long borrow = Long.compareUnsigned(this.lo, other.lo) < 0 ? 1L : 0L;
        return new Int128(this.hi - other.hi - borrow, low);
    }

    public Int128 addSaturated(Int128 other) {
        Int128 result = this.add(other);
        return ((this.hi ^ result.hi) & (other.hi ^ result.hi)) < 0L
            ? (this.hi < 0L ? MIN : MAX) : result;
    }

    public Int128 subtractSaturated(Int128 other) {
        Int128 result = this.subtract(other);
        return ((this.hi ^ other.hi) & (this.hi ^ result.hi)) < 0L
            ? (this.hi < 0L ? MIN : MAX) : result;
    }

    /** Negates with saturation: {@code MIN} becomes {@code MAX}. */
    public Int128 negate() {
        if (this.hi == Long.MIN_VALUE && this.lo == 0L) {
            return MAX;
        }
        return new Int128(~this.hi + (this.lo == 0L ? 1L : 0L), -this.lo);
    }

    public Int128 absSaturated() {
        return this.hi < 0L ? this.negate() : this;
    }

    /** Arithmetic right shift (floor division). Counts above 127 sign-fill both halves. */
    public Int128 shiftRight(int bits) {
        if (bits < 0) {
            throw new IllegalArgumentException("Negative shift count: " + bits);
        }
        if (bits == 0) {
            return this;
        }
        if (bits < 64) {
            return new Int128(this.hi >> bits, (this.lo >>> bits) | (this.hi << (64 - bits)));
        }
        return new Int128(this.hi >> 63, bits < 128 ? this.hi >> (bits - 64) : this.hi >> 63);
    }

    /** Multiplies by {@code 2^bits}, saturating toward the original sign on overflow. */
    public Int128 shiftLeftSaturated(int bits) {
        if (bits < 0) {
            throw new IllegalArgumentException("Negative shift count: " + bits);
        }
        if (bits == 0) {
            return this;
        }
        if (bits >= 128) {
            return this.hi == 0L && this.lo == 0L ? ZERO : (this.hi < 0L ? MIN : MAX);
        }
        long low = bits < 64 ? this.lo << bits : 0L;
        long high = bits < 64 ? (this.hi << bits) | (this.lo >>> (64 - bits)) : this.lo << (bits - 64);
        Int128 result = new Int128(high, low);
        return result.shiftRight(bits).equals(this) ? result : (this.hi < 0L ? MIN : MAX);
    }

    public Int128 and(Int128 other) {
        return new Int128(this.hi & other.hi, this.lo & other.lo);
    }

    public Int128 or(Int128 other) {
        return new Int128(this.hi | other.hi, this.lo | other.lo);
    }

    public Int128 xor(Int128 other) {
        return new Int128(this.hi ^ other.hi, this.lo ^ other.lo);
    }

    public Int128 not() {
        return new Int128(~this.hi, ~this.lo);
    }

    /** Tests a bit numbered from 0 (least significant) through 127 (sign). */
    public boolean testBit(int bit) {
        if (bit < 0 || bit >= 128) {
            throw new IndexOutOfBoundsException("Bit index: " + bit);
        }
        return bit < 64 ? (this.lo & (1L << bit)) != 0L : (this.hi & (1L << (bit - 64))) != 0L;
    }

    /** Signed multiplication, clamped to the 128-bit range; no big-integer allocation. */
    public Int128 multiplySaturated(long factor) {
        boolean negative = (this.hi < 0L) != (factor < 0L);
        // Unsigned magnitudes preserve MIN and Long.MIN_VALUE without saturating them.
        long low = this.hi < 0L ? -this.lo : this.lo;
        long high = this.hi < 0L ? ~this.hi + (this.lo == 0L ? 1L : 0L) : this.hi;
        long magnitude = factor < 0L ? -factor : factor;
        long resultLow = low * magnitude;
        long carry = unsignedMultiplyHigh(low, magnitude);
        long middle = high * magnitude;
        long resultHigh = middle + carry;
        long top = unsignedMultiplyHigh(high, magnitude) + (Long.compareUnsigned(resultHigh, middle) < 0 ? 1L : 0L);
        boolean overflow = top != 0L || resultHigh < 0L
            && !(negative && resultHigh == Long.MIN_VALUE && resultLow == 0L);
        if (overflow) {
            return negative ? MIN : MAX;
        }
        return negative ? new Int128(~resultHigh + (resultLow == 0L ? 1L : 0L), -resultLow)
            : new Int128(resultHigh, resultLow);
    }

    private static long unsignedMultiplyHigh(long a, long b) {
        return Math.multiplyHigh(a, b) + (a < 0L ? b : 0L) + (b < 0L ? a : 0L);
    }

    /**
     * Floors toward negative infinity and saturates into the signed 128-bit range.
     * {@code NaN} becomes {@code 0}. Infinities and magnitudes at {@code ±2^127} saturate.
     * The long-range cast truncates toward zero and is then corrected; it is not the floor.
     */
    public static Int128 fromDoubleFloorSaturated(double value) {
        if (Double.isNaN(value)) {
            return ZERO;
        }
        if (value >= 0x1p127) {
            return MAX;
        }
        if (value <= -0x1p127) {
            return MIN;
        }
        if (value >= -0x1p63 && value < 0x1p63) {
            long truncated = (long) value;
            return fromLong(value < truncated ? truncated - 1L : truncated);
        }
        return fromHugeFiniteDouble(value);
    }

    /**
     * Lossy monotone projection into {@code [-2^127, 2^127]}. Never {@code NaN} or infinite.
     *
     * <p>{@code MIN} is exact. {@code MAX} ({@code 2^127 - 1}) rounds up to {@code 2^127}.
     * The top {@code 2^73} values, {@code [2^127 - 2^73, MAX]}, collapse to {@code 2^127},
     * and {@code [MIN, MIN + 2^73]} collapses to {@code -2^127}. Do not use this for bounds
     * tests, equality, hashing, persistence, or round-tripping. Debug, F3, and render or
     * audio hints only.</p>
     */
    public double toDoubleSaturated() {
        if (this.hi == Long.MIN_VALUE && this.lo == 0L) {
            return -0x1p127;
        }
        boolean negative = this.hi < 0L;
        long magHi = negative ? ~this.hi + (this.lo == 0L ? 1L : 0L) : this.hi;
        long magLo = negative ? -this.lo : this.lo;
        double magnitude = unsignedToDouble(magHi, magLo);
        return negative ? -magnitude : magnitude;
    }

    /** Low 64 bits, without sign extension. */
    public long toLongLow() {
        return this.lo;
    }

    /** Decimal form. Cold path; not for hot loops. */
    public String toDecimalString() {
        return toDecimal(this.hi, this.lo);
    }

    /**
     * Parses a base-10 integer. Cold path. Rejects surrounding whitespace and values outside
     * {@code [MIN, MAX]}.
     */
    public static Int128 parseDecimal(String text) {
        if (text == null || text.isEmpty()) {
            throw new NumberFormatException("empty decimal");
        }
        int index = 0;
        boolean negative = false;
        char sign = text.charAt(0);
        if (sign == '+' || sign == '-') {
            negative = sign == '-';
            if (++index == text.length()) {
                throw new NumberFormatException(text);
            }
        }
        long hi = 0L;
        long lo = 0L;
        for (; index < text.length(); ++index) {
            char c = text.charAt(index);
            if (c < '0' || c > '9') {
                throw new NumberFormatException(text);
            }
            int digit = c - '0';
            if (hi < 0L) {
                throw new NumberFormatException(text);
            }
            long productLo = lo * 10L;
            long loHigh = unsignedMultiplyHigh(lo, 10L);
            long hiLow = hi * 10L;
            long hiHigh = unsignedMultiplyHigh(hi, 10L);
            long nextLo = productLo + digit;
            long carry = Long.compareUnsigned(nextLo, productLo) < 0 ? 1L : 0L;
            long mid = loHigh + hiLow;
            long midCarry = Long.compareUnsigned(mid, loHigh) < 0 ? 1L : 0L;
            long nextHi = mid + carry;
            long top = hiHigh + midCarry + (Long.compareUnsigned(nextHi, mid) < 0 ? 1L : 0L);
            if (top != 0L || nextHi < 0L && !(nextHi == Long.MIN_VALUE && nextLo == 0L)) {
                throw new NumberFormatException(text);
            }
            hi = nextHi;
            lo = nextLo;
        }
        if (hi == Long.MIN_VALUE) {
            if (!negative) {
                throw new NumberFormatException(text);
            }
            return MIN;
        }
        if (!negative || hi == 0L && lo == 0L) {
            return new Int128(hi, lo);
        }
        return new Int128(~hi + (lo == 0L ? 1L : 0L), -lo);
    }

    /** 32 lowercase hex characters, high half first. Cold path. */
    public String toHex() {
        char[] chars = new char[32];
        writeHex(chars, 0, this.hi);
        writeHex(chars, 16, this.lo);
        return new String(chars);
    }

    /** Inverse of {@link #toHex()}. Exactly 32 hex digits; either case. Cold path. */
    public static Int128 fromHex(String text) {
        if (text == null || text.length() != 32) {
            throw new NumberFormatException("hex width");
        }
        return of(parseHexHalf(text, 0), parseHexHalf(text, 16));
    }

    /** Map hash: {@code mix(hi) ^ rotateLeft(mix(lo), 32)}. */
    public long hash() {
        return hash(this.hi, this.lo);
    }

    /** Allocation-free form of {@link #hash()}. */
    public static long hash(long hi, long lo) {
        return mix(hi) ^ Long.rotateLeft(mix(lo), 32);
    }

    /** fastutil finalizer. Sequential keys must not land in adjacent map slots. */
    public static long mix(long value) {
        return HashCommon.mix(value);
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
        return this.toDecimalString();
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

    /** {@code |value| >= 2^63} and {@code |value| < 2^127}, so the double is already an integer. */
    private static Int128 fromHugeFiniteDouble(double value) {
        long bits = Double.doubleToRawLongBits(value);
        int shift = (int) ((bits >>> 52) & 0x7FFL) - 1075;
        long significand = (bits & 0xFFFFFFFFFFFFFL) | 0x10000000000000L;
        long magHi;
        long magLo;
        if (shift >= 64) {
            magHi = significand << (shift - 64);
            magLo = 0L;
        } else {
            magHi = significand >>> (64 - shift);
            magLo = significand << shift;
        }
        return bits < 0L ? new Int128(~magHi + (magLo == 0L ? 1L : 0L), -magLo) : new Int128(magHi, magLo);
    }

    private static double unsignedToDouble(long hi, long lo) {
        if (hi == 0L && lo >= 0L) {
            return (double) lo;
        }
        int bitLength = hi == 0L ? 64 - Long.numberOfLeadingZeros(lo) : 128 - Long.numberOfLeadingZeros(hi);
        int shift = bitLength - 53;
        long significand = extractBits(hi, lo, shift, 53);
        if (roundUp(hi, lo, shift, significand)) {
            ++significand;
        }
        int exponent = bitLength - 1;
        if (significand == 1L << 53) {
            significand >>>= 1;
            ++exponent;
        }
        long encoded = ((long) (exponent + 1023) << 52) | (significand & 0xFFFFFFFFFFFFFL);
        return Double.longBitsToDouble(encoded);
    }

    private static boolean roundUp(long hi, long lo, int shift, long significand) {
        if (!bit(hi, lo, shift - 1)) {
            return false;
        }
        return bitsBelow(hi, lo, shift - 1) || (significand & 1L) != 0L;
    }

    private static long extractBits(long hi, long lo, int lowBit, int count) {
        if (lowBit >= 64) {
            return (hi >>> (lowBit - 64)) & lowMask(count);
        }
        int lowCount = 64 - lowBit;
        if (count <= lowCount) {
            return (lo >>> lowBit) & lowMask(count);
        }
        return (lo >>> lowBit) | ((hi & lowMask(count - lowCount)) << lowCount);
    }

    private static boolean bit(long hi, long lo, int index) {
        return index < 64 ? (lo & (1L << index)) != 0L : (hi & (1L << (index - 64))) != 0L;
    }

    private static boolean bitsBelow(long hi, long lo, int index) {
        if (index <= 0) {
            return false;
        }
        if (index <= 64) {
            return (lo & lowMask(index)) != 0L;
        }
        return lo != 0L || (hi & lowMask(index - 64)) != 0L;
    }

    private static long lowMask(int bits) {
        return bits >= 64 ? -1L : (1L << bits) - 1L;
    }

    private static void writeHex(char[] chars, int offset, long value) {
        for (int i = 15; i >= 0; --i) {
            int digit = (int) (value & 0xFL);
            chars[offset + i] = (char) (digit < 10 ? '0' + digit : 'a' + digit - 10);
            value >>>= 4;
        }
    }

    private static long parseHexHalf(String text, int offset) {
        long value = 0L;
        for (int i = 0; i < 16; ++i) {
            int digit = Character.digit(text.charAt(offset + i), 16);
            if (digit < 0) {
                throw new NumberFormatException(text);
            }
            value = (value << 4) | digit;
        }
        return value;
    }
}
