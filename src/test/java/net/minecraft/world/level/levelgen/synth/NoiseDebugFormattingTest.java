package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoiseDebugFormattingTest {
    @Test
    void ordinaryValuesRoundToThreeDecimalPlaces() {
        assertEquals("0.123", BlendedNoise.formatDebugValue(0.1234D));
        assertEquals("-0.124", BlendedNoise.formatDebugValue(-0.1236D));
        assertEquals("1000000000.000", BlendedNoise.formatDebugValue(1_000_000_000.0D));
    }

    @Test
    void overflowingValuesAreRedAndUseScientificNotation() {
        assertEquals("\247c1.235e+12\247r", BlendedNoise.formatDebugValue(1.23456e12D));
        assertEquals("\247c-1.235e+12\247r", BlendedNoise.formatDebugValue(-1.23456e12D));
        assertEquals("\247cInfinity\247r", BlendedNoise.formatDebugValue(Double.POSITIVE_INFINITY));
        assertEquals("\247cNaN\247r", BlendedNoise.formatDebugValue(Double.NaN));
    }
}
