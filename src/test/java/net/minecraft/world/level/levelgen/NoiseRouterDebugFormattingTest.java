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

    private final DecimalFormat format = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Test
    void validValuesKeepFixedPrecisionIncludingRangeBoundaries() {
        assertEquals("0.123", format(0.1234D));
        assertEquals("-1.500", format(-1.5D));
        assertEquals("1.500", format(1.5D));
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
        assertEquals("\247c1.501e+00\247r", format(1.501D));
        assertEquals("\247c1.235e+115\247r", format(1.23456e115D));
        assertEquals("\247c-1.235e+115\247r", format(-1.23456e115D));
        assertEquals("\247cNaN\247r", format(Double.NaN));
        assertEquals("\247cInfinity\247r", format(Double.POSITIVE_INFINITY));
        assertEquals("\247c-Infinity\247r", format(Double.NEGATIVE_INFINITY));
    }

    private String format(double value) {
        return NoiseBasedChunkGenerator.formatDebugValue(format, value, -1.5D, 1.5D);
    }
}
