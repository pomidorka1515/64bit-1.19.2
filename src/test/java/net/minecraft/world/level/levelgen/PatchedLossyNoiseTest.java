package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.junit.jupiter.api.Test;

class PatchedLossyNoiseTest {
    @Test
    void directDoubleSamplingCannotExtrapolateInPatchedMode() {
        var noise = new ImprovedNoise(RandomSource.create(0L));
        try (GenerationContext ignored = context(GeneratorMode.PATCHED)) {
            for (double x : new double[] {0x1p63, -0x1p64, 0x1p177, -0x1p178, Double.MAX_VALUE}) {
                for (int axis = 0; axis < 3; axis++) {
                    double a = axis == 0 ? x : 0.25D;
                    double b = axis == 1 ? x : 0.5D;
                    double c = axis == 2 ? x : 0.75D;
                    double value = noise.noise(a, b, c);
                    assertBounded(value, 2.0D);
                    double[] derivative = new double[3];
                    assertBounded(noise.noiseWithDerivative(a, b, c, derivative), 2.0D);
                    for (double component : derivative) assertTrue(Double.isFinite(component));
                }
            }
        }
        double vanilla;
        try (GenerationContext ignored = context(GeneratorMode.BIT_64)) {
            vanilla = noise.noise(1234.25D, -12.5D, -9876.75D);
            assertTrue(!Double.isFinite(noise.noise(0x1p178, 0.5D, 0.75D)));
        }
        try (GenerationContext ignored = context(GeneratorMode.PATCHED)) {
            assertEquals(vanilla, noise.noise(1234.25D, -12.5D, -9876.75D));
        }
    }

    @Test
    void spaghettiAndSurfaceNoisesStayBoundedAndDensityStaysFinite() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (GenerationContext ignored = context(GeneratorMode.PATCHED)) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0L);
            for (int power : new int[] {63, 177, 178, 1023}) {
                for (int sign : new int[] {-1, 1}) {
                    var coordinate = Int1088.ONE.shiftLeftSaturated(power);
                    if (sign < 0) coordinate = Int1088.ZERO.subtractSaturated(coordinate);
                    for (int axis = 0; axis < 2; axis++) {
                        var x = axis == 0 ? coordinate : Int1088.ZERO;
                        var z = axis == 1 ? coordinate : Int1088.ZERO;
                        for (int y = -64; y < 320; y += 8) {
                            var point = new DensityFunction.SinglePointContext(x, y, z);
                            assertTrue(Double.isFinite(state.router().finalDensity().compute(point)), "density at " + point);
                            state.router().mapAll(function -> {
                                if (function instanceof DensityFunctions.WeirdScaledSampler sampler)
                                    assertBounded(sampler.compute(point), sampler.maxValue());
                                return function;
                            });
                        }
                        for (var key : java.util.List.of(
                                Noises.BADLANDS_PILLAR, Noises.BADLANDS_PILLAR_ROOF, Noises.BADLANDS_SURFACE,
                                Noises.ICEBERG_PILLAR, Noises.ICEBERG_PILLAR_ROOF, Noises.ICEBERG_SURFACE)) {
                            var noise = state.getOrCreateNoise(key);
                            for (double scale : new double[] {1.0D, 0.2D, 0.25D, 1.28D})
                                assertBounded(SurfaceSystem.sampleNoise(noise, x, scale, z), noise.maxValue());
                        }
                    }
                }
            }
        }
    }

    private static GenerationContext context(GeneratorMode mode) {
        return GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.LOSSY));
    }

    private static void assertBounded(double value, double bound) {
        assertTrue(Double.isFinite(value) && Math.abs(value) <= bound, "unbounded noise: " + value);
    }
}
