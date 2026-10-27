package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

class BiomeManagerInt1088Test {
    @Test
    void runtimeQueriesDispatchFullWidthQuartCoordinates() {
        Int1088 x = Int1088.of(64L, 123L);
        Int1088 z = Int1088.of(128L, -98L);
        Int1088[] queried = new Int1088[2];
        BiomeManager manager = new BiomeManager(new BiomeManager.NoiseBiomeSource() {
            public Holder<Biome> getNoiseBiome(long x, int y, long z) {
                throw new AssertionError("runtime lookup narrowed before provider dispatch");
            }

            public Holder<Biome> getNoiseBiome(Int1088 x, int y, Int1088 z) {
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
                || queried[0].equals(x.shiftRight(2).subtract(Int1088.ONE)));
        assertTrue(queried[1].equals(z.shiftRight(2))
                || queried[1].equals(z.shiftRight(2).subtract(Int1088.ONE)));
    }

    @Test
    void defaultSamplingProviderReceivesFullQuartCoordinates() {
        BiomeManager.NoiseBiomeSource source = (x, y, z) -> {
            assertEquals(Int1088.of(17L, 42L), x);
            assertEquals(Int1088.of(-19L, -42L), z);
            throw new Sampled();
        };
        assertThrows(Sampled.class, () -> source.getNoiseBiome(
                Int1088.of(17L, 42L), 5, Int1088.of(-19L, -42L)));
    }

    private static class Sampled extends RuntimeException {
    }
}
