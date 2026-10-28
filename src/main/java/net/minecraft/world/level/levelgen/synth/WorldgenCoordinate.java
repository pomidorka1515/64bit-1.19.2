package net.minecraft.world.level.levelgen.synth;

import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.world.level.levelgen.RandomSupport;

/** Allocation-free signed horizontal coordinate operations for generation. */
public final class WorldgenCoordinate {
    // Scaling is a leaf operation: no callbacks or nested scaling occur while
    // these buffers are in use. Each thread owns its complete working set.
    private static final ThreadLocal<ScalingScratch> SCALING = ThreadLocal.withInitial(ScalingScratch::new);

    private static final class ScalingScratch {
        final long[] magnitude = new long[Int1088.LIMBS];
        final long[] product = new long[Int1088.LIMBS + 1];
    }

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
        if (value.fitsLong()) return value.lowLimb();
        long seed = value.limb(0);
        for (int i = 1; i < value.limbCount(); ++i) {
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
            scale(v, scale, octave, out);
            return;
        }
        ScalingScratch scratch = SCALING.get();
        int length = value.limbCount();
        for (int i = 0; i < length; ++i) scratch.magnitude[i] = value.limb(i);
        scaleWide(scratch.magnitude, length, value.signum() < 0, scale, octave, scratch.product, out);
    }

    public static void scale(long[] limbs, int offset, int length, double scale, int octave, PreciseNoiseCoordinate.Split out) {
        int canonical = Int1088Math.canonicalLength(limbs, offset, length);
        if (canonical == 1) {
            long v = limbs[offset];
            scale(v, scale, octave, out);
            return;
        }
        if (canonical > Int1088.LIMBS) throw new IllegalArgumentException("Coordinate exceeds 1088 bits");
        ScalingScratch scratch = SCALING.get();
        System.arraycopy(limbs, offset, scratch.magnitude, 0, canonical);
        scaleWide(scratch.magnitude, canonical, limbs[offset + length - 1] < 0, scale, octave, scratch.product, out);
    }

    private static void scaleWide(long[] magnitude, int length, boolean coordinateNegative, double scale, int octave, long[] product, PreciseNoiseCoordinate.Split out) {
        out.failureMagnitude = Double.NaN;
        if (!Double.isFinite(scale) || scale == 0.0D) { out.lattice = 0; out.fraction = 0; return; }
        if (coordinateNegative) {
            long carry = 1;
            for (int i = 0; i < length; ++i) {
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
        int len = Int1088Math.multiplyUnsigned(magnitude, 0, length, mantissa, product, 0);
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
        int topWord = words.length - 1;
        while (topWord >= 0 && words[topWord] == 0L) --topWord;
        if (topWord < 0) return 0;
        int top = topWord * 64 + 63 - Long.numberOfLeadingZeros(words[topWord]);
        int keep = (int)Math.min(Math.min(53, top + 1), Math.max(0, 1075L + top - shift));
        if (keep == 0) {
            if (1075L + top - shift < 0) return 0;
            return anyBitsBelow(words, top) ? Double.MIN_VALUE : 0;
        }
        int bottom = top - keep + 1;
        int word = bottom >>> 6, bit = bottom & 63;
        long significand = words[word] >>> bit;
        if (bit != 0 && word + 1 < words.length) significand |= words[word + 1] << (64 - bit);
        significand = maskBelow(significand, keep);
        int guard = top - keep;
        if (guard >= 0 && (words[guard >>> 6] & (1L << (guard & 63))) != 0) {
            if ((significand & 1) != 0 || anyBitsBelow(words, guard)) ++significand;
        }
        return Math.scalb((double)significand, (int)Math.max(-2099, top - keep + 1L - shift));
    }

    private static boolean anyBitsBelow(long[] words, int bit) {
        int word = bit >>> 6;
        if (maskBelow(words[word], bit & 63) != 0L) return true;
        for (int i = 0; i < word; ++i) if (words[i] != 0L) return true;
        return false;
    }


    /** Preserve the historical seed domain and mix only its formerly discarded bits. */



    private static double unsignedDouble(long value) {
        return (double) (value >>> 1) * 2.0D + (value & 1L);
    }









    /** Floor division by positive int; allocation occurs only at the placement API boundary. */

    private static long multiplyHigh(long a, long b) {
        return Math.multiplyHigh(a, b) + (a < 0L ? b : 0L) + (b < 0L ? a : 0L);
    }

    /** Narrow path: retain the established 64-by-53-bit arithmetic. */
    public static void scale(long coordinate, double scale, int octave, PreciseNoiseCoordinate.Split out) {
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
        boolean negative = (coordinate < 0L) ^ (bits < 0L);
        long magnitude = coordinate < 0L ? -coordinate : coordinate;
        long p0 = magnitude * mantissa;
        long p1 = multiplyHigh(magnitude, mantissa);
        int productBits = p1 != 0L ? 128 - Long.numberOfLeadingZeros(p1) : 64 - Long.numberOfLeadingZeros(p0);
        if (productBits + power > 63 && GenerationContext.current().mode() != GeneratorMode.PATCHED) {
            double failure = Math.scalb(unsignedDouble(p0), power) + Math.scalb(unsignedDouble(p1), power + 64);
            out.failureMagnitude = negative ? -failure : failure;
        }
        if (power >= 0) {
            long integer = power >= 64 ? 0L : p0 << power;
            out.lattice = negative ? -integer : integer;
            out.fraction = 0.0D;
            return;
        }
        int shift = -power;
        long integer = wordAt(p0, p1, 0L, shift);
        long r0 = maskBelow(p0, shift), r1 = maskBelow(p1, shift - 64);
        boolean remainder = (r0 | r1) != 0L;
        long lattice = negative ? -integer - (remainder ? 1L : 0L) : integer;
        if (negative && remainder && shift <= 192) {
            long c0 = -r0;
            long c1 = ~r1 + (r0 == 0L ? 1L : 0L);
            long c2 = ~0L + (r0 == 0L && r1 == 0L ? 1L : 0L);
            r0 = maskBelow(c0, shift);
            r1 = maskBelow(c1, shift - 64);
            double fraction = Math.scalb(unsignedDouble(r0), -shift)
                    + Math.scalb(unsignedDouble(r1), 64 - shift)
                    + Math.scalb(unsignedDouble(maskBelow(c2, shift - 128)), 128 - shift);
            if (fraction >= 1.0D) { ++lattice; fraction = 0.0D; }
            out.lattice = lattice;
            out.fraction = fraction;
            return;
        }
        double fraction = Math.scalb(unsignedDouble(r0), -shift) + Math.scalb(unsignedDouble(r1), 64 - shift);
        if (negative && remainder && shift > 192) fraction = 1.0D - fraction;
        if (fraction >= 1.0D) { ++lattice; fraction = 0.0D; }
        out.lattice = lattice;
        out.fraction = fraction;
    }

    /**
     * Exact 128x53-bit product followed by a binary exponent. Only the final
     * lattice is reduced modulo 2^64; octave division precedes that reduction.
     */

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
