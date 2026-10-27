package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Scratch;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;

public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double TARGET_DEVIATION = 0.3333333333333333D;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NormalNoise.NoiseParameters parameters;

    /** @deprecated */
    @Deprecated
    public static NormalNoise createLegacyNetherBiome(RandomSource pRandom, NormalNoise.NoiseParameters pParameters) {
        return new NormalNoise(pRandom, pParameters, false);
    }

    public static NormalNoise create(RandomSource pRandom, int pFirstOctave, double... pAmplitudes) {
        return create(pRandom, new NormalNoise.NoiseParameters(pFirstOctave, new DoubleArrayList(pAmplitudes)));
    }

    public static NormalNoise create(RandomSource pRandom, NormalNoise.NoiseParameters pParameters) {
        return new NormalNoise(pRandom, pParameters, true);
    }

    private NormalNoise(RandomSource pRandom, NormalNoise.NoiseParameters pParameters, boolean pUseLegacyNetherBiome) {
        int i = pParameters.firstOctave;
        DoubleList doublelist = pParameters.amplitudes;
        this.parameters = pParameters;
        if (pUseLegacyNetherBiome) {
            this.first = PerlinNoise.create(pRandom, i, doublelist);
            this.second = PerlinNoise.create(pRandom, i, doublelist);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(pRandom, i, doublelist);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(pRandom, i, doublelist);
        }

        int j = Integer.MAX_VALUE;
        int k = Integer.MIN_VALUE;
        DoubleListIterator doublelistiterator = doublelist.iterator();

        while (doublelistiterator.hasNext()) {
            int l = doublelistiterator.nextIndex();
            double d0 = doublelistiterator.nextDouble();
            if (d0 != 0.0D) {
                j = Math.min(j, l);
                k = Math.max(k, l);
            }
        }

        this.valueFactor = 0.16666666666666666D / expectedDeviation(k - j);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }

    private static double expectedDeviation(int pOctaves) {
        return 0.1D * (1.0D + 1.0D / (double) (pOctaves + 1));
    }

    public double getValue(double pX, double pY, double pZ) {
        double d0 = pX * 1.0181268882175227D;
        double d1 = pY * 1.0181268882175227D;
        double d2 = pZ * 1.0181268882175227D;
        return (this.first.getValue(pX, pY, pZ) + this.second.getValue(d0, d1, d2)) * this.valueFactor;
    }

    public double getValue(long cellX, double fracX, double y, long cellZ, double fracZ) {
        long cellY = (long) Math.floor(y);
        return this.getValue(cellX, fracX, cellY, y - (double) cellY, cellZ, fracZ);
    }

    public double getValue(long cellX, double fracX, long cellY, double fracY, long cellZ, double fracZ) {
        double factor = 1.0181268882175227D;
        PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
        PreciseNoiseCoordinate.scaledSplit(cellX, fracX, factor, scratch);
        long secondX = scratch.lattice;
        double secondFracX = scratch.fraction;
        PreciseNoiseCoordinate.scaledSplit(cellY, fracY, factor, scratch);
        long secondY = scratch.lattice;
        double secondFracY = scratch.fraction;
        PreciseNoiseCoordinate.scaledSplit(cellZ, fracZ, factor, scratch);
        long secondZ = scratch.lattice;
        double secondFracZ = scratch.fraction;
        return (this.first.getValue(cellX, fracX, cellY, fracY, cellZ, fracZ) + this.second.getValue(secondX, secondFracX, secondY, secondFracY, secondZ, secondFracZ)) * this.valueFactor;
    }

    public double getValueScaled(long blockX, double xzScale, double y, long blockZ) {
        if (FarlandsMode.usesPreciseCoordinates())
            return getValueScaledShifted(blockX >> 63, blockX, xzScale, 0.0D, y, blockZ >> 63, blockZ, 0.0D);
        long cellY = (long) Math.floor(y);
        return this.getValueScaled(blockX, xzScale, cellY, y - (double) cellY, blockZ);
    }

    /** Samples a block-coordinate noise function while splitting all three scaled axes. */
    public double getValueScaled(long blockX, double xzScale, long blockY, double yScale, long blockZ) {
        if (FarlandsMode.usesPreciseCoordinates())
            return getValueScaled(blockX >> 63, blockX, xzScale, blockY, yScale, blockZ >> 63, blockZ);
        PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
        PreciseNoiseCoordinate.scale(blockX, xzScale, scratch);
        long firstX = scratch.lattice;
        double firstFracX = scratch.fraction;
        PreciseNoiseCoordinate.scale(blockY, yScale, scratch);
        long firstY = scratch.lattice;
        double firstFracY = scratch.fraction;
        PreciseNoiseCoordinate.scale(blockZ, xzScale, scratch);
        long firstZ = scratch.lattice;
        double firstFracZ = scratch.fraction;
        double factor = 1.0181268882175227D;
        PreciseNoiseCoordinate.scale(blockX, xzScale * factor, scratch);
        long secondX = scratch.lattice;
        double secondFracX = scratch.fraction;
        PreciseNoiseCoordinate.scale(blockY, yScale * factor, scratch);
        long secondY = scratch.lattice;
        double secondFracY = scratch.fraction;
        PreciseNoiseCoordinate.scale(blockZ, xzScale * factor, scratch);
        long secondZ = scratch.lattice;
        double secondFracZ = scratch.fraction;
        return (this.first.getValue(firstX, firstFracX, firstY, firstFracY, firstZ, firstFracZ) + this.second.getValue(secondX, secondFracX, secondY, secondFracY, secondZ, secondFracZ)) * this.valueFactor;
    }

    public double getValueScaledShifted(long blockX, double xzScale, double shiftX, double y, long blockZ, double shiftZ) {
        if (FarlandsMode.usesPreciseCoordinates())
            return getValueScaledShifted(blockX >> 63, blockX, xzScale, shiftX, y, blockZ >> 63, blockZ, shiftZ);
        PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
        PreciseNoiseCoordinate.scale(blockX, xzScale, scratch);
        long shiftedFirstX = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftX);
        double firstFracX = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftX);
        PreciseNoiseCoordinate.scale(blockZ, xzScale, scratch);
        long shiftedFirstZ = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftZ);
        double firstFracZ = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftZ);
        double factor = 1.0181268882175227D;
        PreciseNoiseCoordinate.scale(blockX, xzScale * factor, scratch);
        long shiftedSecondX = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftX * factor);
        double secondFracX = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftX * factor);
        PreciseNoiseCoordinate.scale(blockZ, xzScale * factor, scratch);
        long shiftedSecondZ = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftZ * factor);
        double secondFracZ = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftZ * factor);
        return (this.first.getValue(shiftedFirstX, firstFracX, y, shiftedFirstZ, firstFracZ) + this.second.getValue(shiftedSecondX, secondFracX, y * factor, shiftedSecondZ, secondFracZ)) * this.valueFactor;
    }

    public NormalNoise.NoiseParameters parameters() {
        return this.parameters;
    }

    public double getValueScaled(Int1088 x, double scale, long y, double yScale, Int1088 z) {
        if (x.fitsLong() && z.fitsLong()) return getValueScaled(x.lowLimb(), scale, y, yScale, z.lowLimb());
        try (Int1088Scratch sx = Int1088Scratch.acquire(); Int1088Scratch sy = Int1088Scratch.acquire(); Int1088Scratch sz = Int1088Scratch.acquire()) {
            sx.copyFrom(x); sz.copyFrom(z); sy.limbs()[0] = y;
            return (this.first.getValueScaled(sx.limbs(), 0, 17, scale, 0, sy.limbs(), 0, 1, yScale, 0, sz.limbs(), 0, 17, 0)
                    + this.second.getValueScaled(sx.limbs(), 0, 17, scale * INPUT_FACTOR, 0, sy.limbs(), 0, 1, yScale * INPUT_FACTOR, 0, sz.limbs(), 0, 17, 0)) * this.valueFactor;
        }
    }

    public double getValueScaledShifted(Int1088 x, double scale, double shiftX, double y, Int1088 z, double shiftZ) {
        if (x.fitsLong() && z.fitsLong()) return getValueScaledShifted(x.lowLimb(), scale, shiftX, y, z.lowLimb(), shiftZ);
        return (this.first.getValueScaled(x, scale, shiftX, Int1088.ZERO, 1, y, z, shiftZ)
                + this.second.getValueScaled(x, scale * INPUT_FACTOR, shiftX * INPUT_FACTOR,
                Int1088.ZERO, 1, y * INPUT_FACTOR, z, shiftZ * INPUT_FACTOR)) * this.valueFactor;
    }

    public double getValueScaled(long[] x, int xo, int xl, double scale, long y, double yScale, long[] z, int zo, int zl) {
        if (WorldgenCoordinate.fitsLong(x, xo, xl) && WorldgenCoordinate.fitsLong(z, zo, zl)) return getValueScaled(x[xo], scale, y, yScale, z[zo]);
        try (Int1088Scratch sy = Int1088Scratch.acquire()) {
            sy.limbs()[0] = y;
            return (this.first.getValueScaled(x, xo, xl, scale, 0, sy.limbs(), 0, 1, yScale, 0, z, zo, zl, 0)
                    + this.second.getValueScaled(x, xo, xl, scale * INPUT_FACTOR, 0, sy.limbs(), 0, 1, yScale * INPUT_FACTOR, 0, z, zo, zl, 0)) * this.valueFactor;
        }
    }

    public double getValueScaledShifted(long[] x, int xo, int xl, double scale, double shiftX, double y,
                                        long[] z, int zo, int zl, double shiftZ) {
        if (WorldgenCoordinate.fitsLong(x, xo, xl) && WorldgenCoordinate.fitsLong(z, zo, zl)) return getValueScaledShifted(x[xo], scale, shiftX, y, z[zo], shiftZ);
        try (Int1088Scratch sy = Int1088Scratch.acquire()) {
            return (this.first.getValueScaled(x, xo, xl, scale, shiftX, sy.limbs(), 0, 1, 1, y, z, zo, zl, shiftZ)
                    + this.second.getValueScaled(x, xo, xl, scale * INPUT_FACTOR, shiftX * INPUT_FACTOR,
                    sy.limbs(), 0, 1, 1, y * INPUT_FACTOR, z, zo, zl, shiftZ * INPUT_FACTOR)) * this.valueFactor;
        }
    }

    public double getValueScaledAxes(Int1088 x, Int1088 y, Int1088 z, double scale) {
        return (this.first.getValueScaled(x, scale, 0, y, scale, 0, z, 0)
                + this.second.getValueScaled(x, scale * INPUT_FACTOR, 0, y, scale * INPUT_FACTOR, 0, z, 0)) * this.valueFactor;
    }

    public double getValueScaledAxes(long[] x, int xo, int xl, long[] y, int yo, int yl,
                                     long[] z, int zo, int zl, double scale) {
        return (this.first.getValueScaled(x, xo, xl, scale, 0, y, yo, yl, scale, 0, z, zo, zl, 0)
                + this.second.getValueScaled(x, xo, xl, scale * INPUT_FACTOR, 0, y, yo, yl,
                scale * INPUT_FACTOR, 0, z, zo, zl, 0)) * this.valueFactor;
    }

    public double getValueScaled(long xHi, long xLo, double scale, long y, double yScale, long zHi, long zLo) {
        return (this.first.getValueScaled(xHi, xLo, scale, 0.0D, y >> 63, y, yScale, 0.0D, zHi, zLo, 0.0D)
                + this.second.getValueScaled(xHi, xLo, scale * INPUT_FACTOR, 0.0D, y >> 63, y, yScale * INPUT_FACTOR, 0.0D, zHi, zLo, 0.0D)) * this.valueFactor;
    }

    public double getValueScaledShifted(long xHi, long xLo, double scale, double shiftX, double y, long zHi, long zLo, double shiftZ) {
        return (this.first.getValueScaled(xHi, xLo, scale, shiftX, 0L, 0L, 1.0D, y, zHi, zLo, shiftZ)
                + this.second.getValueScaled(xHi, xLo, scale * INPUT_FACTOR, shiftX * INPUT_FACTOR, 0L, 0L, 1.0D, y * INPUT_FACTOR, zHi, zLo, shiftZ * INPUT_FACTOR)) * this.valueFactor;
    }

    /** ShiftB rotates X into the vertical noise axis; that axis is full width too. */
    public double getValueScaledAxes(long xHi, long xLo, long yHi, long yLo, long zHi, long zLo, double scale) {
        return (this.first.getValueScaled(xHi, xLo, scale, 0.0D, yHi, yLo, scale, 0.0D, zHi, zLo, 0.0D)
                + this.second.getValueScaled(xHi, xLo, scale * INPUT_FACTOR, 0.0D, yHi, yLo, scale * INPUT_FACTOR, 0.0D, zHi, zLo, 0.0D)) * this.valueFactor;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder p_192847_) {
        p_192847_.append("NormalNoise {");
        p_192847_.append("first: ");
        this.first.parityConfigString(p_192847_);
        p_192847_.append(", second: ");
        this.second.parityConfigString(p_192847_);
        p_192847_.append("}");
    }

    public static record NoiseParameters(int firstOctave, DoubleList amplitudes) {
        public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create((p_192865_) -> {
            return p_192865_.group(Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave), Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)).apply(p_192865_, NormalNoise.NoiseParameters::new);
        });
        public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.create(Registry.NOISE_REGISTRY, DIRECT_CODEC);

        public NoiseParameters(int p_192861_, List<Double> p_192862_) {
            this(p_192861_, new DoubleArrayList(p_192862_));
        }

        public NoiseParameters(int p_192857_, double p_192858_, double... p_192859_) {
            this(p_192857_, Util.make(new DoubleArrayList(p_192859_), (p_210636_) -> {
                p_210636_.add(0, p_192858_);
            }));
        }
    }
}
