package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void improvedNoiseFieldsUseSharedBoundsAndRetainOrdinaryFormatting() {
        var noise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(1234L));
        ImprovedNoise.beginDebugCapture();
        try {
            noise.noise(0.0D, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString().matches("Sample Point: -?\\d+\\.\\d{3} Grid Index: -?\\d+"));
            noise.noise(1.0e12D, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString().contains("Sample Point: \247c"));
            assertTrue(ImprovedNoise.getDebugString().contains("Grid Index: \247c"));
            noise.noise(Double.POSITIVE_INFINITY, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString2().contains("Cell Offset: \247c"));
            assertTrue(ImprovedNoise.getDebugString2().contains("Smoothed Offset: \247c"));
        } finally {
            ImprovedNoise.endDebugCapture();
        }
    }

    @Test
    void nonFiniteSimplexOutputUsesSharedRedFormatting() throws ReflectiveOperationException {
        var field = SimplexNoise.class.getDeclaredField("debugSimplexOutput");
        field.setAccessible(true);
        double previous = field.getDouble(null);
        try {
            field.setDouble(null, Double.NaN);
            assertTrue(SimplexNoise.getDebugString2().contains("Simplex Output: \247cNaN\247r"));
            field.setDouble(null, 1.0e12D);
            assertTrue(SimplexNoise.getDebugString2().contains("Simplex Output: \247c1.000e+12\247r"));
        } finally {
            field.setDouble(null, previous);
        }
    }
}
