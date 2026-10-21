package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

class BiomeManagerInt128Test {
    @Test
    void runtimeQueriesDispatchFullWidthQuartCoordinates() {
        Int128 x = Int128.of(123L, 64L);
        Int128 z = Int128.of(-98L, 128L);
        Int128[] queried = new Int128[2];
        BiomeManager manager = new BiomeManager(new BiomeManager.NoiseBiomeSource() {
            public Holder<Biome> getNoiseBiome(long x, int y, long z) {
                throw new AssertionError("runtime lookup narrowed before provider dispatch");
            }

            public Holder<Biome> getNoiseBiome(Int128 x, int y, Int128 z) {
                queried[0] = x;
                queried[1] = z;
                return null;
            }
        }, 1234L);

        manager.getNoiseBiomeAtPosition(new BlockPos(x, 70, z));
        assertEquals(x.shiftRight(2), queried[0]);
        assertEquals(z.shiftRight(2), queried[1]);
        manager.getBiome(new BlockPos(x, 70, z));
        assertTrue(queried[0].equals(x.shiftRight(2))
                || queried[0].equals(x.shiftRight(2).subtract(Int128.ONE)));
        assertTrue(queried[1].equals(z.shiftRight(2))
                || queried[1].equals(z.shiftRight(2).subtract(Int128.ONE)));
    }

    @Test
    void legacySamplingProviderReceivesOnlyLowQuartBits() {
        BiomeManager.NoiseBiomeSource source = (x, y, z) -> {
            assertEquals(17L, x);
            assertEquals(-19L, z);
            throw new Sampled();
        };
        assertThrows(Sampled.class, () -> source.getNoiseBiome(
                Int128.of(42L, 17L), 5, Int128.of(-42L, -19L)));
    }

    private static class Sampled extends RuntimeException {
    }
}
