package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.Test;

class StableShiftContinentalnessTest {
    @Test void screenshotExplosionIsClimateOverflowNotShiftOverflow() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var config = new GeneratorConfig(GeneratorMode.BIT_32_STABLE_SHIFTS, CoordinatePrecision.LOSSY);
        try (var ignored = GenerationContext.open(config)) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                    NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0L);
            var holder = new DensityFunction.NoiseHolder(BuiltinRegistries.NOISE.getHolderOrThrow(Noises.SHIFT),
                    state.getOrCreateNoise(Noises.SHIFT));
            var screenshot = new DensityFunction.SinglePointContext(16873994903L, 257, -124L);
            for (var shift : new DensityFunction[] {new DensityFunctions.ShiftA(holder), new DensityFunctions.ShiftB(holder)}) {
                var result = NoiseSampling.sampleWithExtrapolation(() -> shift.compute(screenshot));
                assertFalse(result.extrapolated());
                assertTrue(Math.abs(result.value()) <= shift.maxValue());
                assertEquals(config, GenerationContext.current());
            }
            assertEquals(-1.09574e8, state.router().continents().compute(screenshot), 1000);
            assertTrue(Double.isFinite(state.router().finalDensity().compute(screenshot)));
            // Exhaustive window scan in StableShiftBoundaryProbe locates this local edge.
            for (long x : new long[] {16873994511L, 16873994512L}) {
                var point = new DensityFunction.SinglePointContext(x, 64, -124L);
                assertEquals(x == 16873994512L,
                        NoiseSampling.hasExtrapolation(() -> state.router().continents().compute(point)));
            }
        }
    }
}
