package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class WorldGenRegionTest {
    @Test
    void reportedCrashCoordinatesSelectTheCenterNotItsWesternNeighbor() {
        assertCacheLookups(new ChunkPos(-576460752303423481L, 576460752303423476L), 8);
    }

    @Test
    void clampedWindowsKeepCorrectIndicesAtEveryEdgeAndCorner() {
        long[] coordinates = {WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK + 1L,
                WorldBounds.MIN_CHUNK + 7L, 0L, WorldBounds.MAX_CHUNK - 7L,
                WorldBounds.MAX_CHUNK - 1L, WorldBounds.MAX_CHUNK};
        for (long x : coordinates) {
            for (long z : coordinates) {
                assertCacheLookups(new ChunkPos(x, z), 8);
            }
        }
    }

    @Test
    void ordinaryAndSingleChunkWindowsKeepTheirExistingIndices() {
        for (int radius : new int[] {0, 1, 2, 8}) {
            assertCacheLookups(new ChunkPos(0L, 0L), radius);
            assertCacheLookups(new ChunkPos(1L << 54, -(1L << 54)), radius);
        }
    }

    private static void assertCacheLookups(ChunkPos center, int radius) {
        int size = radius * 2 + 1;
        // Match ChunkMap.getChunkRangeFuture: out-of-world slots are clamped,
        // not removed, so the same edge chunk can occupy several slots.
        List<ChunkPos> cache = new ArrayList<>();
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                cache.add(new ChunkPos(WorldBounds.addChunkOffset(center.x, dx),
                        WorldBounds.addChunkOffset(center.z, dz)));
            }
        }

        assertEquals(cache.size() / 2,
                WorldGenRegion.getChunkIndex(center, size, center.x, center.z),
                "the center must use its own slot with the required generation status");
        for (ChunkPos requested : cache) {
            int index = WorldGenRegion.getChunkIndex(center, size, requested.x, requested.z);
            assertTrue(index >= 0 && index < cache.size());
            assertEquals(requested, cache.get(index), "wrong chunk in window centered at " + center);
            // Edge duplicates must select the slot nearest the center: that
            // slot has the strongest scheduled generation-status dependency.
            int dx = (int) (requested.x - center.x);
            int dz = (int) (requested.z - center.z);
            assertEquals((dz + radius) * size + dx + radius, index);
        }
    }
}
