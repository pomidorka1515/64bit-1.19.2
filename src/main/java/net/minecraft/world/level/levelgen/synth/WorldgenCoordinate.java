package net.minecraft.world.level.levelgen.synth;

import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.core.Int1088Scratch;
import net.minecraft.world.level.levelgen.RandomSupport;

/** Allocation-free signed horizontal coordinate operations for generation. */
public final class WorldgenCoordinate {
    private WorldgenCoordinate() {
    }

    public static boolean fitsLong(Int1088 value) { return value.fitsLong(); }
    public static boolean fitsLong(long[] limbs, int offset, int length) { return Int1088Math.fitsLong(limbs, offset, length); }
    public static double toDouble(long[] limbs, int offset, int length) { return Int1088Math.toDoubleRounded(limbs, offset, length); }
    public static float toFloat(long[] limbs, int offset, int length) { return fitsLong(limbs, offset, length) ? (float)limbs[offset] : (float)toDouble(limbs, offset, length); }
    public static double toDouble(Int1088 value) { return value.fitsLong() ? (double)value.lowLimb() : value.toDoubleRounded(); }
    public static float toFloat(Int1088 value) { return value.fitsLong() ? (float)value.lowLimb() : (float)value.toDoubleRounded(); }
    public static Int1088 add(Int1088 value, long offset) { return value.addSaturated(Int1088.fromLong(offset)); }
    public static Int1088 add(Int1088 a, Int1088 b) { return a.addSaturated(b); }
    public static Int1088 subtract(Int1088 a, Int1088 b) { return a.subtractSaturated(b); }
    public static Int1088 shiftRight(Int1088 value, int bits) { return value.shiftRight(bits); }
    public static Int1088 shiftLeftSaturated(Int1088 value, int bits) { return value.shiftLeftSaturated(bits); }

    public static Int1088 truncShift(Int1088 value, int bits) {
        Int1088 floor = value.shiftRight(bits);
        if (bits == 0 || value.signum() >= 0) return floor;
        int full = Math.min(bits >>> 6, Int1088.LIMBS);
        for (int i = 0; i < full; ++i) if (value.limb(i) != 0) return floor.add(Int1088.ONE);
        if (full < Int1088.LIMBS && (bits & 63) != 0 && maskBelow(value.limb(full), bits & 63) != 0) return floor.add(Int1088.ONE);
        return floor;
    }

    public static int remainder(Int1088 value, int divisor) { return value.remainder(divisor); }
    public static Int1088 floorDivide(Int1088 value, int divisor) { return value.floorDivideSaturated(divisor); }

    public static long seedCoordinate(Int1088 value) {
        long seed = value.limb(0);
        for (int i = 1; i < Int1088.LIMBS; ++i) {
            long extra = value.limb(i) ^ (value.limb(i - 1) >> 63);
            if (extra != 0) seed ^= RandomSupport.mixStafford13(extra + i * 0x9e3779b97f4a7c15L);
        }
        return seed;
    }

    public static boolean squareSumExceeds(Int1088 x, Int1088 z, long limit) {
        if (limit < 0) return true;
        long root = (long)Math.sqrt(limit);
        while (root > 0 && root > limit / root) --root;
        while (root < 3037000499L && root + 1 <= limit / (root + 1)) ++root;
        if (!x.fitsLong() || !z.fitsLong()) return true;
        long a = x.lowLimb(), b = z.lowLimb();
        if (a < -root || a > root || b < -root || b > root) return true;
        return a * a > limit - b * b;
    }

    public static void scale(Int1088 value, double scale, int octave, PreciseNoiseCoordinate.Split out) {
        if (value.fitsLong()) {
            long v = value.lowLimb();
            scale(v >> 63, v, scale, octave, out);
            return;
        }
        try (Int1088Scratch magnitude = Int1088Scratch.acquire(); Int1088Scratch product = Int1088Scratch.acquire()) {
            value.copyFixed(magnitude.limbs(), 0);
            scaleWide(magnitude.limbs(), value.signum() < 0, scale, octave, product.limbs(), out);
        }
    }

    public static void scale(long[] limbs, int offset, int length, double scale, int octave, PreciseNoiseCoordinate.Split out) {
        int canonical = Int1088Math.canonicalLength(limbs, offset, length);
        if (canonical == 1) {
            long v = limbs[offset];
            scale(v >> 63, v, scale, octave, out);
            return;
        }
        if (canonical > Int1088.LIMBS) throw new IllegalArgumentException("Coordinate exceeds 1088 bits");
        try (Int1088Scratch magnitude = Int1088Scratch.acquire(); Int1088Scratch product = Int1088Scratch.acquire()) {
            magnitude.copyFrom(limbs, offset, length);
            scaleWide(magnitude.limbs(), limbs[offset + length - 1] < 0, scale, octave, product.limbs(), out);
        }
    }

    private static void scaleWide(long[] magnitude, boolean coordinateNegative, double scale, int octave, long[] product, PreciseNoiseCoordinate.Split out) {
        out.failureMagnitude = Double.NaN;
        if (!Double.isFinite(scale) || scale == 0.0D) { out.lattice = 0; out.fraction = 0; return; }
        if (coordinateNegative) {
            long carry = 1;
            for (int i = 0; i < Int1088.LIMBS; ++i) {
                long v = ~magnitude[i] + carry;
                carry = carry != 0 && v == 0 ? 1 : 0;
                magnitude[i] = v;
            }
        }
        long bits = Double.doubleToRawLongBits(scale);
        int exponent = (int)((bits >>> 52) & 2047);
        long mantissa = (bits & 0xfffffffffffffL) | (exponent == 0 ? 0 : 0x10000000000000L);
        long power = (long)(exponent == 0 ? -1074 : exponent - 1075) + octave;
        boolean negative = coordinateNegative ^ (bits < 0);
        int len = Int1088Math.multiplyUnsigned(magnitude, 0, Int1088.LIMBS, mantissa, product, 0);
        int productBits = (len - 1) * 64 + 64 - Long.numberOfLeadingZeros(product[len - 1]);
        if (productBits + power > 63 && GenerationContext.current().mode() != GeneratorMode.PATCHED) {
            double result = 0;
            for (int i = 0; i < len; ++i) {
                long exp = power + 64L * i;
                if (product[i] != 0) result += Math.scalb(unsignedDouble(product[i]), (int)Math.max(-2099, Math.min(2099, exp)));
            }
            out.failureMagnitude = negative ? -result : result;
        }
        if (power >= 0) {
            long integer = power >= 64 ? 0 : product[0] << (int)power;
            out.lattice = negative ? -integer : integer;
            out.fraction = 0;
            return;
        }
        long shift = -power;
        long integer = 0;
        if (shift < 1152) {
            int word = (int)(shift >>> 6), bit = (int)shift & 63;
            integer = product[word] >>> bit;
            if (bit != 0 && word + 1 < 18) integer |= product[word + 1] << (64 - bit);
        }
        boolean remainder = false;
        for (int i = 0; i < 18; ++i) {
            product[i] = maskBelow(product[i], (int)Math.max(0, Math.min(64, shift - 64L * i)));
            remainder |= product[i] != 0;
        }
        long lattice = negative ? -integer - (remainder ? 1 : 0) : integer;
        if (negative && remainder && shift <= 1152) {
            long carry = 1;
            for (int i = 0; i < 18; ++i) {
                long v = ~product[i] + carry;
                carry = carry != 0 && v == 0 ? 1 : 0;
                product[i] = maskBelow(v, (int)Math.max(0, Math.min(64, shift - 64L * i)));
            }
        }
        double fraction = fractionRounded(product, shift);
        if (negative && remainder && shift > 1152) fraction = 1.0D - fraction;
        if (fraction >= 1.0D) { ++lattice; fraction = 0; }
        out.lattice = lattice;
        out.fraction = fraction;
    }

    private static double fractionRounded(long[] words, long shift) {
        int top = 1151;
        while (top >= 0 && (words[top >>> 6] & (1L << (top & 63))) == 0) --top;
        if (top < 0) return 0;
        int keep = (int)Math.min(Math.min(53, top + 1), Math.max(0, 1075L + top - shift));
        if (keep == 0) {
            if (1075L + top - shift < 0) return 0;
            boolean sticky = false;
            for (int bit = top - 1; bit >= 0 && !sticky; --bit) sticky = (words[bit >>> 6] & (1L << (bit & 63))) != 0;
            return sticky ? Double.MIN_VALUE : 0;
        }
        long significand = 0;
        for (int bit = top; bit > top - keep; --bit) significand = (significand << 1) | ((words[bit >>> 6] >>> (bit & 63)) & 1);
        int guard = top - keep;
        if (guard >= 0 && (words[guard >>> 6] & (1L << (guard & 63))) != 0) {
            boolean sticky = false;
            for (int bit = guard - 1; bit >= 0 && !sticky; --bit) sticky = (words[bit >>> 6] & (1L << (bit & 63))) != 0;
            if (sticky || (significand & 1) != 0) ++significand;
        }
        return Math.scalb((double)significand, (int)Math.max(-2099, top - keep + 1L - shift));
    }

    public static boolean fitsLong(long hi, long lo) {
        return hi == (lo >> 63);
    }

    /** Preserve the historical seed domain and mix only its formerly discarded bits. */
    public static long seedCoordinate(long hi, long lo) {
        long extra = hi ^ (lo >> 63);
        return extra == 0L ? lo : lo ^ net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(extra);
    }

    public static double toDouble(long hi, long lo) {
        if (fitsLong(hi, lo))
            return (double) lo;
        // Convert the unsigned magnitude before rounding; negative limbs are not
        // independent signed coordinates (notably just below -2^64).
        boolean negative = hi < 0L;
        long low = negative ? -lo : lo;
        long high = negative ? ~hi + (lo == 0L ? 1L : 0L) : hi;
        int length = high == 0L ? 64 - Long.numberOfLeadingZeros(low) : 128 - Long.numberOfLeadingZeros(high);
        if (length <= 53) return negative ? -(double) low : (double) low;
        int shift = length - 53;
        long significant = shift < 64 ? (low >>> shift) | (high << (64 - shift)) : high >>> (shift - 64);
        int guardBit = shift - 1;
        boolean guard = guardBit < 64 ? ((low >>> guardBit) & 1L) != 0L : ((high >>> (guardBit - 64)) & 1L) != 0L;
        boolean sticky = guardBit < 64 ? maskBelow(low, guardBit) != 0L : low != 0L || maskBelow(high, guardBit - 64) != 0L;
        if (guard && (sticky || (significant & 1L) != 0L)) ++significant;
        double magnitude = Math.scalb((double) significant, shift);
        return negative ? -magnitude : magnitude;
    }

    public static float toFloat(long hi, long lo) {
        // Retain the direct long-to-float rounding of the established path.
        return fitsLong(hi, lo) ? (float) lo : (float) toDouble(hi, lo);
    }

    private static double unsignedDouble(long value) {
        return (double) (value >>> 1) * 2.0D + (value & 1L);
    }

    public static long addHigh(long hi, long lo, long offset) {
        long sum = lo + offset;
        long high = hi + (offset >> 63) + (Long.compareUnsigned(sum, lo) < 0 ? 1L : 0L);
        return ((hi ^ high) & ((offset >> 63) ^ high)) < 0L ? (hi < 0L ? Long.MIN_VALUE : Long.MAX_VALUE) : high;
    }

    public static long addLow(long hi, long lo, long offset) {
        long sum = lo + offset;
        long high = hi + (offset >> 63) + (Long.compareUnsigned(sum, lo) < 0 ? 1L : 0L);
        return ((hi ^ high) & ((offset >> 63) ^ high)) < 0L ? (hi < 0L ? 0L : -1L) : sum;
    }

    public static double difference(long aHi, long aLo, long bHi, long bLo) {
        if (fitsLong(aHi, aLo) && fitsLong(bHi, bLo))
            return net.minecraft.world.level.WorldBounds.signedDifference(aLo, bLo);
        long lo = aLo - bLo;
        long hi = aHi - bHi - (Long.compareUnsigned(aLo, bLo) < 0 ? 1L : 0L);
        if (((aHi ^ bHi) & (aHi ^ hi)) < 0L) return aHi < 0L ? -Double.MAX_VALUE : Double.MAX_VALUE;
        return toDouble(hi, lo);
    }

    public static long shiftLow(long hi, long lo, int shift) {
        if (shift == 0) return lo;
        if (shift < 64) return (lo >>> shift) | (hi << (64 - shift));
        return shift < 128 ? hi >> (shift - 64) : hi >> 63;
    }

    public static long shiftHigh(long hi, int shift) {
        return shift < 64 ? hi >> shift : hi >> 63;
    }

    public static long truncShiftLow(long hi, long lo, int shift) {
        long floor = shiftLow(hi, lo, shift);
        return floor + (hi < 0L && (lo & ((1L << shift) - 1L)) != 0L ? 1L : 0L);
    }

    public static long truncShiftHigh(long hi, long lo, int shift) {
        long floor = shiftLow(hi, lo, shift);
        return shiftHigh(hi, shift) + (hi < 0L && (lo & ((1L << shift) - 1L)) != 0L && floor == -1L ? 1L : 0L);
    }

    public static int remainder(long hi, long lo, int divisor) {
        boolean negative = hi < 0L;
        long a = negative ? -lo : lo;
        long b = negative ? ~hi + (lo == 0L ? 1L : 0L) : hi;
        long r = Long.remainderUnsigned(b, divisor);
        for (int shift = 48; shift >= 0; shift -= 16)
            r = ((r << 16) + ((a >>> shift) & 65535L)) % divisor;
        return negative ? -(int) r : (int) r;
    }

    /** Floor division by positive int; allocation occurs only at the placement API boundary. */
    public static net.minecraft.core.Int128 floorDivide(long hi, long lo, int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("non-positive divisor");
        boolean negative = hi < 0L;
        long low = negative ? -lo : lo;
        long high = negative ? ~hi + (lo == 0L ? 1L : 0L) : hi;
        long qHi = Long.divideUnsigned(high, divisor);
        long r = Long.remainderUnsigned(high, divisor);
        long acc = (r << 32) | (low >>> 32);
        long qUpper = acc / divisor;
        r = acc % divisor;
        acc = (r << 32) | (low & 0xffffffffL);
        long qLo = (qUpper << 32) | (acc / divisor);
        r = acc % divisor;
        if (negative) {
            qHi = ~qHi + (qLo == 0L ? 1L : 0L);
            qLo = -qLo;
            if (r != 0L) {
                qHi -= qLo == 0L ? 1L : 0L;
                --qLo;
            }
        }
        return net.minecraft.core.Int128.of(qHi, qLo);
    }

    private static long multiplyHigh(long a, long b) {
        return Math.multiplyHigh(a, b) + (a < 0L ? b : 0L) + (b < 0L ? a : 0L);
    }

    /**
     * Exact 128x53-bit product followed by a binary exponent. Only the final
     * lattice is reduced modulo 2^64; octave division precedes that reduction.
     */
    public static void scale(long hi, long lo, double scale, int octave, PreciseNoiseCoordinate.Split out) {
        out.failureMagnitude = Double.NaN;
        if (!Double.isFinite(scale) || scale == 0.0D) {
            out.lattice = 0L;
            out.fraction = 0.0D;
            return;
        }
        long bits = Double.doubleToRawLongBits(scale);
        int exponent = (int) ((bits >>> 52) & 2047L);
        long mantissa = bits & 0x000fffffffffffffL;
        int power = (exponent == 0 ? -1074 : exponent - 1075) + octave;
        if (exponent != 0) mantissa |= 0x0010000000000000L;
        boolean negative = (hi < 0L) ^ (bits < 0L);
        long a = hi < 0L ? -lo : lo;
        long b = hi < 0L ? ~hi + (lo == 0L ? 1L : 0L) : hi;
        long p0 = a * mantissa;
        long carry = multiplyHigh(a, mantissa);
        long middle = b * mantissa;
        long p1 = middle + carry;
        long p2 = multiplyHigh(b, mantissa) + (Long.compareUnsigned(p1, middle) < 0 ? 1L : 0L);
        int productBits = p2 != 0L ? 192 - Long.numberOfLeadingZeros(p2)
                : p1 != 0L ? 128 - Long.numberOfLeadingZeros(p1) : 64 - Long.numberOfLeadingZeros(p0);
        // Classification precedes permutation reduction. Conversion is permitted
        // only for an explicitly failed legacy result, never a healthy coordinate.
        if (productBits + power > 63 && GenerationContext.current().mode() != GeneratorMode.PATCHED) {
            double magnitude = Math.scalb(unsignedDouble(p0), power)
                    + Math.scalb(unsignedDouble(p1), power + 64)
                    + Math.scalb(unsignedDouble(p2), power + 128);
            out.failureMagnitude = negative ? -magnitude : magnitude;
        }
        if (power >= 0) {
            long integer = power >= 64 ? 0L : p0 << power;
            out.lattice = negative ? -integer : integer;
            out.fraction = 0.0D;
            return;
        }
        int shift = -power;
        long integer = wordAt(p0, p1, p2, shift);
        long r0 = maskBelow(p0, shift);
        long r1 = maskBelow(p1, shift - 64);
        long r2 = maskBelow(p2, shift - 128);
        boolean remainder = (r0 | r1 | r2) != 0L;
        long lattice = negative ? -integer - (remainder ? 1L : 0L) : integer;
        // Complement the integer remainder, not a rounded fraction: subtraction
        // from 1 would erase tiny negative-coordinate offsets near an integer.
        if (negative && remainder && shift <= 192) {
            long c0 = -r0;
            long c1 = ~r1 + (r0 == 0L ? 1L : 0L);
            long c2 = ~r2 + (r0 == 0L && r1 == 0L ? 1L : 0L);
            r0 = maskBelow(c0, shift);
            r1 = maskBelow(c1, shift - 64);
            r2 = maskBelow(c2, shift - 128);
        }
        double fraction = Math.scalb(unsignedDouble(r0), -shift)
                + Math.scalb(unsignedDouble(r1), 64 - shift)
                + Math.scalb(unsignedDouble(r2), 128 - shift);
        if (negative && remainder && shift > 192) fraction = 1.0D - fraction;
        if (fraction >= 1.0D) {
            ++lattice;
            fraction = 0.0D;
        }
        out.lattice = lattice;
        out.fraction = fraction;
    }

    private static long maskBelow(long value, int bits) {
        return bits <= 0 ? 0L : bits >= 64 ? value : value & (-1L >>> (64 - bits));
    }

    private static long wordAt(long p0, long p1, long p2, int shift) {
        if (shift >= 192) return 0L;
        if (shift >= 128) return p2 >>> (shift - 128);
        if (shift == 64) return p1;
        if (shift > 64) return (p1 >>> (shift - 64)) | (p2 << (128 - shift));
        return (p0 >>> shift) | (p1 << (64 - shift));
    }
}
