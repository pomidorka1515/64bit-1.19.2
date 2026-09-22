package net.minecraft.world.level.levelgen.synth;

/** Primitive split-coordinate arithmetic used by the precise generator mode. */
public final class PreciseNoiseCoordinate {
   private static final long MASK_32 = 0xffffffffL;
   private static final double TWO_TO_32 = 0x1.0p32;
   private static final double TWO_TO_64 = 0x1.0p64;

   private PreciseNoiseCoordinate() {
   }

   public static boolean needsPrecisePath(long coordinate) {
      return coordinate < -9007199254740992L || coordinate > 9007199254740992L;
   }

   /** Splits coordinate * scale, retaining the lattice modulo 2^64 for noise permutation lookup. */
   public static long scaledLattice(long coordinate, double scale) {
      if (!Double.isFinite(scale) || scale == 0.0D) return 0L;
      int shift = scaleExponent(scale);
      long p0 = productWord(coordinate, scale, 0);
      long p1 = productWord(coordinate, scale, 1);
      long p2 = productWord(coordinate, scale, 2);
      long p3 = productWord(coordinate, scale, 3);
      long p4 = productWord(coordinate, scale, 4);
      long p5 = productWord(coordinate, scale, 5);
      long p6 = productWord(coordinate, scale, 6);
      long p7 = productWord(coordinate, scale, 7);
      boolean negative = (coordinate < 0L) ^ scaleNegative(scale);
      long magnitude = shift >= 0 ? shiftProductLeft(p0, p1, p2, p3, p4, p5, p6, p7, shift) : shiftProductRight(p0, p1, p2, p3, p4, p5, p6, p7, -shift);
      boolean hasFraction = shift < 0 && hasProductRemainder(p0, p1, p2, p3, p4, p5, p6, p7, -shift);
      long lattice = negative ? -magnitude - (hasFraction ? 1L : 0L) : magnitude;
      if (shift < 0 && roundedFractionCarries(p0, p1, p2, p3, p4, p5, p6, p7, -shift, negative)) {
         ++lattice;
      }

      return lattice;
   }

   /** Returns the fraction paired with scaledLattice; call both methods as a pair. */
   public static double scaledFraction(long coordinate, double scale) {
      if (!Double.isFinite(scale) || scale == 0.0D) return 0.0D;
      int shift = scaleExponent(scale);
      if (shift >= 0) return 0.0D;
      long p0 = productWord(coordinate, scale, 0);
      long p1 = productWord(coordinate, scale, 1);
      long p2 = productWord(coordinate, scale, 2);
      long p3 = productWord(coordinate, scale, 3);
      long p4 = productWord(coordinate, scale, 4);
      long p5 = productWord(coordinate, scale, 5);
      long p6 = productWord(coordinate, scale, 6);
      long p7 = productWord(coordinate, scale, 7);
      boolean negative = (coordinate < 0L) ^ scaleNegative(scale);
      return roundedFraction(p0, p1, p2, p3, p4, p5, p6, p7, -shift, negative);
   }

   /* The lattice and fraction methods intentionally duplicate this primitive
    * calculation: both must make the identical carry decision without making
    * a temporary object in the world-generation hot path. */
   private static boolean roundedFractionCarries(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift, boolean negative) {
      return productRemainderFraction(p0, p1, p2, p3, p4, p5, p6, p7, shift, negative) >= 1.0D;
   }

   private static double roundedFraction(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift, boolean negative) {
      double fraction = productRemainderFraction(p0, p1, p2, p3, p4, p5, p6, p7, shift, negative);
      return fraction >= 1.0D ? 0.0D : normalize(fraction);
   }

   public static long shiftedLattice(long lattice, double fraction, double shift) {
      return lattice + (long)Math.floor(fraction + shift);
   }

   public static double shiftedFraction(long lattice, double fraction, double shift) {
      return normalize(fraction + shift);
   }

   /** Multiplies a split coordinate by 2^power, carrying between parts. */
   public static long powerOfTwoLattice(long lattice, double fraction, int power) {
      if (power >= 0) return (lattice << power) + (long)Math.floor(Math.scalb(fraction, power));
      int shift = -power;
      long divisor = 1L << shift;
      return (lattice >> shift) + (long)Math.floor(((double)(lattice & (divisor - 1L)) + fraction) / (double)divisor);
   }

   public static double powerOfTwoFraction(long lattice, double fraction, int power) {
      if (power >= 0) return normalize(Math.scalb(fraction, power));
      int shift = -power;
      long divisor = 1L << shift;
      return normalize(((double)(lattice & (divisor - 1L)) + fraction) / (double)divisor);
   }

   /** Multiplies a split coordinate by a finite arbitrary factor. */
   public static long scaledSplitLattice(long lattice, double fraction, double factor) {
      long productLattice = scaledLattice(lattice, factor);
      double productFraction = scaledFraction(lattice, factor);
      return productLattice + (long)Math.floor(productFraction + fraction * factor);
   }

   public static double scaledSplitFraction(long lattice, double fraction, double factor) {
      return normalize(scaledFraction(lattice, factor) + fraction * factor);
   }

   private static int scaleExponent(double scale) {
      long bits = Double.doubleToRawLongBits(scale);
      int exponent = (int)((bits >>> 52) & 2047L);
      return exponent == 0 ? -1074 : exponent - 1075;
   }

   private static boolean scaleNegative(double scale) {
      return Double.doubleToRawLongBits(scale) < 0L;
   }

   private static long productWord(long coordinate, double scale, int word) {
      long magnitude = coordinate < 0L ? -coordinate : coordinate;
      long bits = Double.doubleToRawLongBits(scale);
      long mantissa = bits & 0x000fffffffffffffL;
      if (((bits >>> 52) & 2047L) != 0L) mantissa |= 0x0010000000000000L;
      long a0 = magnitude & 0xffffL;
      long a1 = magnitude >>> 16 & 0xffffL;
      long a2 = magnitude >>> 32 & 0xffffL;
      long a3 = magnitude >>> 48 & 0xffffL;
      long b0 = mantissa & 0xffffL;
      long b1 = mantissa >>> 16 & 0xffffL;
      long b2 = mantissa >>> 32 & 0xffffL;
      long b3 = mantissa >>> 48 & 0xffffL;
      long s0 = a0 * b0;
      long w0 = s0 & 0xffffL;
      long carry = s0 >>> 16;
      long s1 = a1 * b0 + a0 * b1 + carry;
      long w1 = s1 & 0xffffL;
      carry = s1 >>> 16;
      long s2 = a2 * b0 + a1 * b1 + a0 * b2 + carry;
      long w2 = s2 & 0xffffL;
      carry = s2 >>> 16;
      long s3 = a3 * b0 + a2 * b1 + a1 * b2 + a0 * b3 + carry;
      long w3 = s3 & 0xffffL;
      carry = s3 >>> 16;
      long s4 = a3 * b1 + a2 * b2 + a1 * b3 + carry;
      long w4 = s4 & 0xffffL;
      carry = s4 >>> 16;
      long s5 = a3 * b2 + a2 * b3 + carry;
      long w5 = s5 & 0xffffL;
      carry = s5 >>> 16;
      long s6 = a3 * b3 + carry;
      long w6 = s6 & 0xffffL;
      long w7 = s6 >>> 16;
      return word == 0 ? w0 : word == 1 ? w1 : word == 2 ? w2 : word == 3 ? w3 : word == 4 ? w4 : word == 5 ? w5 : word == 6 ? w6 : w7;
   }

   private static long limb(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int index) {
      return index == 0 ? p0 : index == 1 ? p1 : index == 2 ? p2 : index == 3 ? p3 : index == 4 ? p4 : index == 5 ? p5 : index == 6 ? p6 : index == 7 ? p7 : 0L;
   }

   private static long shiftProductLeft(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift) {
      if (shift >= 128) return 0L;
      int wordShift = shift >>> 4;
      int bitShift = shift & 15;
      long result = 0L;
      for (int i = 0; i < 4; ++i) {
         int source = i - wordShift;
         long value = limb(p0, p1, p2, p3, p4, p5, p6, p7, source) << bitShift;
         if (bitShift != 0) value |= limb(p0, p1, p2, p3, p4, p5, p6, p7, source - 1) >>> (16 - bitShift);
         result |= (value & 0xffffL) << (i * 16);
      }
      return result;
   }

   private static long shiftProductRight(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift) {
      if (shift >= 128) return 0L;
      int wordShift = shift >>> 4;
      int bitShift = shift & 15;
      long result = 0L;
      for (int i = 0; i < 4; ++i) {
         int source = i + wordShift;
         long value = limb(p0, p1, p2, p3, p4, p5, p6, p7, source) >>> bitShift;
         if (bitShift != 0) value |= limb(p0, p1, p2, p3, p4, p5, p6, p7, source + 1) << (16 - bitShift);
         result |= (value & 0xffffL) << (i * 16);
      }
      return result;
   }

   private static boolean hasProductRemainder(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift) {
      if (shift >= 128) return p0 != 0L || p1 != 0L || p2 != 0L || p3 != 0L || p4 != 0L || p5 != 0L || p6 != 0L || p7 != 0L;
      for (int i = 0; i < 8 && i * 16 < shift; ++i) {
         int bits = Math.min(16, shift - i * 16);
         long mask = bits == 16 ? 0xffffL : (1L << bits) - 1L;
         if ((limb(p0, p1, p2, p3, p4, p5, p6, p7, i) & mask) != 0L) return true;
      }
      return false;
   }

   private static double productRemainderFraction(long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, int shift, boolean negative) {
      if (shift <= 0) return 0.0D;
      if (shift <= 64) {
         long remainder = p0 | p1 << 16 | p2 << 32 | p3 << 48;
         if (shift < 64) remainder &= (1L << shift) - 1L;
         if (remainder == 0L) return 0.0D;
         if (negative) remainder = shift == 64 ? -remainder : (1L << shift) - remainder;
         return Math.scalb(unsignedToDouble(remainder), -shift);
      }

      int highest = (shift - 1) >>> 4;
      int highBits = shift - highest * 16;
      double result = 0.0D;
      boolean hasRemainder = false;
      for (int i = highest; i >= 0; --i) {
         int bits = i == highest ? highBits : 16;
         long mask = bits == 16 ? 0xffffL : (1L << bits) - 1L;
         long value = limb(p0, p1, p2, p3, p4, p5, p6, p7, i) & mask;
         hasRemainder |= value != 0L;
         result += Math.scalb((double)value, i * 16 - shift);
      }
      return !hasRemainder ? 0.0D : negative ? 1.0D - result : result;
   }

   private static double unsignedToDouble(long value) {
      return value >= 0L ? (double)value : (double)(value & Long.MAX_VALUE) + 0x1.0p63;
   }


   private static long wrappedInteger(double value) {
      if (Double.isNaN(value)) return 0L;
      double remainder = value % TWO_TO_64;
      if (remainder < 0.0D) remainder += TWO_TO_64;
      return remainder >= 0x1.0p63 ? Long.MIN_VALUE + (long)(remainder - 0x1.0p63) : (long)remainder;
   }

   public static double normalize(double fraction) {
      if (Double.isNaN(fraction)) return 0.0D;
      fraction -= Math.floor(fraction);
      if (fraction < 0.0D) return 0.0D;
      return fraction >= 1.0D ? Math.nextDown(1.0D) : fraction;
   }
}
