package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class NoiseRouterDebugFormattingTest {
    private final DecimalFormat format = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Test
    void validValuesKeepFixedPrecisionIncludingRangeBoundaries() {
        assertEquals("0.123", format(0.1234D));
        assertEquals("-1.500", format(-1.5D));
        assertEquals("1.500", format(1.5D));
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
