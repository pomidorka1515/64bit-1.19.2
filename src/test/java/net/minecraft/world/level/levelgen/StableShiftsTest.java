package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StableShiftsTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static double sample(DensityFunction function, DensityFunction.FunctionContext point,
            GeneratorMode mode, CoordinatePrecision precision) {
        try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
            double result = function.compute(point);
            assertEquals(mode, GenerationContext.current().mode());
            return result;
        }
    }

    @Test void bothShiftsMatchPatchedSamplingAcrossFull1088BitDomain() {
        // Lower octaves make a changed high limb distinguishable after permutation reduction.
        var parameters = new NormalNoise.NoiseParameters(-9, 1.0D, 1.0D, 1.0D, 0.0D);
        var holder = new DensityFunction.NoiseHolder(Holder.direct(parameters),
                NormalNoise.create(new LegacyRandomSource(0L), parameters));
        var shifts = new DensityFunction[] {new DensityFunctions.ShiftA(holder), new DensityFunctions.ShiftB(holder)};
        Int1088[] coordinates = {Int1088.ZERO, Int1088.of(37L, 17L), Int1088.of(41L, -19L),
                Int1088.parseDecimal("72473261583357418568"),
                Int1088.parseDecimal("73786976294838222848"), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
        for (var precision : CoordinatePrecision.values()) {
            for (var shift : shifts) {
                for (var coordinate : coordinates) {
                    // Test each world axis separately, including X -> noise Y in ShiftB.
                    for (boolean xAxis : new boolean[] {true, false}) {
                        var point = new DensityFunction.SinglePointContext(xAxis ? coordinate : Int1088.ZERO, 80,
                                xAxis ? Int1088.ZERO : coordinate);
                        double expected = sample(shift, point, GeneratorMode.PATCHED, CoordinatePrecision.PRECISE);
                        for (var mode : new GeneratorMode[] {GeneratorMode.BIT_32_STABLE_SHIFTS, GeneratorMode.BIT_64_STABLE_SHIFTS}) {
                            double actual = sample(shift, point, mode, precision);
                            assertTrue(Double.isFinite(actual));
                            assertTrue(Math.abs(actual) <= shift.maxValue());
                            assertEquals(expected, actual);
                        }
                    }
                }
                var point = new DensityFunction.SinglePointContext(Int1088.of(37L, 17L), 80, Int1088.of(41L, -19L));
                double value = sample(shift, point, GeneratorMode.BIT_64_STABLE_SHIFTS, precision);
                assertNotEquals(value, sample(shift,
                        new DensityFunction.SinglePointContext(Int1088.of(37L, 18L), 80, Int1088.of(41L, -19L)),
                        GeneratorMode.BIT_64_STABLE_SHIFTS, precision));
                assertNotEquals(value, sample(shift,
                        new DensityFunction.SinglePointContext(Int1088.of(37L, 17L), 80, Int1088.of(41L, -18L)),
                        GeneratorMode.BIT_64_STABLE_SHIFTS, precision));
            }
        }
    }

    @Test void unrelatedNoiseRetains64BitOverflowAndShiftCollapseIsRemoved() {
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                    BuiltinRegistries.NOISE, 0L);
            var point = new DensityFunction.SinglePointContext(Int1088.fromLong(-24), 80,
                    Int1088.parseDecimal("156797324626531188726"));
            assertTrue(Double.isFinite(state.router().finalDensity().compute(point)));
            assertTrue(Double.isNaN(sample(state.router().finalDensity(), point, GeneratorMode.BIT_64, CoordinatePrecision.PRECISE)));
            for (var precision : CoordinatePrecision.values()) {
                for (var key : java.util.List.of(Noises.CAVE_CHEESE, Noises.PILLAR, Noises.SHIFT)) {
                    var function = new DensityFunctions.Noise(new DensityFunction.NoiseHolder(
                            BuiltinRegistries.NOISE.getHolderOrThrow(key), state.getOrCreateNoise(key)), 25.0D, 1.0D);
                    assertEquals(sample(function, point, GeneratorMode.BIT_64, precision),
                            sample(function, point, GeneratorMode.BIT_64_STABLE_SHIFTS, precision));
                }
            }
        }
    }

    @Test void unrelatedNoiseRetains32BitArithmetic() {
        var parameters = new NormalNoise.NoiseParameters(-3, 1.0D, 1.0D);
        var holder = new DensityFunction.NoiseHolder(Holder.direct(parameters),
                NormalNoise.create(new LegacyRandomSource(0L), parameters));
        var function = new DensityFunctions.Noise(holder, 25.0D, 1.0D);
        for (var precision : CoordinatePrecision.values()) {
            for (long coordinate : new long[] {0L, 30_000_000L, Integer.MAX_VALUE, -16_800_000_000L, Long.MAX_VALUE}) {
                var point = new DensityFunction.SinglePointContext(Int1088.fromLong(coordinate), 80, Int1088.ZERO);
                assertEquals(sample(function, point, GeneratorMode.BIT_32, precision),
                        sample(function, point, GeneratorMode.BIT_32_STABLE_SHIFTS, precision));
            }
            try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_32_STABLE_SHIFTS, precision))) {
                assertTrue(GenerationPolicy.isEnabled());
                assertTrue(GenerationPolicy.usesLegacyBlendedNoise());
                assertFalse(GenerationPolicy.usesNoFarlandsSafeguards());
            }
        }
    }
}
