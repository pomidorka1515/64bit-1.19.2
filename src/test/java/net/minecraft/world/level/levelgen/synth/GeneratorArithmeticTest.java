package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;

class GeneratorArithmeticTest {
    @Test void preciseRoutingPreservesLegacyFailureInsteadOfWrappingItAway() {
        ImprovedNoise noise = new ImprovedNoise(new LegacyRandomSource(1234L));
        for (GeneratorMode mode : new GeneratorMode[] {GeneratorMode.BIT_32, GeneratorMode.BIT_64}) {
            for (long x : mode == GeneratorMode.BIT_32
                    ? new long[] {Integer.MAX_VALUE - 512L, Integer.MAX_VALUE + 512L, Integer.MIN_VALUE - 512L, 16_800_000_000L, -16_800_000_000L}
                    : new long[] {Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - 4096L}) {
                double scale = mode == GeneratorMode.BIT_64 ? 2.0D : 1.0D;
                double reference;
                try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.LOSSY))) {
                    reference = noise.noise((double) x * scale, 73.0D, 37.0D);
                }
                try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.PRECISE))) {
                    double exact = NoiseSampling.sample(noise, x >> 63, x, scale, 0.0D,
                            0L, 73L, 1.0D, 0.0D, 0L, 37L, 1.0D, 0.0D, 0, 0.0D, 0.0D);
                    assertEquals(reference, exact, Math.max(1.0E-6D, Math.abs(reference) * 1.0E-12D), mode + " x=" + x);
                }
            }
        }
    }

    @Test void patchedPreciseDoesNotInheritLegacySaturation() {
        ImprovedNoise noise = new ImprovedNoise(new LegacyRandomSource(1234L));
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            for (long hi : new long[] {0L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                double value = NoiseSampling.sample(noise, hi, 0L, 684.412D, 0.0D,
                        0L, 73L, 1.0D, 0.0D, hi, -1L, 684.412D, 0.0D, 0, 0.0D, 0.0D);
                assertTrue(Double.isFinite(value));
                assertTrue(Math.abs(value) < 2.0D);
            }
        }
    }
}
