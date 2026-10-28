package net.minecraft.core;

import com.mojang.serialization.Codec;
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
    public static final Codec<Int1088> CODEC = Int1088Codec.CODEC;
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

    private final byte length;
    private final long l0;
    private final long l1;
    // Wide values retain a canonical primitive view for the arithmetic layer.
    private final long[] rest;
    private volatile long cachedHash;

    private Int1088(long[] ownedCanonicalLimbs) {
        this.length = (byte) ownedCanonicalLimbs.length;
        this.l0 = ownedCanonicalLimbs[0];
        this.l1 = this.length > 1 ? ownedCanonicalLimbs[1] : this.l0 >> 63;
        this.rest = this.length > 2 ? ownedCanonicalLimbs : null;
    }

    private Int1088(long value) {
        this.length = 1;
        this.l0 = value;
        this.l1 = value >> 63;
        this.rest = null;
    }

    private Int1088(long l0, long l1) {
        this.length = 2;
        this.l0 = l0;
        this.l1 = l1;
        this.rest = null;
    }

    private long[] words() {
        return this.rest != null ? this.rest : this.length == 1 ? new long[]{this.l0} : new long[]{this.l0, this.l1};
    }

    public static Int1088 of(long... limbs) {
        return fromLimbs(limbs, 0, limbs.length);
    }

    public static Int1088 fromLong(long value) {
        if (value == 0) return ZERO;
        if (value == 1) return ONE;
        return new Int1088(value);
    }

    public static Int1088 fromUnsignedLong(long value) {
        return value < 0 ? of(value, 0) : fromLong(value);
    }

    public static Int1088 fromLimbs(long[] src, int off, int len) {
        if (len < 1 || len > LIMBS) throw new IllegalArgumentException("Int1088 requires 1..17 limbs");
        int canonical = Int1088Math.canonicalLength(src, off, len);
        return canonical == 1 ? fromLong(src[off]) : canonical == 2 ? new Int1088(src[off], src[off + 1])
                : new Int1088(Arrays.copyOfRange(src, off, off + canonical));
    }

    public static Int1088 fromFixedLimbs(long[] limbs17) {
        if (limbs17.length != LIMBS) throw new IllegalArgumentException("Int1088 requires exactly 17 limbs");
        return fromLimbs(limbs17, 0, LIMBS);
    }

    private static Int1088 result(long[] words, int length) {
        return fromLimbs(words, 0, length);
    }

    /** Takes ownership of a newly allocated result, avoiding a second wide-array copy. */
    private static Int1088 ownedResult(long[] words) {
        int length = Int1088Math.canonicalLength(words, 0, words.length);
        if (length == 1) return fromLong(words[0]);
        if (length == 2) return new Int1088(words[0], words[1]);
        return new Int1088(length == words.length ? words : Arrays.copyOf(words, length));
    }

    public int limbCount() {
        return this.length;
    }

    public long limb(int index) {
        Objects.checkIndex(index, LIMBS);
        return index == 0 ? this.l0 : index == 1 ? this.l1 : this.rest != null
                ? (index < this.length ? this.rest[index] : this.rest[this.length - 1] >> 63) : this.l1 >> 63;
    }

    public boolean fitsLong() {
        return this.length == 1;
    }

    /** Number of bits in the unsigned absolute magnitude (MIN has bit length 1088). */
    public int bitLength() {
        return Int1088Math.magnitudeBits(this.words(), 0, this.length);
    }

    public int signum() {
        return this.limb(this.length - 1) < 0 ? -1 : this.fitsLong() && this.l0 == 0 ? 0 : 1;
    }

    /** Low word only; never use this as the identity of a wide coordinate. */
    public long lowLimb() {
        return this.l0;
    }

    /** Common local-distance case; compare the immutable high words in bulk. */
    boolean sameUpperLimbs(Int1088 other) {
        return this.length == other.length && this.l1 == other.l1
                && (this.rest == other.rest || this.rest != null
                && Arrays.equals(this.rest, 2, this.length, other.rest, 2, other.length));
    }

    public Int1088 copy() {
        return this.fitsLong() ? new Int1088(this.l0) : this.length == 2 ? new Int1088(this.l0, this.l1)
                : new Int1088(this.rest.clone());
    }

    public void copyFixed(long[] dst, int offset) {
        Objects.checkFromIndexSize(offset, LIMBS, dst.length);
        for (int i = 0; i < LIMBS; ++i) dst[offset + i] = this.limb(i);
    }

    /** Internal wrapping arithmetic modulo 2^1088; use addSaturated for coordinates. */
    public Int1088 add(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) return this.addSaturated(other);
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.add(this.words(), 0, this.limbCount(), other.words(), 0, other.limbCount(), out, 0));
    }

    public Int1088 addSaturated(Int1088 other) {
        if (other.fitsLong() && other.l0 == 0L) return this;
        if (this.fitsLong() && this.l0 == 0L) return other;
        if (this.fitsLong() && other.fitsLong()) {
            long a = this.lowLimb(), b = other.lowLimb(), r = a + b;
            if (((a ^ r) & (b ^ r)) >= 0) return fromLong(r);
        }
        if (!this.fitsLong() && other.fitsLong()) return this.addLongSaturated(other.l0);
        if (this.fitsLong() && !other.fitsLong()) return other.addLongSaturated(this.l0);
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.addSaturated(this.words(), 0, this.limbCount(), other.words(), 0, other.limbCount(), out, 0));
    }

    private Int1088 addLongSaturated(long offset) {
        long[] out = this.words().clone();
        long previous = out[0];
        out[0] = previous + offset;
        // Adding a negative offset propagates a borrow, a positive one a carry.
        boolean propagate = offset < 0 ? Long.compareUnsigned(out[0], previous) > 0
                : Long.compareUnsigned(out[0], previous) < 0;
        long step = offset < 0 ? -1L : 1L;
        for (int i = 1; propagate && i < out.length; ++i) {
            previous = out[i];
            out[i] = previous + step;
            propagate = offset < 0 ? previous == 0L : out[i] == 0L;
        }
        long top = this.limb(this.length - 1);
        if (((top ^ out[out.length - 1]) & (offset ^ out[out.length - 1])) < 0L) {
            if (this.length == LIMBS) return offset < 0 ? MIN : MAX;
            out = Arrays.copyOf(out, out.length + 1);
            out[out.length - 1] = top >> 63;
        }
        return ownedResult(out);
    }

    /** Internal wrapping arithmetic modulo 2^1088. */
    public Int1088 subtract(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) return this.subtractSaturated(other);
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.subtract(this.words(), 0, this.limbCount(), other.words(), 0, other.limbCount(), out, 0));
    }

    public Int1088 subtractSaturated(Int1088 other) {
        if (this.fitsLong() && other.fitsLong()) {
            long a = this.lowLimb(), b = other.lowLimb(), r = a - b;
            if (((a ^ b) & (a ^ r)) >= 0) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.subtractSaturated(this.words(), 0, this.limbCount(), other.words(), 0, other.limbCount(), out, 0));
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
        if (bits >= this.length * 64) return fromLong(this.signum() < 0 ? -1L : 0L);
        int words = bits >>> 6, shift = bits & 63;
        long[] out = new long[this.length - words];
        for (int i = 0; i < out.length; ++i) {
            long lo = this.limb(i + words);
            long hi = i + words + 1 < this.length ? this.limb(i + words + 1) : lo >> 63;
            out[i] = shift == 0 ? lo : (lo >>> shift) | (hi << (64 - shift));
        }
        return ownedResult(out);
    }

    public Int1088 shiftLeftSaturated(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
        if (bits == 0) return this;
        if (this.fitsLong() && bits < 64) {
            long r = this.lowLimb() << bits;
            if ((r >> bits) == this.lowLimb()) return fromLong(r);
        }
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.shiftLeftSaturated(this.words(), 0, this.limbCount(), bits, out, 0));
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
        if (this.fitsLong()) return fromLong(~this.l0);
        long[] out = new long[this.limbCount()];
        for (int i = 0; i < out.length; ++i) out[i] = ~this.limb(i);
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
        return result(out, Int1088Math.multiplySaturated(this.words(), 0, this.limbCount(), factor, out, 0));
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
        return this.fitsLong() ? (double) this.l0 : Int1088Math.toDoubleRounded(this.words(), 0, this.limbCount());
    }

    public double toDoubleSaturated() {
        return this.fitsLong() ? (double) this.l0 : Int1088Math.toDoubleSaturated(this.words(), 0, this.limbCount());
    }

    public float toFloatRounded() {
        return this.fitsLong() ? (float) this.l0 : Int1088Math.toFloatRounded(this.words(), 0, this.limbCount());
    }

    public Int1088 floorDivideSaturated(int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        if (this.fitsLong()) return fromLong(Math.floorDiv(this.lowLimb(), (long) divisor));
        long[] out = new long[LIMBS];
        return result(out, Int1088Math.floorDivide(this.words(), 0, this.limbCount(), divisor, out, 0));
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
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        if (this.fitsLong()) return (int) (this.l0 % divisor);
        boolean negative = this.signum() < 0;
        int firstNonzero = 0;
        if (negative) while (this.limb(firstNonzero) == 0L) ++firstNonzero;
        long remainder = 0L;
        for (int i = this.length - 1; i >= 0; --i) {
            long word = this.limb(i);
            if (negative) word = ~word + (i <= firstNonzero ? 1L : 0L);
            remainder = ((remainder << 32) | (word >>> 32)) % divisor;
            remainder = ((remainder << 32) | (word & 0xffffffffL)) % divisor;
        }
        return (int) (negative ? -remainder : remainder);
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
        if (this == other) return 0;
        if (this.fitsLong() && other.fitsLong()) return Long.compare(this.lowLimb(), other.lowLimb());
        int sign = Integer.compare(this.signum(), other.signum());
        if (sign != 0) return sign;
        if (this.length != other.length) return this.signum() * Integer.compare(this.length, other.length);
        for (int i = Math.max(this.length, other.length) - 1; i >= 0; --i) {
            int comparison = Long.compareUnsigned(this.limb(i), other.limb(i));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Int1088)) return false;
        Int1088 value = (Int1088) other;
        return this.length == value.length && this.l0 == value.l0 && this.l1 == value.l1
                && Arrays.equals(this.rest, value.rest);
    }

    public long hash() {
        long cached = this.cachedHash;
        if (cached != 0L) return cached;
        long computed = this.computeHash();
        this.cachedHash = computed;
        return computed;
    }

    private long computeHash() {
        if (this.fitsLong()) return Int1088Math.hash(this.l0);
        if (this.rest != null) return Int1088Math.hash(this.rest, 0, this.length);
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < LIMBS; ++i) {
            long mixed = it.unimi.dsi.fastutil.HashCommon.murmurHash3(this.limb(i) + 0x9e3779b97f4a7c15L * (i + 1));
            h = (h ^ mixed) * 0x100000001b3L;
        }
        return it.unimi.dsi.fastutil.HashCommon.murmurHash3(h);
    }

    public int hashXZ() {
        return Long.hashCode(this.hash());
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
