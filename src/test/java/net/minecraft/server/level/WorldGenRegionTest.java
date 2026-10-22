package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Int128;
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
        Int128[] coordinates = {WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, 1L),
                WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, 7L), Int128.ZERO,
                WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, -7L),
                WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, -1L), WorldBounds.MAX_CHUNK};
        for (Int128 x : coordinates) {
            for (Int128 z : coordinates) {
                assertCacheLookups(new ChunkPos(x, z), 8);
            }
        }
    }

    @Test
    void ordinaryAndSingleChunkWindowsKeepTheirExistingIndices() {
        for (int radius : new int[] {0, 1, 2, 8}) {
            assertCacheLookups(new ChunkPos(0L, 0L), radius);
            assertCacheLookups(new ChunkPos(1L << 54, -(1L << 54)), radius);
            assertCacheLookups(new ChunkPos(Int128.of(123L, -1L), Int128.of(-123L, 0L)), radius);
        }
    }

    private static void assertCacheLookups(ChunkPos center, int radius) {
        int size = radius * 2 + 1;
        // Match ChunkMap.getChunkRangeFuture: out-of-world slots are clamped,
        // not removed, so the same edge chunk can occupy several slots.
        List<ChunkPos> cache = new ArrayList<>();
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                cache.add(new ChunkPos(WorldBounds.addChunkOffset(center.x(), dx),
                        WorldBounds.addChunkOffset(center.z(), dz)));
            }
        }

        assertEquals(cache.size() / 2,
                WorldGenRegion.getChunkIndex(center, size, center.x(), center.z()),
                "the center must use its own slot with the required generation status");
        for (ChunkPos requested : cache) {
            int index = WorldGenRegion.getChunkIndex(center, size, requested.x(), requested.z());
            assertEquals(index, WorldGenRegion.getChunkIndex(center, size,
                    requested.xHi(), requested.xLo(), requested.zHi(), requested.zLo()));
            assertTrue(index >= 0 && index < cache.size());
            assertEquals(requested, cache.get(index), "wrong chunk in window centered at " + center);
            // Edge duplicates must select the slot nearest the center: that
            // slot has the strongest scheduled generation-status dependency.
            int dx = Math.toIntExact(WorldBounds.signedDifferenceAsLong(requested.x(), center.x()));
            int dz = Math.toIntExact(WorldBounds.signedDifferenceAsLong(requested.z(), center.z()));
            assertEquals((dz + radius) * size + dx + radius, index);
        }
    }
}
