package net.minecraft.client.renderer.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class RenderRegionInt128Test {
    @Test
    void localIndicesCrossLowWordSeamsWithoutLosingHighHalves() {
        for (Int128 origin : new Int128[] {Int128.ZERO, Int128.of(7, -1), Int128.of(-7, -1),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK.subtract(Int128.fromLong(3))}) {
            for (int i = 0; i < 4; ++i) {
                assertEquals(i, RenderChunkRegion.localIndex(origin.add(Int128.fromLong(i)), origin, 4));
            }
            assertEquals(-1, RenderChunkRegion.localIndex(origin.subtract(Int128.ONE), origin, 4));
            assertEquals(-1, RenderChunkRegion.localIndex(origin.add(Int128.fromLong(4)), origin, 4));
            assertEquals(4, RenderRegionCache.regionSize(origin, origin.add(Int128.fromLong(3))));
        }
    }

    @Test
    void distantCoordinatesNeverAliasLocalSlots() {
        Int128 origin = Int128.of(7, 23);
        for (Int128 distant : new Int128[] {Int128.of(8, 23), Int128.of(6, 23), Int128.MIN, Int128.MAX,
                origin.add(Int128.fromLong(1L << 32))}) {
            assertEquals(-1, RenderChunkRegion.localIndex(distant, origin, 4));
        }
        assertEquals(-1, RenderChunkRegion.localIndex(origin, origin, 0));
    }

    @Test
    void oversizedAndReversedRegionsAreRejectedBeforeAllocationOrLookup() {
        assertThrows(IllegalArgumentException.class, () -> RenderRegionCache.regionSize(Int128.MIN, Int128.MAX));
        assertThrows(IllegalArgumentException.class, () -> RenderRegionCache.regionSize(Int128.ONE, Int128.ZERO));
        RenderRegionCache cache = new RenderRegionCache();
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null,
                new BlockPos(Int128.ZERO, 0, Int128.ZERO),
                new BlockPos(Int128.of(1, 0), 15, Int128.ZERO), 1));
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null,
                new BlockPos(0, 0, 0), new BlockPos(16384, 15, 16384), 0));
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null,
                BlockPos.ZERO, BlockPos.ZERO, -1));
    }

    @Test
    void missingAndDistantRegionColumnsReturnNoBlockEntity() {
        Int128 origin = Int128.of(7, -1);
        RenderChunkRegion region = new RenderChunkRegion(null, origin, origin,
                new RenderChunk[][] {null, new RenderChunk[2]});
        for (Int128 section : new Int128[] {origin, origin.add(Int128.ONE), Int128.of(8, -1)}) {
            assertNull(region.getBlockEntity(new BlockPos(section.shiftLeftSaturated(4), 64,
                    origin.shiftLeftSaturated(4))));
        }
    }

    @Test
    void translatedOpaqueWallHasIdenticalVisibility() {
        VisibilitySet expected = wallVisibility(Int128.ZERO, Int128.ZERO);
        for (Int128 base : new Int128[] {Int128.of(7, -16), Int128.of(-7, 0),
                WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK)}) {
            VisibilitySet actual = wallVisibility(base, base);
            for (Direction from : Direction.values()) {
                for (Direction to : Direction.values()) {
                    assertEquals(expected.visibilityBetween(from, to), actual.visibilityBetween(from, to));
                }
            }
        }
    }

    private static VisibilitySet wallVisibility(Int128 x, Int128 z) {
        VisGraph graph = new VisGraph();
        for (int y = 0; y < 16; ++y) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                graph.setOpaque(new BlockPos(x.add(Int128.fromLong(8)), y, z.add(Int128.fromLong(localZ))));
            }
        }
        return graph.resolve();
    }
}
