package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;

class SimplexNoiseExactTest {
    @Test
    void integerSamplingPreservesNearOriginTerrain() {
        FarlandsMode.Mode previous = FarlandsMode.getMode();
        try {
            FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
            SimplexNoise noise = new SimplexNoise(new LegacyRandomSource(1234L));
            for (long x = -1000; x <= 1000; x += 17) {
                assertEquals(noise.getValue((double) x, (double) -x + 31), noise.getValueAt(x, -x + 31));
            }
            for (long x = 2000000; x < 2000100; ++x) {
                assertEquals(noise.getValue((double) x, (double) x + 31), noise.getValueAt(x, x + 31), 1.0E-8D);
                assertEquals(noise.getValue((double) -x, (double) x + 31), noise.getValueAt(-x, x + 31), 1.0E-8D);
            }
        } finally {
            FarlandsMode.setMode(previous);
        }
    }

    @Test
    void integerSamplingRetainsIslandsAndAdjacentVariationAtLongLimits() {
        SimplexNoise noise = new SimplexNoise(new LegacyRandomSource(1234L));
        for (long origin : new long[] {1L << 53, 1L << 62, -(1L << 62), Long.MAX_VALUE - 512, Long.MIN_VALUE}) {
            int islandSeeds = 0;
            double first = noise.getValueAt(origin, origin);
            boolean varies = false;
            for (int offset = 0; offset < 512; ++offset) {
                double value = noise.getValueAt(origin + offset, origin);
                assertTrue(Double.isFinite(value));
                varies |= value != first;
                if (value < -0.9F) {
                    ++islandSeeds;
                }
            }
            assertTrue(varies, "adjacent samples collapsed at " + origin);
            assertTrue(islandSeeds > 0, "no island seeds at " + origin);
        }
    }
}
