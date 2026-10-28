package net.minecraft.core;

import it.unimi.dsi.fastutil.HashCommon;

/** Allocation-free little-endian limb arithmetic. Inputs are signed limb views (1..17 words).
 * Outputs need 17 words, except unsigned multiplication, which needs 18.
 * Only equal-offset, in-place right shift is supported; other outputs must not overlap inputs.
 * Unused output words are unspecified. Returned lengths are canonical signed lengths.
 */
public final class Int1088Math {
    public static final int CAPACITY = 20;
    /** Shared physics-local precision guard, independent of the world-coordinate domain. */
    public static final long MAX_SAFE_LOCAL_SPAN = 1L << 20;
    private static final int N = Int1088.LIMBS;

    private Int1088Math() {
    }

    private static long word(long[] a, int off, int len, int i) {
        return i < len ? a[off + i] : a[off + len - 1] >> 63;
    }

    public static int canonicalLength(long[] a, int off, int len) {
        if (len < 1 || len > CAPACITY) throw new IllegalArgumentException("Invalid limb count: " + len);
        java.util.Objects.checkFromIndexSize(off, len, a.length);
        while (len > 1 && a[off + len - 1] == (a[off + len - 2] >> 63)) --len;
        return len;
    }

    public static boolean fitsLong(long[] a, int ao, int alen) {
        return canonicalLength(a, ao, alen) == 1;
    }

    public static int compare(long[] a, int ao, int alen, long[] b, int bo, int blen) {
        if (alen == 1 && blen == 1) return Long.compare(a[ao], b[bo]);
        int c = Long.compare(word(a, ao, alen, N - 1), word(b, bo, blen, N - 1));
        for (int i = N - 2; c == 0 && i >= 0; --i) {
            c = Long.compareUnsigned(word(a, ao, alen, i), word(b, bo, blen, i));
        }
        return c;
    }

    /** Exact subtraction followed by signed-long saturation, without allocating a result. */
    public static long signedDifferenceAsLong(Int1088 value, Int1088 origin) {
        if (value == origin) return 0L;
        if (value.fitsLong() && origin.fitsLong()) {
            return longDifference(value.lowLimb(), origin.lowLimb());
        }
        long low = value.limb(0) - origin.limb(0);
        long borrow = Long.compareUnsigned(value.limb(0), origin.limb(0)) < 0 ? 1 : 0;
        if (borrow == (low >>> 63) && value.sameUpperLimbs(origin)) return low;
        long extension = low >> 63;
        boolean fits = true;
        long top = low;
        int length = Math.min(N, Math.max(value.limbCount(), origin.limbCount()) + 1);
        for (int i = 1; i < length; ++i) {
            long a = value.limb(i), b = origin.limb(i);
            long difference = a - b;
            top = difference - borrow;
            borrow = Long.compareUnsigned(a, b) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
            fits &= top == extension;
        }
        long a = value.limb(N - 1), b = origin.limb(N - 1);
        if (((a ^ b) & (a ^ top)) < 0) return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        return fits ? low : top < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** Limb-view form for hot callers retaining their own coordinate buffers. */
    public static long signedDifferenceAsLong(long[] a, int ao, int alen, long[] b, int bo, int blen) {
        alen = canonicalLength(a, ao, alen);
        blen = canonicalLength(b, bo, blen);
        if (alen > N || blen > N) throw new IllegalArgumentException("Coordinate exceeds 1088 bits");
        if (alen == 1 && blen == 1) return longDifference(a[ao], b[bo]);
        long low = a[ao] - b[bo];
        long borrow = Long.compareUnsigned(a[ao], b[bo]) < 0 ? 1 : 0;
        long extension = low >> 63;
        boolean fits = true;
        long top = low;
        int length = Math.min(N, Math.max(alen, blen) + 1);
        for (int i = 1; i < length; ++i) {
            long x = word(a, ao, alen, i), y = word(b, bo, blen, i);
            long difference = x - y;
            top = difference - borrow;
            borrow = Long.compareUnsigned(x, y) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
            fits &= top == extension;
        }
        long at = word(a, ao, alen, N - 1), bt = word(b, bo, blen, N - 1);
        if (((at ^ bt) & (at ^ top)) < 0) return at < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        return fits ? low : top < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    private static long longDifference(long value, long origin) {
        long result = value - origin;
        return ((value ^ origin) & (value ^ result)) < 0
                ? value < 0 ? Long.MIN_VALUE : Long.MAX_VALUE : result;
    }

    public static boolean signedDifferenceExact(Int1088 value, Int1088 origin) {
        long difference = signedDifferenceAsLong(value, origin);
        return difference >= -MAX_SAFE_LOCAL_SPAN && difference <= MAX_SAFE_LOCAL_SPAN;
    }

    public static double signedDifference(Int1088 value, Int1088 origin, boolean[] exactOut) {
        long difference = signedDifferenceAsLong(value, origin);
        boolean exact = difference >= -MAX_SAFE_LOCAL_SPAN && difference <= MAX_SAFE_LOCAL_SPAN;
        exactOut[0] = exact;
        return exact ? (double) difference : difference < 0 ? (double) Long.MIN_VALUE : (double) Long.MAX_VALUE;
    }

    /** Nonnegative distance, saturated to a signed long. */
    public static long distance(Int1088 a, Int1088 b) {
        return a.compareTo(b) >= 0 ? signedDifferenceAsLong(a, b) : signedDifferenceAsLong(b, a);
    }

    public static long distancePlusOne(Int1088 a, Int1088 b) {
        long distance = distance(a, b);
        return distance == Long.MAX_VALUE ? distance : distance + 1;
    }

    /** Nonnegative span product, saturated rather than wrapped. */
    public static long safeProduct(long first, long second) {
        if (first < 0 || second < 0) throw new IllegalArgumentException("Negative span");
        return second != 0 && first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    private static int limit(boolean negative, long[] out, int oo) {
        for (int i = 0; i < N - 1; ++i) out[oo + i] = negative ? 0L : -1L;
        out[oo + N - 1] = negative ? Long.MIN_VALUE : Long.MAX_VALUE;
        return N;
    }

    private static int sum(long[] a, int ao, int alen, long[] b, int bo, int blen,
                           long[] out, int oo, boolean subtract, boolean saturate) {
        if (alen == 1 && blen == 1) {
            long x = a[ao], y = b[bo], r = subtract ? x - y : x + y;
            boolean overflow = subtract ? ((x ^ y) & (x ^ r)) < 0 : ((x ^ r) & (y ^ r)) < 0;
            if (!overflow) {
                out[oo] = r;
                return 1;
            }
        }
        long carry = subtract ? 1L : 0L;
        long at = word(a, ao, alen, N - 1);
        long bt = word(b, bo, blen, N - 1);
        // One extra sign word contains every possible carry. Do not process
        // all 17 words for a two- or three-word distant coordinate.
        int length = Math.min(N, Math.max(alen, blen) + 1);
        for (int i = 0; i < length; ++i) {
            long x = word(a, ao, alen, i);
            long y = word(b, bo, blen, i);
            if (subtract) y = ~y;
            long s = x + y;
            long r = s + carry;
            carry = Long.compareUnsigned(s, x) < 0 || Long.compareUnsigned(r, s) < 0 ? 1L : 0L;
            out[oo + i] = r;
        }
        long rt = out[oo + length - 1];
        boolean overflow = subtract ? ((at ^ bt) & (at ^ rt)) < 0 : ((at ^ rt) & (bt ^ rt)) < 0;
        return saturate && overflow ? limit(at < 0, out, oo) : canonicalLength(out, oo, length);
    }

    public static int add(long[] a, int ao, int alen, long[] b, int bo, int blen, long[] out, int oo) {
        return sum(a, ao, alen, b, bo, blen, out, oo, false, false);
    }

    public static int addSaturated(long[] a, int ao, int alen, long[] b, int bo, int blen, long[] out, int oo) {
        return sum(a, ao, alen, b, bo, blen, out, oo, false, true);
    }

    public static int subtract(long[] a, int ao, int alen, long[] b, int bo, int blen, long[] out, int oo) {
        return sum(a, ao, alen, b, bo, blen, out, oo, true, false);
    }

    public static int subtractSaturated(long[] a, int ao, int alen, long[] b, int bo, int blen, long[] out, int oo) {
        return sum(a, ao, alen, b, bo, blen, out, oo, true, true);
    }

    /** Wrapping addition of a signed long. */
    public static int addLong(long[] a, int ao, int alen, long value, long[] out, int oo) {
        if (alen == 1) {
            long x = a[ao], r = x + value;
            if (((x ^ r) & (value ^ r)) >= 0) {
                out[oo] = r;
                return 1;
            }
        }
        long carry = 0;
        for (int i = 0; i < N; ++i) {
            long x = word(a, ao, alen, i);
            long y = i == 0 ? value : value >> 63;
            long s = x + y;
            long r = s + carry;
            carry = Long.compareUnsigned(s, x) < 0 || Long.compareUnsigned(r, s) < 0 ? 1L : 0L;
            out[oo + i] = r;
        }
        return canonicalLength(out, oo, N);
    }

    private static void checkShift(int bits) {
        if (bits < 0) throw new IllegalArgumentException("Negative shift count: " + bits);
    }

    private static long rightWord(long[] a, int ao, int alen, int i, int bits, boolean unsigned) {
        if (bits >= Int1088.BITS) return unsigned ? 0 : word(a, ao, alen, N - 1) >> 63;
        int index = i + (bits >>> 6);
        int shift = bits & 63;
        long extension = unsigned ? 0 : word(a, ao, alen, N - 1) >> 63;
        long lo = index < N ? word(a, ao, alen, index) : extension;
        long hi = index + 1 < N ? word(a, ao, alen, index + 1) : extension;
        return shift == 0 ? lo : (lo >>> shift) | (hi << (64 - shift));
    }

    /** Supports in-place operation at the same offset. */
    public static int shiftRight(long[] a, int ao, int alen, int bits, long[] out, int oo) {
        checkShift(bits);
        if (alen == 1) {
            out[oo] = bits >= 64 ? a[ao] >> 63 : a[ao] >> bits;
            return 1;
        }
        // Preserve sign extension when the canonical top word is overwritten in place.
        long extension = a[ao + alen - 1] >> 63;
        for (int i = 0; i < N; ++i) {
            out[oo + i] = i < alen ? rightWord(a, ao, alen, i, bits, false) : extension;
        }
        return canonicalLength(out, oo, N);
    }

    public static int shiftLeftSaturated(long[] a, int ao, int alen, int bits, long[] out, int oo) {
        checkShift(bits);
        if (alen == 1 && bits < 64) {
            long r = a[ao] << bits;
            if ((r >> bits) == a[ao]) {
                out[oo] = r;
                return 1;
            }
        }
        boolean negative = a[ao + alen - 1] < 0;
        boolean zero = canonicalLength(a, ao, alen) == 1 && a[ao] == 0;
        if (bits >= Int1088.BITS) {
            if (!zero) return limit(negative, out, oo);
            out[oo] = 0;
            return 1;
        }
        // All discarded bits, including the new sign bit, must equal the original sign.
        for (int bit = Int1088.SIGN_BIT - bits; bit < Int1088.BITS; ++bit) {
            if (((word(a, ao, alen, bit >>> 6) >>> (bit & 63)) & 1L) != (negative ? 1 : 0)) {
                return limit(negative, out, oo);
            }
        }
        int words = bits >>> 6;
        int shift = bits & 63;
        for (int i = 0; i < N; ++i) {
            int source = i - words;
            long lo = source >= 0 ? word(a, ao, alen, source) : 0;
            long below = source > 0 ? word(a, ao, alen, source - 1) : 0;
            out[oo + i] = shift == 0 ? lo : (lo << shift) | (below >>> (64 - shift));
        }
        return canonicalLength(out, oo, N);
    }

    /** Word of the unsigned absolute magnitude, including MIN without saturation. */
    static long magnitudeWord(long[] a, int ao, int alen, int index) {
        long x = word(a, ao, alen, index);
        if (a[ao + alen - 1] >= 0) return x;
        boolean carry = true;
        for (int i = 0; i < index && carry; ++i) carry = word(a, ao, alen, i) == 0;
        return ~x + (carry ? 1L : 0L);
    }

    private static long unsignedHigh(long a, long b) {
        return Math.multiplyHigh(a, b) + (a < 0 ? b : 0) + (b < 0 ? a : 0);
    }

    /** Unsigned 17-word magnitude times an unsigned long; writes 18 words, returns unsigned length. */
    public static int multiplyUnsigned(long[] a, int ao, int alen, long factor, long[] out, int oo) {
        long carry = 0;
        for (int i = 0; i < alen; ++i) {
            long x = a[ao + i];
            long low = x * factor;
            long result = low + carry;
            carry = unsignedHigh(x, factor) + (Long.compareUnsigned(result, low) < 0 ? 1L : 0L);
            out[oo + i] = result;
        }
        out[oo + alen] = carry;
        java.util.Arrays.fill(out, oo + alen + 1, oo + N + 1, 0L);
        int len = alen + 1;
        while (len > 1 && out[oo + len - 1] == 0) --len;
        return len;
    }

    public static int multiplySaturated(long[] a, int ao, int alen, long factor, long[] out, int oo) {
        if (alen == 1) {
            long x = a[ao], r = x * factor;
            if (Math.multiplyHigh(x, factor) == (r >> 63)) {
                out[oo] = r;
                return 1;
            }
        }
        boolean negative = (a[ao + alen - 1] < 0) != (factor < 0);
        long f = factor < 0 ? -factor : factor;
        long carry = 0;
        boolean lowerZero = true;
        for (int i = 0; i < N; ++i) {
            long x = magnitudeWord(a, ao, alen, i);
            long low = x * f;
            long r = low + carry;
            carry = unsignedHigh(x, f) + (Long.compareUnsigned(r, low) < 0 ? 1L : 0L);
            out[oo + i] = r;
            if (i < N - 1) lowerZero &= r == 0;
        }
        long top = out[oo + N - 1];
        if (carry != 0 || top < 0 && !(negative && top == Long.MIN_VALUE && lowerZero)) {
            return limit(negative, out, oo);
        }
        if (negative) negateFixed(out, oo);
        return canonicalLength(out, oo, N);
    }

    static void negateFixed(long[] out, int oo) {
        long carry = 1;
        for (int i = 0; i < N; ++i) {
            long r = ~out[oo + i] + carry;
            out[oo + i] = r;
            if (r != 0) carry = 0;
        }
    }

    public static int fromDoubleFloor(double value, long[] out, int oo) {
        if (Double.isInfinite(value)) return limit(value < 0, out, oo);
        if (Double.isNaN(value)) value = 0;
        if (value >= -0x1p63 && value < 0x1p63) {
            long truncated = (long) value;
            out[oo] = value < truncated ? truncated - 1 : truncated;
            return 1;
        }
        long raw = Double.doubleToRawLongBits(value);
        int shift = (int) ((raw >>> 52) & 0x7ff) - 1075;
        long significand = (raw & 0xfffffffffffffL) | 0x10000000000000L;
        for (int i = 0; i < N; ++i) out[oo + i] = 0;
        int index = shift >>> 6;
        int bits = shift & 63;
        out[oo + index] = significand << bits;
        if (bits != 0) out[oo + index + 1] = significand >>> (64 - bits);
        if (raw < 0) negateFixed(out, oo);
        return canonicalLength(out, oo, N);
    }

    static int magnitudeBits(long[] a, int ao, int alen) {
        if (alen == 1) {
            long magnitude = a[ao] < 0 ? -a[ao] : a[ao];
            return 64 - Long.numberOfLeadingZeros(magnitude);
        }
        for (int i = N - 1; i >= 0; --i) {
            long w = magnitudeWord(a, ao, alen, i);
            if (w != 0) return i * 64 + 64 - Long.numberOfLeadingZeros(w);
        }
        return 0;
    }

    private static double rounded(long[] a, int ao, int alen, int precision, int maxExponent) {
        if (alen == 1 && precision == 53) return (double) a[ao];
        int length = magnitudeBits(a, ao, alen);
        if (length == 0) return 0;
        int discard = Math.max(0, length - precision);
        long significant = 0;
        for (int bit = length - 1; bit >= discard; --bit) {
            significant = (significant << 1) | ((magnitudeWord(a, ao, alen, bit >>> 6) >>> (bit & 63)) & 1);
        }
        if (discard > 0) {
            boolean guard = ((magnitudeWord(a, ao, alen, (discard - 1) >>> 6) >>> ((discard - 1) & 63)) & 1) != 0;
            boolean sticky = false;
            for (int bit = 0; bit < discard - 1 && !sticky; ++bit) {
                sticky = ((magnitudeWord(a, ao, alen, bit >>> 6) >>> (bit & 63)) & 1) != 0;
            }
            if (guard && (sticky || (significant & 1) != 0)) ++significant;
        }
        double magnitude = Math.scalb((double) significant, discard);
        double maximum = maxExponent == 1023 ? Double.MAX_VALUE : Float.MAX_VALUE;
        magnitude = Math.min(magnitude, maximum);
        return a[ao + alen - 1] < 0 ? -magnitude : magnitude;
    }

    public static double toDoubleRounded(long[] a, int ao, int alen) {
        return rounded(a, ao, alen, 53, 1023);
    }

    public static double toDoubleSaturated(long[] a, int ao, int alen) {
        return toDoubleRounded(a, ao, alen);
    }

    static float toFloatRounded(long[] a, int ao, int alen) {
        return (float) rounded(a, ao, alen, 24, 127);
    }

    /** Divides an unsigned fixed magnitude in place; used by cold decimal formatting. */
    static int divideMagnitude(long[] a, int ao, int divisor) {
        long remainder = 0;
        for (int i = N - 1; i >= 0; --i) {
            long w = a[ao + i];
            long high = (remainder << 32) | (w >>> 32);
            long qh = high / divisor;
            remainder = high % divisor;
            long low = (remainder << 32) | (w & 0xffffffffL);
            a[ao + i] = (qh << 32) | (low / divisor);
            remainder = low % divisor;
        }
        return (int) remainder;
    }

    public static int floorDivide(long[] a, int ao, int alen, int divisor, long[] out, int oo) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        if (alen == 1) {
            out[oo] = Math.floorDiv(a[ao], (long) divisor);
            return 1;
        }
        boolean negative = a[ao + alen - 1] < 0;
        for (int i = 0; i < N; ++i) out[oo + i] = magnitudeWord(a, ao, alen, i);
        int rem = divideMagnitude(out, oo, divisor);
        if (negative) {
            if (rem != 0) {
                for (int i = 0; i < N; ++i) if (++out[oo + i] != 0) break;
            }
            negateFixed(out, oo);
        }
        return canonicalLength(out, oo, N);
    }

    public static int remainder(long[] a, int ao, int alen, int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        if (alen == 1) return (int) (a[ao] % divisor);
        long rem = 0;
        for (int i = N - 1; i >= 0; --i) {
            long w = magnitudeWord(a, ao, alen, i);
            rem = ((rem << 32) | (w >>> 32)) % divisor;
            rem = ((rem << 32) | (w & 0xffffffffL)) % divisor;
        }
        return (int) (a[ao + alen - 1] < 0 ? -rem : rem);
    }

    public static long hash(long[] a, int ao, int alen) {
        long h = 0xcbf29ce484222325L;
        // Avalanche each word before folding: raw FNV folding preserves structured
        // high-bit differences, which can also disappear when folded to 32 bits.
        for (int i = 0; i < N; ++i) {
            long mixed = HashCommon.murmurHash3(word(a, ao, alen, i) + 0x9e3779b97f4a7c15L * (i + 1));
            h = (h ^ mixed) * 0x100000001b3L;
        }
        return HashCommon.murmurHash3(h);
    }

    public static int hashXZ(long[] a, int ao, int alen) {
        long h = hash(a, ao, alen);
        return (int) (h ^ (h >>> 32));
    }

    /** Hash a sign-extended single-limb coordinate without allocating a value or array. */
    public static int hashXZ(long value) {
        return Long.hashCode(hash(value));
    }

    /** The same full sign-extension fold as the primitive limb view. */
    public static long hash(long value) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < N; ++i) {
            long word = i == 0 ? value : value >> 63;
            long mixed = HashCommon.murmurHash3(word + 0x9e3779b97f4a7c15L * (i + 1));
            h = (h ^ mixed) * 0x100000001b3L;
        }
        h = HashCommon.murmurHash3(h);
        return h;
    }
}
