package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Int128;
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

    @Test void bothShiftsMatchPatchedSamplingAcrossFull128BitDomain() {
        // Lower octaves make a changed high limb distinguishable after permutation reduction.
        var parameters = new NormalNoise.NoiseParameters(-9, 1.0D, 1.0D, 1.0D, 0.0D);
        var holder = new DensityFunction.NoiseHolder(Holder.direct(parameters),
                NormalNoise.create(new LegacyRandomSource(0L), parameters));
        var shifts = new DensityFunction[] {new DensityFunctions.ShiftA(holder), new DensityFunctions.ShiftB(holder)};
        Int128[] coordinates = {Int128.ZERO, Int128.of(17L, 37L), Int128.of(-19L, 41L),
                Int128.parseDecimal("72473261583357418568"),
                Int128.parseDecimal("73786976294838222848"), Int128.MIN, Int128.MAX};
        for (var precision : CoordinatePrecision.values()) {
            for (var shift : shifts) {
                for (var coordinate : coordinates) {
                    // Test each world axis separately, including X -> noise Y in ShiftB.
                    for (boolean xAxis : new boolean[] {true, false}) {
                        var point = new DensityFunction.SinglePointContext(xAxis ? coordinate : Int128.ZERO, 80,
                                xAxis ? Int128.ZERO : coordinate);
                        double expected = sample(shift, point, GeneratorMode.PATCHED, CoordinatePrecision.PRECISE);
                        double actual = sample(shift, point, GeneratorMode.BIT_64_STABLE_SHIFTS, precision);
                        assertTrue(Double.isFinite(actual));
                        assertTrue(Math.abs(actual) <= shift.maxValue());
                        assertEquals(expected, actual);
                    }
                }
                var point = new DensityFunction.SinglePointContext(Int128.of(17L, 37L), 80, Int128.of(-19L, 41L));
                double value = sample(shift, point, GeneratorMode.BIT_64_STABLE_SHIFTS, precision);
                assertNotEquals(value, sample(shift,
                        new DensityFunction.SinglePointContext(Int128.of(18L, 37L), 80, Int128.of(-19L, 41L)),
                        GeneratorMode.BIT_64_STABLE_SHIFTS, precision));
                assertNotEquals(value, sample(shift,
                        new DensityFunction.SinglePointContext(Int128.of(17L, 37L), 80, Int128.of(-18L, 41L)),
                        GeneratorMode.BIT_64_STABLE_SHIFTS, precision));
            }
        }
    }

    @Test void unrelatedNoiseRetains64BitOverflowAndShiftCollapseIsRemoved() {
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                    BuiltinRegistries.NOISE, 0L);
            var point = new DensityFunction.SinglePointContext(Int128.fromLong(-24), 80,
                    Int128.parseDecimal("156797324626531188726"));
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
}
