package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LossyDoubleOverflowTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lossyConversionMatchesIeeeOverflowBoundaryWithoutChangingGeneralConversion() {
        BigInteger limit = BigInteger.ONE.shiftLeft(1024);
        BigInteger maxFinite = limit.subtract(BigInteger.ONE.shiftLeft(971));
        BigInteger roundingBoundary = limit.subtract(BigInteger.ONE.shiftLeft(970));
        for (var precision : CoordinatePrecision.values()) {
            try (var ignored = context(precision)) {
                for (var magnitude : new BigInteger[] {maxFinite, roundingBoundary.subtract(BigInteger.ONE),
                        roundingBoundary, limit, BigInteger.ONE.shiftLeft(1086)}) {
                    for (int sign : new int[] {-1, 1}) {
                        var integer = magnitude.multiply(BigInteger.valueOf(sign));
                        var coordinate = Int1088.parseDecimal(integer.toString());
                        double expected = integer.doubleValue();
                        if (precision == CoordinatePrecision.PRECISE && !Double.isFinite(expected))
                            expected = sign * Double.MAX_VALUE;
                        assertEquals(expected, WorldgenCoordinate.toDouble(coordinate));
                        long[] limbs = new long[Int1088.LIMBS + 2];
                        coordinate.copyFixed(limbs, 1);
                        assertEquals(expected, WorldgenCoordinate.toDouble(limbs, 1, Int1088.LIMBS));
                        assertTrue(Double.isFinite(coordinate.toDoubleRounded()));
                    }
                }
            }
        }
    }

    @Test
    void finiteAbsoluteCoordinatesKeepClampsEvenWhenScalingWouldOverflow() {
        var noise = new ImprovedNoise(RandomSource.create(0L));
        try (var ignored = context(CoordinatePrecision.LOSSY)) {
            for (int power : new int[] {64, 177, 1023}) {
                for (int sign : new int[] {-1, 1}) {
                    var coordinate = Int1088.ONE.shiftLeftSaturated(power);
                    if (sign < 0) coordinate = coordinate.negate();
                    for (double scale : new double[] {0.25D, 684.412D, Double.MAX_VALUE}) {
                        double scaled = ContextCoordinates.scaled(coordinate, scale, 0.25D);
                        assertTrue(Double.isFinite(scaled));
                        assertTrue(Double.isFinite(noise.noise(scaled, 80.0D, 0.0D)));
                    }
                }
            }
        }
    }

    @Test
    void overflowReachesTerrainAndSurfaceButPreciseSamplingStillWorks() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var noise = new ImprovedNoise(RandomSource.create(0L));
        for (var precision : CoordinatePrecision.values()) {
            try (var ignored = context(precision)) {
                var state = RandomState.create(settings, BuiltinRegistries.NOISE, 0L);
                var blended = new BlendedNoise(RandomSource.create(0L), 1.0D, 1.0D, 80.0D, 160.0D, 8.0D);
                var surface = state.getOrCreateNoise(Noises.BADLANDS_SURFACE);
                for (int sign : new int[] {-1, 1}) {
                    var coordinate = Int1088.ONE.shiftLeftSaturated(1024);
                    if (sign < 0) coordinate = coordinate.negate();
                    for (boolean xAxis : new boolean[] {true, false}) {
                        var x = xAxis ? coordinate : Int1088.ZERO;
                        var z = xAxis ? Int1088.ZERO : coordinate;
                        var point = new DensityFunction.SinglePointContext(x, 80, z);
                        if (precision == CoordinatePrecision.LOSSY) {
                            assertFalse(Double.isFinite(ContextCoordinates.scaled(point, xAxis, 0.25D, 0.0D)));
                            assertFalse(Double.isFinite(blended.compute(point)));
                            assertFalse(Double.isFinite(state.router().finalDensity().compute(point)));
                            assertFalse(Double.isFinite(SurfaceSystem.sampleNoise(surface, x, 0.25D, z)));
                            double dx = WorldgenCoordinate.toDouble(x);
                            double dz = WorldgenCoordinate.toDouble(z);
                            assertFalse(Double.isFinite(noise.noise(dx, 80.0D, dz)));
                            assertFalse(Double.isFinite(noise.noiseWithDerivative(dx, 80.0D, dz, new double[3])));
                        } else {
                            assertTrue(Double.isFinite(blended.compute(point)));
                            assertTrue(Double.isFinite(SurfaceSystem.sampleNoise(surface, x, 0.25D, z)));
                            assertTrue(Double.isFinite(state.router().finalDensity().compute(point)));
                        }
                    }
                }
            }
        }
    }

    private static GenerationContext context(CoordinatePrecision precision) {
        return GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, precision));
    }
}
