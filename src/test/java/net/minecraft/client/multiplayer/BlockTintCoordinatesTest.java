package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class BlockTintCoordinatesTest {
    @Test
    void exactKeysAndInvalidationRefreshThreadLocalFastPath() {
        AtomicInteger calls = new AtomicInteger();
        BlockTintCache cache = new BlockTintCache(pos -> calls.incrementAndGet());
        for (Int1088 chunk : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 7), Int1088.of(-1, -7), Int1088.ONE.shiftLeftSaturated(1020),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            BlockPos pos = new BlockPos(chunk.shiftLeftSaturated(4), 64, chunk.shiftLeftSaturated(4));
            int first = cache.getColor(pos);
            assertEquals(first, cache.getColor(pos));
            cache.invalidateForChunk(chunk, chunk);
            assertEquals(first + 1, cache.getColor(pos));
            cache.invalidateAll();
            assertEquals(first + 2, cache.getColor(pos));
        }
    }

    @Test
    void upperLimbDifferencesNeverAlias() {
        AtomicInteger calls = new AtomicInteger();
        BlockTintCache cache = new BlockTintCache(pos -> calls.incrementAndGet());
        BlockPos near = new BlockPos(Int1088.fromLong(9), 64, Int1088.ZERO);
        BlockPos far = new BlockPos(Int1088.ONE.shiftLeftSaturated(1024).addSaturated(Int1088.fromLong(9)), 64, Int1088.ZERO);
        assertEquals(1, cache.getColor(near));
        assertEquals(2, cache.getColor(far));
        assertEquals(1, cache.getColor(near));
        cache.invalidateForChunk(far.getX().shiftRight(4), Int1088.ZERO);
        assertEquals(3, cache.getColor(far));
        assertEquals(1, cache.getColor(near));
    }

    @Test
    void equalLowWordsDoNotAliasAndNeighborInvalidationIsExact() {
        AtomicInteger calls = new AtomicInteger();
        BlockTintCache cache = new BlockTintCache(pos -> calls.incrementAndGet());
        Int1088 chunk = Int1088.of(-1, 7);
        BlockPos a = new BlockPos(chunk.shiftLeftSaturated(4), 64, Int1088.ZERO);
        BlockPos b = new BlockPos(Int1088.of(-1, 8).shiftLeftSaturated(4), 64, Int1088.ZERO);
        assertEquals(1, cache.getColor(a));
        assertEquals(2, cache.getColor(b));
        cache.invalidateForChunk(chunk.add(Int1088.ONE), Int1088.ZERO);
        assertEquals(2, cache.getColor(b));
        assertEquals(3, cache.getColor(a));
    }
}
