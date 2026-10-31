package net.minecraft.world.level.levelgen.synth;

import net.minecraft.core.Int1088Scratch;
import net.minecraft.world.level.levelgen.ContextCoordinates;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class BlendedNoise implements DensityFunction.SimpleFunction {
    private static final Codec<Double> SCALE_RANGE = Codec.doubleRange(0.001D, 1000.0D);
    private static final MapCodec<BlendedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec((p_230486_) -> {
        return p_230486_.group(SCALE_RANGE.fieldOf("xz_scale").forGetter((p_230497_) -> {
                            return p_230497_.xzScale;
                        }),
                                SCALE_RANGE.fieldOf("y_scale").forGetter((p_230495_) -> {
                                    return p_230495_.yScale;
                                }),
                                SCALE_RANGE.fieldOf("xz_factor").forGetter((p_230493_) -> {
                                    return p_230493_.xzFactor;
                                }),
                                SCALE_RANGE.fieldOf("y_factor").forGetter((p_230490_) -> {
                                    return p_230490_.yFactor;
                                }),
                                Codec.doubleRange(1.0D, 8.0D).fieldOf("smear_scale_multiplier").forGetter((p_230488_) -> {
                                    return p_230488_.smearScaleMultiplier;
                                }))
                .apply(p_230486_, BlendedNoise::createUnseeded);
    });
    public static final KeyDispatchDataCodec<BlendedNoise> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double maxValue;
    private final double xzScale;
    private final double yScale;
    private static volatile Thread debugCaptureThread;
    private static double debugScaledX;
    private static String debugScaledXSplit;
    private static double debugSelectorLerp;
    private static double debugMinLimit;
    private static double debugMaxLimit;
    private static double debugBlendedDensity;

    public static BlendedNoise createUnseeded(double p_230478_, double p_230479_, double p_230480_, double p_230481_, double p_230482_) {
        return new BlendedNoise(new XoroshiroRandomSource(0L), p_230478_, p_230479_, p_230480_, p_230481_, p_230482_);
    }

    private BlendedNoise(PerlinNoise pMinLimitNoise, PerlinNoise pMaxLimitNoise, PerlinNoise pMainNoise, double pXzScale, double pYScale, double pXzFactor, double pYFactor, double pSmearScaleMultiplier) {
        this.minLimitNoise = pMinLimitNoise;
        this.maxLimitNoise = pMaxLimitNoise;
        this.mainNoise = pMainNoise;
        this.xzScale = pXzScale;
        this.yScale = pYScale;
        this.xzFactor = pXzFactor;
        this.yFactor = pYFactor;
        this.smearScaleMultiplier = pSmearScaleMultiplier;
        this.xzMultiplier = 684.412D * this.xzScale;
        this.yMultiplier = 684.412D * this.yScale;
        this.maxValue = pMinLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    @VisibleForTesting
    public BlendedNoise(RandomSource pRandom, double pXzScale, double pYScale, double pXzFactor, double pYFactor, double pSmearScaleMultiplier) {
        this(PerlinNoise.createLegacyForBlendedNoise(pRandom, IntStream.rangeClosed(-15, 0)), PerlinNoise.createLegacyForBlendedNoise(pRandom, IntStream.rangeClosed(-15, 0)), PerlinNoise.createLegacyForBlendedNoise(pRandom, IntStream.rangeClosed(-7, 0)), pXzScale, pYScale, pXzFactor, pYFactor, pSmearScaleMultiplier);
    }

    public BlendedNoise withNewRandom(RandomSource pRandom) {
        return new BlendedNoise(pRandom, this.xzScale, this.yScale, this.xzFactor, this.yFactor, this.smearScaleMultiplier);
    }

    public double compute(DensityFunction.FunctionContext pContext) {
        if (FarlandsMode.usesPreciseCoordinates()) {
            return this.computePrecise(pContext);
        }

        boolean useLegacy32BitFloor = FarlandsMode.usesLegacyBlendedNoise();
        double d0 = scaledAxis(pContext, true, this.xzMultiplier);
        double d1 = (double) pContext.blockY() * this.yMultiplier;
        double d2 = scaledAxis(pContext, false, this.xzMultiplier);
        double d3 = d0 / this.xzFactor;
        double d4 = d1 / this.yFactor;
        double d5 = d2 / this.xzFactor;
        double d6 = this.yMultiplier * this.smearScaleMultiplier;
        double d7 = d6 / this.yFactor;
        double d8 = 0.0D;
        double d9 = 0.0D;
        double d10 = 0.0D;
        boolean flag = true;
        double d11 = 1.0D;

        for (int i = 0; i < 8; ++i) {
            ImprovedNoise improvednoise = this.mainNoise.getOctaveNoise(i);
            if (improvednoise != null) {
                d10 += improvednoise.noise(PerlinNoise.wrap(d3 * d11), PerlinNoise.wrap(d4 * d11), PerlinNoise.wrap(d5 * d11), d7 * d11, d4 * d11, useLegacy32BitFloor) / d11;
            }

            d11 /= 2.0D;
        }

        double d16 = (d10 / 10.0D + 1.0D) / 2.0D;
        boolean flag1 = d16 >= 1.0D;
        boolean flag2 = d16 <= 0.0D;
        d11 = 1.0D;

        for (int j = 0; j < 16; ++j) {
            double d12 = PerlinNoise.wrap(d0 * d11);
            double d13 = PerlinNoise.wrap(d1 * d11);
            double d14 = PerlinNoise.wrap(d2 * d11);
            double d15 = d6 * d11;
            if (!flag1) {
                ImprovedNoise improvednoise1 = this.minLimitNoise.getOctaveNoise(j);
                if (improvednoise1 != null) {
                    d8 += improvednoise1.noise(d12, d13, d14, d15, d1 * d11, useLegacy32BitFloor) / d11;
                }
            }

            if (!flag2) {
                ImprovedNoise improvednoise2 = this.maxLimitNoise.getOctaveNoise(j);
                if (improvednoise2 != null) {
                    d9 += improvednoise2.noise(d12, d13, d14, d15, d1 * d11, useLegacy32BitFloor) / d11;
                }
            }

            d11 /= 2.0D;
        }

        double d17 = Mth.clampedLerp(d8 / 512.0D, d9 / 512.0D, d16) / 128.0D;
        if (Thread.currentThread() == debugCaptureThread) {
            debugScaledX = d0;
            debugScaledXSplit = null;
            debugSelectorLerp = d16;
            debugMinLimit = d8 / 512.0D;
            debugMaxLimit = d9 / 512.0D;
            debugBlendedDensity = d17;
        }

        return d17;
    }

    private double computePrecise(DensityFunction.FunctionContext context) {
        try (Int1088Scratch x = axis(context, true); Int1088Scratch z = axis(context, false); Int1088Scratch zero = Int1088Scratch.acquire()) {
            zero.setLength(1);
            return computePrecise(context, x, z, zero);
        }
    }

    private static Int1088Scratch axis(DensityFunction.FunctionContext context, boolean x) {
        Int1088Scratch scratch = Int1088Scratch.acquire();
        int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
        for (int i = 0; i < length; i++) scratch.limbs()[i] = x ? context.blockXLimb(i) : context.blockZLimb(i);
        scratch.setLength(length);
        return scratch;
    }

    private static double scaledAxis(DensityFunction.FunctionContext context, boolean x, double scale) {
        if (x ? context.blockXFitsLong() : context.blockZFitsLong()) {
            return FarlandsMode.scaledNoiseCoordinate(x ? context.blockXLow() : context.blockZLow(), scale);
        }
        try (Int1088Scratch scratch = axis(context, x)) {
            return ContextCoordinates.scaled(scratch.limbs(), 0, scratch.length(), scale, 0.0D);
        }
    }

    private double computePrecise(DensityFunction.FunctionContext context, Int1088Scratch x, Int1088Scratch z, Int1088Scratch zero) {
        double y = (double) context.blockY() * this.yMultiplier;
        double d4 = y / this.yFactor;
        double d6 = this.yMultiplier * this.smearScaleMultiplier;
        double d7 = d6 / this.yFactor;
        double selector = 0.0D;
        double octave = 1.0D;
        for (int i = 0; i < 8; ++i) {
            ImprovedNoise noise = this.mainNoise.getOctaveNoise(i);
            if (noise != null) {
                double scaledY = d4 * octave;
                selector += NoiseSampling.sample(noise, x.limbs(), 0, x.length(), this.xzMultiplier / this.xzFactor, 0.0D,
                        zero.limbs(), 0, 1, 1.0D, d4, z.limbs(), 0, z.length(), this.xzMultiplier / this.xzFactor, 0.0D,
                        -i, d7 * octave, scaledY) / octave;
            }
            octave /= 2.0D;
        }

        double selectorLerp = (selector / 10.0D + 1.0D) / 2.0D;
        boolean high = selectorLerp >= 1.0D;
        boolean low = selectorLerp <= 0.0D;
        double min = 0.0D;
        double max = 0.0D;
        octave = 1.0D;
        for (int j = 0; j < 16; ++j) {
            double scaledY = y * octave;
            if (!high) {
                ImprovedNoise noise = this.minLimitNoise.getOctaveNoise(j);
                if (noise != null)
                    min += NoiseSampling.sample(noise, x.limbs(), 0, x.length(), this.xzMultiplier, 0.0D,
                            zero.limbs(), 0, 1, 1.0D, y, z.limbs(), 0, z.length(), this.xzMultiplier, 0.0D,
                            -j, d6 * octave, scaledY) / octave;
            }
            if (!low) {
                ImprovedNoise noise = this.maxLimitNoise.getOctaveNoise(j);
                if (noise != null)
                    max += NoiseSampling.sample(noise, x.limbs(), 0, x.length(), this.xzMultiplier, 0.0D,
                            zero.limbs(), 0, 1, 1.0D, y, z.limbs(), 0, z.length(), this.xzMultiplier, 0.0D,
                            -j, d6 * octave, scaledY) / octave;
            }
            octave /= 2.0D;
        }

        double density = Mth.clampedLerp(min / 512.0D, max / 512.0D, selectorLerp) / 128.0D;
        if (Thread.currentThread() == debugCaptureThread) {
            PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
            if (context.blockXFitsLong())
                PreciseNoiseCoordinate.scale(context.blockXLow(), this.xzMultiplier, scratch);
            else
                WorldgenCoordinate.scale(x.limbs(), 0, x.length(), this.xzMultiplier, 0, scratch);
            // Formatting must not recombine a precise lattice and fraction in double.
            debugScaledX = Double.isNaN(scratch.failureMagnitude) ? scratch.fraction : scratch.failureMagnitude;
            debugScaledXSplit = Double.isNaN(scratch.failureMagnitude)
                    ? formatSplitDebugValue(scratch.lattice, scratch.fraction, 0) : null;
            debugSelectorLerp = selectorLerp;
            debugMinLimit = min / 512.0D;
            debugMaxLimit = max / 512.0D;
            debugBlendedDensity = density;
        }
        return density;
    }

    public static void beginDebugCapture() {
        debugScaledXSplit = null;
        debugScaledX = Double.NaN;
        debugSelectorLerp = Double.NaN;
        debugMinLimit = Double.NaN;
        debugMaxLimit = Double.NaN;
        debugBlendedDensity = Double.NaN;
        debugCaptureThread = Thread.currentThread();
    }

    public static void endDebugCapture() {
        debugCaptureThread = null;
    }

    public static String getDebugString() {
        if (Double.isNaN(debugScaledX)) return "Not evaluated at this position";
        return "Scaled X: " + (debugScaledXSplit != null ? debugScaledXSplit : formatDebugCoordinate(debugScaledX, 0))
                + " Selector lerp: " + formatDebugValue(debugSelectorLerp);
    }

    public static String getDebugString2() {
        return String.format(Locale.ROOT, "BlendedNoise min: %s max: %s density: %s", formatDebugValue(debugMinLimit), formatDebugValue(debugMaxLimit), formatDebugValue(debugBlendedDensity));
    }

    /** Display-only decimal addition; retain lattice precision for ordinary coordinates. */
    static String formatSplitDebugValue(long lattice, double fraction, int digits) {
        if (!Double.isFinite(fraction)) return formatDebugValue(fraction);
        if (lattice > 1_000_000_000L || lattice < -1_000_000_001L
                || lattice == 1_000_000_000L && fraction > 0.0D
                || lattice == -1_000_000_001L && fraction < 1.0D) {
            return formatScientific((double) lattice + fraction);
        }
        return net.minecraft.world.phys.SectorVec3.formatCoordinate(lattice, fraction, digits);
    }

    static String formatDebugCoordinate(double value, int digits) {
        if (!Double.isFinite(value)) return formatDebugValue(value);
        return Math.abs(value) > 1_000_000_000.0D ? formatScientific(value)
                : String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    public static String formatDebugValue(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000_000.0D) {
            return "\247c" + formatScientific(value) + "\247r";
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String formatScientific(double value) {
        return String.format(Locale.ROOT, "%.2e", value).replace("e+", "e").replaceAll("e(-?)0+(\\d+)", "e$1$2");
    }

    public double minValue() {
        return -this.maxValue();
    }

    public double maxValue() {
        return this.maxValue;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder pBuilder) {
        pBuilder.append("BlendedNoise{minLimitNoise=");
        this.minLimitNoise.parityConfigString(pBuilder);
        pBuilder.append(", maxLimitNoise=");
        this.maxLimitNoise.parityConfigString(pBuilder);
        pBuilder.append(", mainNoise=");
        this.mainNoise.parityConfigString(pBuilder);
        pBuilder.append(String.format(Locale.ROOT, ", xzScale=%.3f, yScale=%.3f, xzMainScale=%.3f, yMainScale=%.3f, cellWidth=4, cellHeight=8", 684.412D, 684.412D, 8.555150000000001D, 4.277575000000001D)).append('}');
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
