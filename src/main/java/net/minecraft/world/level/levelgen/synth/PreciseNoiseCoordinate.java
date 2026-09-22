package net.minecraft.world.level.levelgen.synth;

/** Primitive split-coordinate arithmetic used by the precise generator mode. */
public final class PreciseNoiseCoordinate {
   private static final long MASK_32 = 0xffffffffL;
   private static final ThreadLocal<Split> SCRATCH = ThreadLocal.withInitial(Split::new);

   private PreciseNoiseCoordinate() {
   }

   public static boolean needsPrecisePath(long coordinate) {
      return coordinate < -9007199254740992L || coordinate > 9007199254740992L;
   }

   /** Splits coordinate * scale, retaining the lattice modulo 2^64 for noise permutation lookup. */
   public static long scaledLattice(long coordinate, double scale) {
      Split split = SCRATCH.get();
      scale(coordinate, scale, split);
      return split.lattice;
   }

   /** Returns the fraction paired with scaledLattice; call both methods as a pair. */
   public static double scaledFraction(long coordinate, double scale) {
      Split split = SCRATCH.get();
      scale(coordinate, scale, split);
      return split.fraction;
   }

   /** Computes the lattice/fraction pair from one 64x53-bit product. */
   public static Split threadScratch() {
      return SCRATCH.get();
   }

   public static void scale(long coordinate, double scale, Split out) {
      if (!Double.isFinite(scale) || scale == 0.0D) {
         out.lattice = 0L;
         out.fraction = 0.0D;
         return;
      }

      long bits = Double.doubleToRawLongBits(scale);
      long mantissa = bits & 0x000fffffffffffffL;
      int encodedExponent = (int)((bits >>> 52) & 2047L);
      int shift;
      if (encodedExponent == 0) {
         shift = -1074;
      } else {
         mantissa |= 0x0010000000000000L;
         shift = encodedExponent - 1075;
      }

      long magnitude = coordinate < 0L ? -coordinate : coordinate;
      long p00 = (magnitude & MASK_32) * (mantissa & MASK_32);
      long p01 = (magnitude & MASK_32) * (mantissa >>> 32);
      long p10 = (magnitude >>> 32) * (mantissa & MASK_32);
      long p11 = (magnitude >>> 32) * (mantissa >>> 32);
      long mid = (p00 >>> 32) + (p01 & MASK_32) + (p10 & MASK_32);
      long lo = (p00 & MASK_32) | (mid << 32);
      long hi = p11 + (p01 >>> 32) + (p10 >>> 32) + (mid >>> 32);

      long integer;
      long remainderLo;
      long remainderHi;
      int fractionShift;
      if (shift >= 0) {
         integer = shift >= 64 ? 0L : lo << shift;
         remainderLo = 0L;
         remainderHi = 0L;
         fractionShift = 0;
      } else {
         fractionShift = -shift;
         if (fractionShift >= 128) {
            integer = 0L;
            remainderLo = lo;
            remainderHi = hi;
         } else if (fractionShift >= 64) {
            int highBits = fractionShift - 64;
            integer = hi >>> highBits;
            remainderLo = lo;
            remainderHi = highBits == 0 ? 0L : hi & ((1L << highBits) - 1L);
         } else {
            integer = lo >>> fractionShift | hi << (64 - fractionShift);
            remainderLo = lo & ((1L << fractionShift) - 1L);
            remainderHi = 0L;
         }
      }

      boolean hasFraction = remainderLo != 0L || remainderHi != 0L;
      boolean negative = (coordinate < 0L) ^ (bits < 0L);
      long lattice = negative ? -integer - (hasFraction ? 1L : 0L) : integer;
      if (!hasFraction) {
         out.lattice = lattice;
         out.fraction = 0.0D;
         return;
      }

      double fraction = remainderToFraction(remainderLo, remainderHi, fractionShift, negative);
      if (fraction >= 1.0D) {
         ++lattice;
         fraction = 0.0D;
      } else {
         fraction = normalize(fraction);
      }

      out.lattice = lattice;
      out.fraction = fraction;
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
      Split split = SCRATCH.get();
      scale(lattice, factor, split);
      return split.lattice + (long)Math.floor(split.fraction + fraction * factor);
   }

   public static double scaledSplitFraction(long lattice, double fraction, double factor) {
      Split split = SCRATCH.get();
      scale(lattice, factor, split);
      return normalize(split.fraction + fraction * factor);
   }

   public static void scaledSplit(long lattice, double fraction, double factor, Split out) {
      scale(lattice, factor, out);
      double combined = out.fraction + fraction * factor;
      long carry = (long)Math.floor(combined);
      out.lattice += carry;
      out.fraction = normalize(combined);
   }

   private static double remainderToFraction(long remainderLo, long remainderHi, int shift, boolean negative) {
      if (shift <= 0) {
         return 0.0D;
      }
      if (shift <= 64) {
         if (remainderLo == 0L) {
            return 0.0D;
         }
         long remainder = remainderLo;
         if (negative) {
            remainder = shift == 64 ? -remainder : (1L << shift) - remainder;
         }
         return Math.scalb(unsignedToDouble(remainder), -shift);
      }
      double unsignedFraction = Math.scalb(unsignedToDouble(remainderLo), -shift) + Math.scalb(unsignedToDouble(remainderHi), 64 - shift);
      return negative ? 1.0D - unsignedFraction : unsignedFraction;
   }

   private static double unsignedToDouble(long value) {
      return value >= 0L ? (double)value : (double)(value & Long.MAX_VALUE) + 0x1.0p63;
   }

   public static double normalize(double fraction) {
      if (Double.isNaN(fraction)) return 0.0D;
      fraction -= Math.floor(fraction);
      if (fraction < 0.0D) return 0.0D;
      return fraction >= 1.0D ? Math.nextDown(1.0D) : fraction;
   }

   /** Mutable lattice/fraction pair reused on the world-generation hot path. */
   public static final class Split {
      public long lattice;
      public double fraction;
   }
}
