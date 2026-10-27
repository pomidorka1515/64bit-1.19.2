package net.minecraft.core;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/** Immutable signed 1088-bit coordinate value, with little-endian significant limbs.
 * Allocate values at identity/API boundaries; use {@link Int1088Math} with caller-owned
 * buffers inside hot loops. World-facing arithmetic must use the saturating variants.
 */
@Immutable
public final class Int1088 implements Comparable<Int1088> {
    public static final int LIMBS = 17;
    public static final int BITS = 1088;
    public static final int SIGN_BIT = 1087;
    public static final Int1088 ZERO = new Int1088(new long[]{0});
    public static final Int1088 ONE = new Int1088(new long[]{1});
    public static final Int1088 MAX;
    public static final Int1088 MIN;

    static {
        long[] max = new long[LIMBS];
        Arrays.fill(max, -1L);
        max[LIMBS - 1] = Long.MAX_VALUE;
        MAX = new Int1088(max);
        long[] min = new long[LIMBS];
        min[LIMBS - 1] = Long.MIN_VALUE;
        MIN = new Int1088(min);
    }

    private final long[] limbs;

    private Int1088(long[] ownedCanonicalLimbs) {
        this.limbs = ownedCanonicalLimbs;
    }

    public static Int1088 of(long... limbs) {
        return fromLimbs(limbs, 0, limbs.length);
    }

    public static Int1088 fromLong(long value) {
        if (value == 0) return ZERO;
        if (value == 1) return ONE;
        return new Int1088(new long[]{value});
    }

    public static Int1088 fromUnsignedLong(long value) {
        return value < 0 ? of(value, 0) : fromLong(value);
    }

    public static Int1088 fromLimbs(long[] src, int off, int len) {
        if (len < 1 || len > LIMBS) throw new IllegalArgumentException("Int1088 requires 1..17 limbs");
        int canonical = Int1088Math.canonicalLength(src, off, len);
        return new Int1088(Arrays.copyOfRange(src, off, off + canonical));
    }

    public static Int1088 fromFixedLimbs(long[] limbs17) {
        if (limbs17.length != LIMBS) throw new IllegalArgumentException("Int1088 requires exactly 17 limbs");
        return fromLimbs(limbs17, 0, LIMBS);
    }

    private static Int1088 result(long[] words, int length) {
        return new Int1088(Arrays.copyOf(words, length));
    }

    public int limbCount() {
        return this.limbs.length;
    }

    public long limb(int index) {
        Objects.checkIndex(index, LIMBS);
        return index < this.limbs.length ? this.limbs[index] : this.limbs[this.limbs.length - 1] >> 63;
    }

    public boolean fitsLong() {
        return this.limbs.length == 1;
    }

    /** Number of bits in the unsigned absolute magnitude (MIN has bit length 1088). */
    public int bitLength() {
        return Int1088Math.magnitudeBits(this.limbs, 0, this.limbs.length);
    }

    public int signum() {
        return this.limbs[this.limbs.length - 1] < 0 ? -1 : this.fitsLong() && this.limbs[0] == 0 ? 0 : 1;
    }

    /** Low word only; never use this as the identity of a wide coordinate. */
    public long lowLimb() {
        return this.limbs[0];
    }

    public Int1088 copy() {
        return new Int1088(this.limbs.clone());
    }

    public void copyFixed(long[] dst, int offset) {
        Objects.checkFromIndexSize(offset, LIMBS, dst.length);
        for (int i = 0; i < LIMBS; ++i) dst[offset + i] = this.limb(i);
    }

    /** Internal wrapping arithmetic modulo 2^1088; use addSaturated for coordinates. */
    public Int1088 add(Int1088 other) {
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.add(this.limbs, 0, this.limbCount(), other.limbs, 0, other.limbCount(), out, 0));
    }

    public Int1088 addSaturated(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) {
            long a = this.lowLimb(), b = other.lowLimb(), r = a + b;
            if (((a ^ r) & (b ^ r)) >= 0) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.addSaturated(this.limbs, 0, this.limbCount(), other.limbs, 0, other.limbCount(), out, 0));
    }

    /** Internal wrapping arithmetic modulo 2^1088. */
    public Int1088 subtract(Int1088 other) {
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.subtract(this.limbs, 0, this.limbCount(), other.limbs, 0, other.limbCount(), out, 0));
    }

    public Int1088 subtractSaturated(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) {
            long a = this.lowLimb(), b = other.lowLimb(), r = a - b;
            if (((a ^ b) & (a ^ r)) >= 0) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.subtractSaturated(this.limbs, 0, this.limbCount(), other.limbs, 0, other.limbCount(), out, 0));
    }

    public Int1088 negate() {
        if (this.fitsLong() && this.lowLimb() != Long.MIN_VALUE) return fromLong(-this.lowLimb());
        return ZERO.subtractSaturated(this);
    }

    public Int1088 absSaturated() {
        return this.signum() < 0 ? this.negate() : this;
    }

    public Int1088 shiftRight(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
        if (bits == 0) return this;
        if (this.fitsLong()) return fromLong(bits >= 64 ? this.lowLimb() >> 63 : this.lowLimb() >> bits);
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.shiftRight(this.limbs, 0, this.limbCount(), bits, out, 0));
    }

    public Int1088 shiftLeftSaturated(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
        if (bits == 0) return this;
        if (this.fitsLong() && bits < 64) {
            long r = this.lowLimb() << bits;
            if ((r >> bits) == this.lowLimb()) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.shiftLeftSaturated(this.limbs, 0, this.limbCount(), bits, out, 0));
    }

    /** Logical shift of the full 1088-bit pattern, for magnitude helpers only. */
    public Int1088 shiftRightUnsigned(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
        if (bits == 0) return this;
        if (bits >= BITS) return ZERO;
        long[] out = new long[LIMBS];
        int words = bits >>> 6, shift = bits & 63;
        for (int i = 0; i + words < LIMBS; ++i) {
            long lo = this.limb(i + words);
            long hi = i + words + 1 < LIMBS ? this.limb(i + words + 1) : 0;
            out[i] = shift == 0 ? lo : (lo >>> shift) | (hi << (64 - shift));
        }
        return fromFixedLimbs(out);
    }

    private Int1088 bitwise(Int1088 other, int operation) {
        int length = Math.max(this.limbCount(), other.limbCount());
        long[] out = new long[length];
        for (int i = 0; i < length; ++i) {
            long a = this.limb(i), b = other.limb(i);
            out[i] = operation == 0 ? a & b : operation == 1 ? a | b : a ^ b;
        }
        return fromLimbs(out, 0, length);
    }

    public Int1088 and(Int1088 other) {
        return this.bitwise(other, 0);
    }

    public Int1088 or(Int1088 other) {
        return this.bitwise(other, 1);
    }

    public Int1088 xor(Int1088 other) {
        return this.bitwise(other, 2);
    }

    public Int1088 not() {
        long[] out = new long[this.limbCount()];
        for (int i = 0; i < out.length; ++i) out[i] = ~this.limbs[i];
        return new Int1088(out);
    }

    public boolean testBit(int bit) {
        Objects.checkIndex(bit, BITS);
        return (this.limb(bit >>> 6) & (1L << (bit & 63))) != 0;
    }

    public Int1088 multiplySaturated(int factor) {
        return this.multiplySaturated((long) factor);
    }

    public Int1088 multiplySaturated(long factor) {
        if (this.fitsLong()) {
            long a = this.lowLimb(), r = a * factor;
            if (Math.multiplyHigh(a, factor) == (r >> 63)) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.multiplySaturated(this.limbs, 0, this.limbCount(), factor, out, 0));
    }

    public static Int1088 fromDoubleFloorSaturated(double value) {
        if (Double.isNaN(value)) return ZERO;
        if (value >= -0x1p63 && value < 0x1p63) {
            long truncated = (long) value;
            return fromLong(value < truncated ? truncated - 1 : truncated);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.fromDoubleFloor(value, out, 0));
    }

    /** Lossy, ties-to-even projection, clamped to finite double range. */
    public double toDoubleRounded() {
        return Int1088Math.toDoubleRounded(this.limbs, 0, this.limbCount());
    }

    public double toDoubleSaturated() {
        return Int1088Math.toDoubleSaturated(this.limbs, 0, this.limbCount());
    }

    public float toFloatRounded() {
        return Int1088Math.toFloatRounded(this.limbs, 0, this.limbCount());
    }

    public Int1088 floorDivideSaturated(int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        if (this.fitsLong()) return fromLong(Math.floorDiv(this.lowLimb(), (long) divisor));
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.floorDivide(this.limbs, 0, this.limbCount(), divisor, out, 0));
    }

    /** Cold signed floor division. Zero is rejected; MIN / -1 saturates to MAX. */
    public Int1088 floorDivideSaturated(Int1088 divisor) {
        if (divisor.signum() == 0) throw new ArithmeticException("Division by zero");
        boolean negative = this.signum() != 0 && this.signum() != divisor.signum();
        long[] dividend = this.magnitude(), denominator = divisor.magnitude();
        long[] quotient = new long[LIMBS], rem = new long[LIMBS];
        for (int bit = BITS - 1; bit >= 0; --bit) {
            long carry = (dividend[bit >>> 6] >>> (bit & 63)) & 1;
            for (int i = 0; i < LIMBS; ++i) {
                long next = rem[i] >>> 63;
                rem[i] = (rem[i] << 1) | carry;
                carry = next;
            }
            int comparison = 0;
            for (int i = LIMBS - 1; comparison == 0 && i >= 0; --i) comparison = Long.compareUnsigned(rem[i], denominator[i]);
            if (comparison >= 0) {
                long borrow = 0;
                for (int i = 0; i < LIMBS; ++i) {
                    long a = rem[i], b = denominator[i], difference = a - b;
                    rem[i] = difference - borrow;
                    borrow = Long.compareUnsigned(a, b) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
                }
                quotient[bit >>> 6] |= 1L << (bit & 63);
            }
        }
        boolean nonzeroRemainder = false;
        for (long w : rem) nonzeroRemainder |= w != 0;
        if (negative) {
            if (nonzeroRemainder) for (int i = 0; i < LIMBS; ++i) if (++quotient[i] != 0) break;
            Int1088Math.negateFixed(quotient, 0);
        } else if (quotient[LIMBS - 1] < 0) {
            return MAX;
        }
        return fromFixedLimbs(quotient);
    }

    public int remainder(int divisor) {
        return Int1088Math.remainder(this.limbs, 0, this.limbCount(), divisor);
    }

    private long[] magnitude() {
        long[] out = new long[LIMBS];
        this.copyFixed(out, 0);
        if (this.signum() < 0) Int1088Math.negateFixed(out, 0);
        return out;
    }

    public static Int1088 parseDecimal(String text) {
        if (text == null || text.isEmpty()) throw new NumberFormatException("Empty Int1088 decimal");
        boolean negative = text.charAt(0) == '-';
        int index = negative || text.charAt(0) == '+' ? 1 : 0;
        if (index == text.length()) throw new NumberFormatException("Missing decimal digits");
        long[] magnitude = new long[LIMBS], product = new long[LIMBS + 1];
        while (index < text.length()) {
            int end = index + Math.min(9, text.length() - index);
            long chunk = 0, scale = 1;
            while (index < end) {
                char c = text.charAt(index++);
                if (c < '0' || c > '9') throw new NumberFormatException("Invalid Int1088 decimal digit");
                chunk = chunk * 10 + c - '0';
                scale *= 10;
            }
            Int1088Math.multiplyUnsigned(magnitude, 0, LIMBS, scale, product, 0);
            long carry = chunk;
            for (int i = 0; i <= LIMBS; ++i) {
                long a = product[i], r = a + carry;
                product[i] = r;
                carry = Long.compareUnsigned(r, a) < 0 ? 1 : 0;
            }
            boolean lowerZero = true;
            for (int i = 0; i < LIMBS - 1; ++i) lowerZero &= product[i] == 0;
            long top = product[LIMBS - 1];
            if (carry != 0 || product[LIMBS] != 0 || top < 0 && !(negative && top == Long.MIN_VALUE && lowerZero)) {
                throw new NumberFormatException("Int1088 decimal outside signed 1088-bit domain");
            }
            System.arraycopy(product, 0, magnitude, 0, LIMBS);
        }
        if (negative) Int1088Math.negateFixed(magnitude, 0);
        return fromFixedLimbs(magnitude);
    }

    public String toDecimalString() {
        if (this.fitsLong()) return Long.toString(this.lowLimb());
        long[] magnitude = this.magnitude();
        int[] chunks = new int[37];
        int count = 0;
        boolean nonzero;
        do {
            chunks[count++] = Int1088Math.divideMagnitude(magnitude, 0, 1000000000);
            nonzero = false;
            for (long w : magnitude) nonzero |= w != 0;
        } while (nonzero);
        StringBuilder text = new StringBuilder(329);
        if (this.signum() < 0) text.append('-');
        text.append(chunks[--count]);
        while (count > 0) {
            String chunk = Integer.toString(chunks[--count]);
            for (int i = chunk.length(); i < 9; ++i) text.append('0');
            text.append(chunk);
        }
        return text.toString();
    }

    public static Int1088 parseHex(String text) {
        if (text == null || text.length() != BITS / 4) throw new NumberFormatException("Int1088 hex requires 272 digits");
        long[] out = new long[LIMBS];
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            int digit = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) throw new NumberFormatException("Invalid Int1088 hex digit");
            int word = LIMBS - 1 - i / 16;
            out[word] = (out[word] << 4) | digit;
        }
        return fromFixedLimbs(out);
    }

    public String toHex() {
        char[] text = new char[BITS / 4];
        String digits = "0123456789abcdef";
        for (int i = 0; i < text.length; ++i) text[i] = digits.charAt((int) (this.limb(LIMBS - 1 - i / 16) >>> ((15 - i % 16) * 4)) & 15);
        return new String(text);
    }

    @Override
    public int compareTo(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) return Long.compare(this.lowLimb(), other.lowLimb());
        return Int1088Math.compare(this.limbs, 0, this.limbCount(), other.limbs, 0, other.limbCount());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Int1088 && Arrays.equals(this.limbs, ((Int1088) other).limbs);
    }

    public long hash() {
        return Int1088Math.hash(this.limbs, 0, this.limbCount());
    }

    public int hashXZ() {
        return Int1088Math.hashXZ(this.limbs, 0, this.limbCount());
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.hash());
    }

    @Override
    public String toString() {
        return this.toDecimalString();
    }
}
