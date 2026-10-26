package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NoiseRouterDebugFormattingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Test
    void peaksAndValleysUsesDoublePrecisionForExplodedFiniteRidges() {
        for (double weirdness : new double[] {1.4e20D, 2.0e38D, 1.0e100D, -1.0e100D}) {
            double pv = NoiseBasedChunkGenerator.debugPeaksAndValleys(weirdness);
            assertEquals(-3.0D * Math.abs(weirdness), pv);
        }
        assertEquals("\247c-3.00e100\247r", format(NoiseBasedChunkGenerator.debugPeaksAndValleys(1.0e100D)));
    }

    @Test
    void peaksAndValleysRetainsOrdinaryShapeAndGenuineNonfiniteResults() {
        assertEquals(-1.0D, NoiseBasedChunkGenerator.debugPeaksAndValleys(0.0D));
        assertEquals(1.0D, NoiseBasedChunkGenerator.debugPeaksAndValleys(2.0D / 3.0D));
        assertEquals(0.0D, NoiseBasedChunkGenerator.debugPeaksAndValleys(1.0D), 1.0e-15D);
        assertEquals(Double.NEGATIVE_INFINITY, NoiseBasedChunkGenerator.debugPeaksAndValleys(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, NoiseBasedChunkGenerator.debugPeaksAndValleys(Double.NEGATIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, NoiseBasedChunkGenerator.debugPeaksAndValleys(Double.MAX_VALUE));
        assertEquals(Double.NaN, NoiseBasedChunkGenerator.debugPeaksAndValleys(Double.NaN));
    }

    @Test
    void validValuesKeepFixedPrecisionIncludingRangeBoundaries() {
        assertEquals("0.12", format(0.1234D));
        assertEquals("-1.50", format(-1.5D));
        assertEquals("1.50", format(1.5D));
    }

    @Test
    void initialDensityAllowsItsFullClampedOverworldRange() {
        for (double value : new double[] {-64.0D, -10.0D, -6.443D, 10.0D, 64.0D}) {
            assertEquals(format.format(value), NoiseBasedChunkGenerator.formatDebugValue(format, value, -64.0D, 64.0D));
        }
        assertEquals("\247cNaN\247r", NoiseBasedChunkGenerator.formatDebugValue(format, Double.NaN, -64.0D, 64.0D));
    }

    @Test
    void invalidValuesAreRedAndRoundedInScientificNotation() {
        assertEquals("\247c1.50e0\247r", format(1.501D));
        assertEquals("\247c1.23e115\247r", format(1.23456e115D));
        assertEquals("\247c-1.23e115\247r", format(-1.23456e115D));
        assertEquals("\247cNaN\247r", format(Double.NaN));
        assertEquals("\247cInfinity\247r", format(Double.POSITIVE_INFINITY));
        assertEquals("\247c-Infinity\247r", format(Double.NEGATIVE_INFINITY));
    }

    private String format(double value) {
        return NoiseBasedChunkGenerator.formatDebugValue(format, value, -1.5D, 1.5D);
    }
}
