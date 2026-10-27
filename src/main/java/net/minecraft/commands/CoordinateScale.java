package net.minecraft.commands;

import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;

/** Exact binary scaling of split horizontal coordinates, without a global double. */
public final class CoordinateScale {
    // Fixed-point input has 1074 fractional bits; multiplication needs 53 more bits.
    private static final int WORDS = 36;
    private static final int FRACTION_BITS = 1074;

    private CoordinateScale() {}

    public static SectorVec3 scale(SectorVec3 position, double scale) {
        if (!(scale > 0.0D) || !Double.isFinite(scale))
            throw new IllegalArgumentException("Invalid dimension scale: " + scale);
        Axis x = scaleAxis(position.blockX(), position.subX(), scale);
        Axis z = scaleAxis(position.blockZ(), position.subZ(), scale);
        return SectorVec3.fromBlockAndFraction(x.block, x.fraction, position.y(), z.block, z.fraction);
    }

    private record Axis(Int1088 block, double fraction) {}

    private static Axis scaleAxis(Int1088 block, double fraction, double scale) {
        boolean negative = block.signum() < 0;
        long[] magnitude = new long[WORDS];
        long carry = negative ? 1 : 0;
        for (int i = 0; i < Int1088.LIMBS; i++) {
            long word = block.limb(i);
            if (negative) {
                word = ~word + carry;
                carry = carry != 0 && word == 0 ? 1 : 0;
            }
            putBits(magnitude, word, FRACTION_BITS + 64 * i);
        }
        long[] fractional = new long[WORDS];
        if (fraction != 0.0D) {
            long bits = Double.doubleToRawLongBits(fraction);
            int exponent = (int)((bits >>> 52) & 0x7ff);
            long significand = bits & 0xfffffffffffffL;
            if (exponent != 0) significand |= 1L << 52;
            putBits(fractional, significand, exponent == 0 ? 0 : exponent - 1);
        }
        carry = 0;
        for (int i = 0; i < WORDS; i++) {
            long a = magnitude[i], b = fractional[i];
            if (negative) {
                long r = a - b - carry;
                carry = Long.compareUnsigned(a, b) < 0 || carry != 0 && a == b ? 1 : 0;
                magnitude[i] = r;
            } else {
                long r = a + b;
                long c = Long.compareUnsigned(r, a) < 0 ? 1 : 0;
                long sum = r + carry;
                carry = c | (Long.compareUnsigned(sum, r) < 0 ? 1 : 0);
                magnitude[i] = sum;
            }
        }
        long bits = Double.doubleToRawLongBits(scale);
        int exponent = (int)((bits >>> 52) & 0x7ff);
        long factor = bits & 0xfffffffffffffL;
        if (exponent != 0) factor |= 1L << 52;
        int shift = FRACTION_BITS - (exponent == 0 ? -1074 : exponent - 1075);
        carry = 0;
        for (int i = 0; i < WORDS; i++) {
            long word = magnitude[i];
            long low = word * factor;
            long sum = low + carry;
            carry = Math.multiplyHigh(word, factor) + (word < 0 ? factor : 0)
                    + (Long.compareUnsigned(sum, low) < 0 ? 1 : 0);
            magnitude[i] = sum;
        }
        long[] whole = new long[Int1088.LIMBS];
        for (int i = 0; i < whole.length; i++) whole[i] = wordAt(magnitude, shift + 64 * i);
        int high = highestBit(magnitude, WORDS * 64);
        boolean overflow = high >= shift + Int1088.BITS - 1;
        if (negative && high == shift + Int1088.BITS - 1) {
            overflow = anyBelow(magnitude, high) || high > shift + Int1088.BITS - 1;
        }
        if (overflow) return new Axis(negative ? Int1088.MIN : Int1088.MAX, negative ? 0.0D : Math.nextDown(1.0D));
        double part = fractionalDouble(magnitude, shift);
        if (negative) {
            // Negate the unsigned magnitude, including MIN, directly in limbs.
            carry = 1;
            for (int i = 0; i < whole.length; i++) {
                long r = ~whole[i] + carry;
                carry = carry != 0 && r == 0 ? 1 : 0;
                whole[i] = r;
            }
            Int1088 result = Int1088.fromFixedLimbs(whole);
            if (highestBit(magnitude, shift) >= 0) {
                if (result.equals(Int1088.MIN)) return new Axis(Int1088.MIN, 0.0D);
                result = result.subtractSaturated(Int1088.ONE);
                part = complementedFraction(magnitude, shift);
                if (part == 1.0D) part = Math.nextDown(1.0D);
            }
            return new Axis(result, part);
        }
        Int1088 result = Int1088.fromFixedLimbs(whole);
        if (part == 1.0D) {
            if (result.equals(Int1088.MAX)) return new Axis(result, Math.nextDown(1.0D));
            return new Axis(result.addSaturated(Int1088.ONE), 0.0D);
        }
        return new Axis(result, part);
    }

    private static void putBits(long[] words, long value, int bit) {
        int index = bit >>> 6, offset = bit & 63;
        words[index] |= value << offset;
        if (offset != 0 && index + 1 < words.length) words[index + 1] |= value >>> (64 - offset);
    }

    private static long wordAt(long[] words, int bit) {
        int index = bit >>> 6, offset = bit & 63;
        long low = index < words.length ? words[index] >>> offset : 0;
        return offset == 0 || index + 1 >= words.length ? low : low | words[index + 1] << (64 - offset);
    }

    private static boolean bit(long[] words, int bit) {
        return bit >= 0 && bit < words.length * 64 && (words[bit >>> 6] & (1L << (bit & 63))) != 0;
    }

    private static int highestBit(long[] words, int exclusive) {
        for (int i = Math.min(exclusive, words.length * 64) - 1; i >= 0; i--) if (bit(words, i)) return i;
        return -1;
    }

    private static boolean anyBelow(long[] words, int exclusive) {
        return highestBit(words, exclusive) >= 0;
    }

    private static double fractionalDouble(long[] words, int shift) {
        int high = highestBit(words, shift);
        if (high < 0) return 0.0D;
        int count = Math.min(53, high - shift + 1075);
        if (count <= 0) return high == shift - 1075 && anyBelow(words, high) ? Double.MIN_VALUE : 0.0D;
        long significand = 0;
        for (int i = 0; i < count; i++) significand = (significand << 1) | (bit(words, high - i) ? 1 : 0);
        int guard = high - count;
        if (bit(words, guard) && (anyBelow(words, guard) || (significand & 1) != 0)) significand++;
        return Math.scalb((double)significand, high - count + 1 - shift);
    }

    private static double complementedFraction(long[] words, int shift) {
        long[] complement = new long[WORDS];
        long carry = 1;
        int count = (shift + 63) >>> 6;
        for (int i = 0; i < count; i++) {
            long value = ~(i < words.length ? words[i] : 0L) + carry;
            carry = carry != 0 && value == 0 ? 1 : 0;
            complement[i] = value;
        }
        int lastBits = shift & 63;
        if (lastBits != 0) complement[count - 1] &= (1L << lastBits) - 1;
        return fractionalDouble(complement, shift);
    }
}
