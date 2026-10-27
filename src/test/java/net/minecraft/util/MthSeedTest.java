package net.minecraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
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
    void everyHighLimbInfluencesSeedsOnBothAxes() {
        Int1088 x = Int1088.fromLong(17L), z = Int1088.fromLong(29L);
        long baseline = Mth.getSeed(x, 64, z);
        for (int bit = 64; bit < Int1088.BITS; ++bit) {
            long[] limbs = new long[Int1088.LIMBS];
            limbs[bit >>> 6] = 1L << (bit & 63);
            limbs[0] = 17L;
            Int1088 wideX = Int1088.fromFixedLimbs(limbs);
            limbs[0] = 29L;
            Int1088 wideZ = Int1088.fromFixedLimbs(limbs);
            assertNotEquals(17L, Mth.mixCoordinate(wideX), "bit " + bit);
            assertNotEquals(baseline, Mth.getSeed(wideX, 64, z), "X bit " + bit);
            assertNotEquals(baseline, Mth.getSeed(x, 64, wideZ), "Z bit " + bit);
            assertEquals(Mth.getSeed(wideX, 64, wideZ), Mth.getSeed(new BlockPos(wideX, 64, wideZ)));
        }
    }

    private static void assertLegacySeed(long x, int y, long z) {
        long word = x * 3129871L ^ z * 116129781L ^ (long) y;
        long expected = (word * word * 42317861L + word * 11L) >> 16;
        assertEquals(x, Mth.mixCoordinate(Int1088.fromLong(x)));
        assertEquals(expected, Mth.getSeed(x, y, z));
        assertEquals(expected, Mth.getSeed(Int1088.fromLong(x), y, Int1088.fromLong(z)));
    }
}
