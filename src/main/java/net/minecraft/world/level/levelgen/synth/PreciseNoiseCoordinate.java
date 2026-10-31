package net.minecraft.world.level.levelgen.synth;

/** Primitive split-coordinate arithmetic used by the precise generator mode. */
public final class PreciseNoiseCoordinate {
    private static final long MASK_32 = 0xffffffffL;
    private static final ThreadLocal<Split> SCRATCH = ThreadLocal.withInitial(Split::new);

    private PreciseNoiseCoordinate() {
    }

    public static boolean needsPrecisePath(long coordinate) {
        return true;
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
        WorldgenCoordinate.scale(coordinate, scale, 0, out);
    }

    private static void scaleLegacy(long coordinate, double scale, Split out) {
        if (!Double.isFinite(scale) || scale == 0.0D) {
            out.lattice = 0L;
            out.fraction = 0.0D;
            return;
        }

        long bits = Double.doubleToRawLongBits(scale);
        long mantissa = bits & 0x000fffffffffffffL;
        int encodedExponent = (int) ((bits >>> 52) & 2047L);
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
        return lattice + (long) Math.floor(fraction + shift);
    }

    public static double shiftedFraction(long lattice, double fraction, double shift) {
        return normalize(fraction + shift);
    }

    /** Multiplies a split coordinate by 2^power, carrying between parts. */
    public static long powerOfTwoLattice(long lattice, double fraction, int power) {
        if (power >= 0)
            return (lattice << power) + (long) Math.floor(Math.scalb(fraction, power));
        int shift = -power;
        long divisor = 1L << shift;
        return (lattice >> shift) + (long) Math.floor(((double) (lattice & (divisor - 1L)) + fraction) / (double) divisor);
    }

    public static double powerOfTwoFraction(long lattice, double fraction, int power) {
        if (power >= 0)
            return normalize(Math.scalb(fraction, power));
        int shift = -power;
        long divisor = 1L << shift;
        return normalize(((double) (lattice & (divisor - 1L)) + fraction) / (double) divisor);
    }

    /** Multiplies a split coordinate by a finite arbitrary factor. */
    public static long scaledSplitLattice(long lattice, double fraction, double factor) {
        Split split = SCRATCH.get();
        scale(lattice, factor, split);
        return split.lattice + (long) Math.floor(split.fraction + fraction * factor);
    }

    public static double scaledSplitFraction(long lattice, double fraction, double factor) {
        Split split = SCRATCH.get();
        scale(lattice, factor, split);
        return normalize(split.fraction + fraction * factor);
    }

    public static void scaledSplit(long lattice, double fraction, double factor, Split out) {
        scale(lattice, factor, out);
        double combined = out.fraction + fraction * factor;
        long carry = (long) Math.floor(combined);
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
        return value >= 0L ? (double) value : (double) (value & Long.MAX_VALUE) + 0x1.0p63;
    }

    public static double normalize(double fraction) {
        if (Double.isNaN(fraction))
            return 0.0D;
        fraction -= Math.floor(fraction);
        if (fraction < 0.0D)
            return 0.0D;
        return fraction >= 1.0D ? Math.nextDown(1.0D) : fraction;
    }

    /** Mutable lattice/fraction pair reused on the world-generation hot path. */
    public static final class Split {
        public long lattice;
        public double fraction;
        /** Only populated after exact arithmetic classifies a signed-long overflow. */
        public double failureMagnitude = Double.NaN;
        // Enough for an Int1088 times any finite double at ordinary noise octaves.
        // Retained separately so the public modulo-2^64 arithmetic stays unchanged.
        long[] extendedLattice = new long[35];
        int extendedLength;
        long vertex0;
        long vertex1;

        /** Compute adjoining vertices together; their upper words usually match. */
        void computeVertices() {
            if (extendedLength == 0) {
                vertex0 = lattice;
                vertex1 = lattice + 1;
                return;
            }
            // A carry or a change in the low word's sign changes the upper-word
            // hash. Keep the general path for both seams.
            if (lattice == -1L || lattice == Long.MAX_VALUE) {
                vertex0 = vertexIndex(0);
                vertex1 = vertexIndex(1);
                return;
            }
            long upperHash = 0;
            long previous = lattice;
            boolean wide = false;
            for (int i = 1; i < extendedLength; ++i) {
                long word = extendedLattice[i];
                long extra = word ^ (previous >> 63);
                if (extra != 0) {
                    wide = true;
                    upperHash ^= net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(
                            extra + i * 0x9e3779b97f4a7c15L);
                }
                previous = word;
            }
            vertex0 = wide ? net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(lattice ^ upperHash) : lattice;
            vertex1 = wide ? net.minecraft.world.level.levelgen.RandomSupport.mixStafford13((lattice + 1) ^ upperHash) : lattice + 1;
        }

        void addExtended(long offset) {
            if (extendedLength == 0 || offset == 0) return;
            long old = extendedLattice[0];
            long sum = old + offset;
            extendedLattice[0] = sum;
            long carry = Long.compareUnsigned(sum, old) < 0 ? 1 : 0;
            long extension = offset >> 63;
            for (int i = 1; i < extendedLength; ++i) {
                old = extendedLattice[i];
                sum = old + extension + carry;
                carry = carry == 0 ? (Long.compareUnsigned(sum, old) < 0 ? 1 : 0)
                        : (Long.compareUnsigned(sum, old) <= 0 ? 1 : 0);
                extendedLattice[i] = sum;
            }
        }

        /** Hash each vertex, not each sample region, so adjoining cells share gradients. */
        long vertexIndex(int step) {
            long low = lattice + step;
            if (extendedLength == 0) return low;
            long hash = low;
            long previous = low;
            long carry = step != 0 && low == 0 ? 1 : 0;
            boolean wide = false;
            for (int i = 1; i < extendedLength; ++i) {
                long word = extendedLattice[i] + carry;
                carry = carry != 0 && word == 0 ? 1 : 0;
                long extra = word ^ (previous >> 63);
                if (extra != 0) {
                    wide = true;
                    hash ^= net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(
                            extra + i * 0x9e3779b97f4a7c15L);
                }
                previous = word;
            }
            // Keep the established signed-long domain, but do not introduce a
            // new 256-cell period within each distant high-word region.
            return wide ? net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(hash) : low;
        }
    }
}
