package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.Test;

/** Documents intentional distant-terrain arithmetic, not a request to patch it. */
class FarlandsSlicingTest {
    @Test
    void overflowingDomainShiftsCutDensityEvenWithoutContinentalness() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (GenerationContext ignored = GenerationContext.open(
                new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.PRECISE))) {
            RandomState state = RandomState.create(
                    BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                    BuiltinRegistries.NOISE, 0L);
            NoiseRouter router = state.router();
            NoiseRouter noContinents = router.mapAll(function -> function instanceof DensityFunctions.ShiftedNoise noise
                    && noise.noise().noiseData().unwrapKey().orElseThrow().equals(Noises.CONTINENTALNESS)
                    ? DensityFunctions.zero() : function);
            NoiseRouter noShifts = router.mapAll(function -> function instanceof DensityFunctions.ShiftedNoise noise
                    ? new DensityFunctions.ShiftedNoise(DensityFunctions.zero(), noise.shiftY(), DensityFunctions.zero(),
                            noise.xzScale(), noise.yScale(), noise.noise()) : function);
            Int1088 z = Int1088.parseDecimal("156797324626531188726");
            var cut = new DensityFunction.SinglePointContext(Int1088.fromLong(-24), 80, z);
            var survivor = new DensityFunction.SinglePointContext(Int1088.ZERO, 80, z);
            assertTrue(Double.isNaN(router.finalDensity().compute(cut)));
            assertTrue(Double.isNaN(noContinents.finalDensity().compute(cut)));
            assertTrue(Double.isFinite(noShifts.finalDensity().compute(cut)));
            assertTrue(Double.isNaN(router.continents().compute(survivor)));
            assertTrue(Double.isFinite(router.finalDensity().compute(survivor)));

            // Early onset: a finite sample becomes NaN within a million blocks
            // of the highest-frequency shift octave's nominal lattice limit.
            var before = new DensityFunction.SinglePointContext(Int1088.fromLong(-120), 80,
                    Int1088.parseDecimal("72473261583357402475"));
            var after = new DensityFunction.SinglePointContext(Int1088.fromLong(-120), 80,
                    Int1088.parseDecimal("72473261583358402475"));
            assertFalse(Double.isNaN(router.finalDensity().compute(before)));
            assertTrue(Double.isNaN(router.finalDensity().compute(after)));
        }
    }
}
