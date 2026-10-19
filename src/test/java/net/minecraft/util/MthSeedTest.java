package net.minecraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MthSeedTest {
    @Test
    void signExtendedCoordinatesPreserveLegacySeeds() {
        long[] edges = {Long.MIN_VALUE, Long.MIN_VALUE + 1L, -1L, 0L, 1L, Long.MAX_VALUE};
        for (long x : edges) {
            for (long z : edges) {
                for (int y : new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
                    assertLegacySeed(x, y, z);
                }
            }
        }
        Random random = new Random(207L);
        for (int i = 0; i < 10000; ++i) {
            assertLegacySeed(random.nextLong(), random.nextInt(), random.nextLong());
        }
    }

    @Test
    void highHalvesInfluenceSeedsOnBothAxes() {
        long baseline = Mth.getSeed(0L, 17L, 64, 0L, 29L);
        for (int bit = 0; bit < 64; ++bit) {
            long hi = 1L << bit;
            assertNotEquals(17L, Mth.mixCoordinate(hi, 17L));
            assertNotEquals(baseline, Mth.getSeed(hi, 17L, 64, 0L, 29L));
            assertNotEquals(baseline, Mth.getSeed(0L, 17L, 64, hi, 29L));
        }
    }

    private static void assertLegacySeed(long x, int y, long z) {
        long word = x * 3129871L ^ z * 116129781L ^ (long) y;
        long expected = (word * word * 42317861L + word * 11L) >> 16;
        assertEquals(x, Mth.mixCoordinate(x < 0L ? -1L : 0L, x));
        assertEquals(expected, Mth.getSeed(x, y, z));
        assertEquals(expected, Mth.getSeed(x < 0L ? -1L : 0L, x, y, z < 0L ? -1L : 0L, z));
    }
}
