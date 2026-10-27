package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

class NoiseExtrapolationCaptureTest {
    @Test
    void observesFractionsWithoutRepairingBrokenNoise() {
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            NormalNoise noise = NormalNoise.create(new XoroshiroRandomSource(0), -7, 1.0);
            assertFalse(NoiseSampling.hasExtrapolation(() -> noise.getValueScaled(0L, 0.67, 64, 0.67, 0L)));
            var x = net.minecraft.core.Int1088.parseDecimal("-225214492026149979000000");
            double before = noise.getValueScaled(x, 0.67, 64, 0.67, net.minecraft.core.Int1088.ZERO);
            assertTrue(NoiseSampling.hasExtrapolation(() -> noise.getValueScaled(x, 0.67, 64, 0.67, net.minecraft.core.Int1088.ZERO)));
            assertTrue(Math.abs(before) > 1.0E10 || !Double.isFinite(before));
            assertEquals(Double.doubleToLongBits(before), Double.doubleToLongBits(noise.getValueScaled(x, 0.67, 64, 0.67, net.minecraft.core.Int1088.ZERO)));
        }
    }

    @Test
    void captureIsNestedAndExceptionSafeAndIncludesLossySampling() {
        var noise = new ImprovedNoise(new XoroshiroRandomSource(0));
        assertTrue(NoiseSampling.hasExtrapolation(() -> {
            assertTrue(NoiseSampling.hasExtrapolation(() -> noise.noise(1.0E30, 0, 0)));
            return 0;
        }));
        assertThrows(IllegalStateException.class, () -> NoiseSampling.hasExtrapolation(() -> {
            noise.noise(1.0E30, 0, 0);
            throw new IllegalStateException();
        }));
        assertFalse(NoiseSampling.hasExtrapolation(() -> noise.noise(0, 0, 0)));
    }
}
