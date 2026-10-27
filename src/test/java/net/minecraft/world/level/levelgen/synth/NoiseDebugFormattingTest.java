package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseDebugFormattingTest {
    @Test
    void splitDisplaysRetainLatticeDigitsAndFractionsBeyondDoublePrecision() {
        assertEquals("\247c9223372036854775807.25\247r", BlendedNoise.formatSplitDebugValue(Long.MAX_VALUE, .25D, 2));
        assertEquals("\247c-9223372036854775807.75\247r", BlendedNoise.formatSplitDebugValue(Long.MIN_VALUE, .25D, 2));
        assertEquals("\247c9223372036854775808\247r", BlendedNoise.formatSplitDebugValue(Long.MAX_VALUE, .75D, 0));
        assertEquals("1.25", BlendedNoise.formatSplitDebugValue(1L, .25D, 2));
        assertEquals("1000000000.00", BlendedNoise.formatSplitDebugValue(1_000_000_000L, 0.0D, 2));
        assertEquals("\247c1000000000.00\247r", BlendedNoise.formatSplitDebugValue(1_000_000_000L, Double.MIN_VALUE, 2));
        assertEquals("-1000000000.00", BlendedNoise.formatSplitDebugValue(-1_000_000_000L, 0.0D, 2));
        assertEquals("\247c-1000000000.00\247r", BlendedNoise.formatSplitDebugValue(-1_000_000_001L, Math.nextDown(1.0D), 2));
    }

    @Test
    void improvedNoiseCaptureRetainsExactGridAndSplitSample() {
        var noise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(1234L));
        var x = new PreciseNoiseCoordinate.Split();
        var yz = new PreciseNoiseCoordinate.Split();
        x.lattice = Long.MAX_VALUE;
        x.fraction = .25D;
        ImprovedNoise.beginDebugCapture();
        try {
            noise.sampleRouted(x, yz, yz, 0.0D, 0.0D);
            assertEquals("Grid Sample point: \247c9223372036854775807.25\247r index: \247c9223372036854775807\247r",
                    ImprovedNoise.getDebugString());
            noise.noise(0.0D, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString().matches("Grid Sample point: -?\\d+\\.\\d{2} index: -?\\d+"));
        } finally {
            ImprovedNoise.endDebugCapture();
        }
    }

    @Test
    void ordinaryValuesRoundToTwoDecimalPlaces() {
        assertEquals("0.12", BlendedNoise.formatDebugValue(0.1234D));
        assertEquals("-0.12", BlendedNoise.formatDebugValue(-0.1236D));
        assertEquals("1000000000.00", BlendedNoise.formatDebugValue(1_000_000_000.0D));
    }

    @Test
    void overflowingValuesAreRedAndUseScientificNotation() {
        assertEquals("\247c1.23e12\247r", BlendedNoise.formatDebugValue(1.23456e12D));
        assertEquals("\247c-1.23e12\247r", BlendedNoise.formatDebugValue(-1.23456e12D));
        assertEquals("1.50e0", BlendedNoise.formatScientific(1.501D));
        assertEquals("1.23e-4", BlendedNoise.formatScientific(0.000123D));
        assertEquals("\247cInfinity\247r", BlendedNoise.formatDebugValue(Double.POSITIVE_INFINITY));
        assertEquals("\247cNaN\247r", BlendedNoise.formatDebugValue(Double.NaN));
    }

    @Test
    void improvedNoiseFieldsUseSharedBoundsAndRetainOrdinaryFormatting() {
        var noise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(1234L));
        ImprovedNoise.beginDebugCapture();
        try {
            noise.noise(0.0D, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString().matches("Grid Sample point: -?\\d+\\.\\d{2} index: -?\\d+"));
            noise.noise(1.0e12D, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString().contains("Sample point: \247c"));
            assertTrue(ImprovedNoise.getDebugString().contains("index: \247c"));
            noise.noise(Double.POSITIVE_INFINITY, 0.0D, 0.0D);
            assertTrue(ImprovedNoise.getDebugString2().contains("Cell Offset raw: \247c"));
            assertTrue(ImprovedNoise.getDebugString2().contains("smoothed: \247c"));
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
            assertTrue(SimplexNoise.getDebugString2().contains("Simplex Output: \247c1.00e12\247r"));
        } finally {
            field.setDouble(null, previous);
        }
    }
}
