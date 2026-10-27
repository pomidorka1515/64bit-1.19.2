package net.minecraft.client.renderer.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class RenderRegionInt1088Test {
    @Test
    void allocationFreeBlockIndexMatchesFullWidthSectionIndex() {
        Random random = new Random(628);
        for (int n = 0; n < 2000; ++n) {
            long[] limbs = new long[Int1088.LIMBS];
            for (int i = 0; i < limbs.length; ++i) limbs[i] = random.nextLong();
            Int1088 block = Int1088.fromFixedLimbs(limbs);
            Int1088 section = block.shiftRight(4);
            Int1088 origin = section.addSaturated(Int1088.fromLong(random.nextInt(11) - 5));
            assertEquals(RenderChunkRegion.localIndex(section, origin, 4),
                    RenderChunkRegion.localBlockIndex(block, origin, 4));
            assertEquals(-1, RenderChunkRegion.localBlockIndex(block, section.xor(Int1088.ONE.shiftLeftSaturated(1024)), 4));
        }
        assertEquals(-1, RenderChunkRegion.localBlockIndex(Int1088.MIN, Int1088.MAX, 4));
        assertEquals(0, RenderChunkRegion.localBlockIndex(Int1088.MAX, Int1088.MAX.shiftRight(4), 4));
    }
    @Test
    void localIndicesCrossLimbSeamsAndDomainEdges() {
        for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 7),
                Int1088.ONE.shiftLeftSaturated(1024), WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK.subtract(Int1088.fromLong(3))}) {
            for (int i = 0; i < 4; ++i) {
                assertEquals(i, RenderChunkRegion.localIndex(origin.addSaturated(Int1088.fromLong(i)), origin, 4));
            }
            assertEquals(-1, RenderChunkRegion.localIndex(origin.subtractSaturated(Int1088.ONE), origin, 4));
            assertEquals(-1, RenderChunkRegion.localIndex(origin.addSaturated(Int1088.fromLong(4)), origin, 4));
            assertEquals(4, RenderRegionCache.regionSize(origin, origin.addSaturated(Int1088.fromLong(3))));
        }
    }

    @Test
    void distantCoordinatesNeverAliasLocalSlots() {
        Int1088 origin = Int1088.of(23, 7);
        for (Int1088 distant : new Int1088[] {Int1088.of(23, 8), Int1088.of(23, 6),
                Int1088.MIN, Int1088.MAX, origin.addSaturated(Int1088.ONE.shiftLeftSaturated(1024))}) {
            assertEquals(-1, RenderChunkRegion.localIndex(distant, origin, 4));
        }
        assertEquals(-1, RenderChunkRegion.localIndex(origin, origin, 0));
    }

    @Test
    void oversizedAndReversedRegionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RenderRegionCache.regionSize(Int1088.MIN, Int1088.MAX));
        assertThrows(IllegalArgumentException.class, () -> RenderRegionCache.regionSize(Int1088.ONE, Int1088.ZERO));
        RenderRegionCache cache = new RenderRegionCache();
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null,
                new BlockPos(Int1088.ZERO, 0, Int1088.ZERO),
                new BlockPos(Int1088.ONE.shiftLeftSaturated(1024), 15, Int1088.ZERO), 1));
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null,
                new BlockPos(0, 0, 0), new BlockPos(16384, 15, 16384), 0));
        assertThrows(IllegalArgumentException.class, () -> cache.createRegion(null, BlockPos.ZERO, BlockPos.ZERO, -1));
    }

    @Test
    void missingAndDistantRegionColumnsReturnNoBlockEntity() {
        Int1088 origin = Int1088.of(-1, 7);
        RenderChunkRegion region = new RenderChunkRegion(null, origin, origin, new RenderChunk[][] {null, new RenderChunk[2]});
        for (Int1088 section : new Int1088[] {origin, origin.addSaturated(Int1088.ONE), Int1088.of(-1, 8)}) {
            assertNull(region.getBlockEntity(new BlockPos(section.shiftLeftSaturated(4), 64, origin.shiftLeftSaturated(4))));
        }
    }

    @Test
    void translatedOpaqueWallHasIdenticalVisibility() {
        VisibilitySet expected = wallVisibility(Int1088.ZERO, Int1088.ZERO);
        for (Int1088 base : new Int1088[] {Int1088.of(-16, 7), Int1088.ONE.shiftLeftSaturated(1024),
                WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK)}) {
            VisibilitySet actual = wallVisibility(base, base);
            for (Direction from : Direction.values()) for (Direction to : Direction.values()) {
                assertEquals(expected.visibilityBetween(from, to), actual.visibilityBetween(from, to));
            }
        }
    }

    private static VisibilitySet wallVisibility(Int1088 x, Int1088 z) {
        VisGraph graph = new VisGraph();
        for (int y = 0; y < 16; ++y) for (int localZ = 0; localZ < 16; ++localZ) {
            graph.setOpaque(new BlockPos(x.addSaturated(Int1088.fromLong(8)), y, z.addSaturated(Int1088.fromLong(localZ))));
        }
        return graph.resolve();
    }
}
