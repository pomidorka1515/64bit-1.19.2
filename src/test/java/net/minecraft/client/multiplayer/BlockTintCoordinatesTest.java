package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class BlockTintCoordinatesTest {
    @Test
    void exactKeysAndInvalidationRefreshThreadLocalFastPath() {
        AtomicInteger calls = new AtomicInteger();
        BlockTintCache cache = new BlockTintCache(pos -> calls.incrementAndGet());
        for (Int128 chunk : new Int128[] {Int128.ZERO, Int128.of(7, -1), Int128.of(-7, -1),
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
    void equalLowWordsDoNotAliasAndNeighborInvalidationIsExact() {
        AtomicInteger calls = new AtomicInteger();
        BlockTintCache cache = new BlockTintCache(pos -> calls.incrementAndGet());
        Int128 chunk = Int128.of(7, -1);
        BlockPos a = new BlockPos(chunk.shiftLeftSaturated(4), 64, Int128.ZERO);
        BlockPos b = new BlockPos(Int128.of(8, -1).shiftLeftSaturated(4), 64, Int128.ZERO);
        assertEquals(1, cache.getColor(a));
        assertEquals(2, cache.getColor(b));
        cache.invalidateForChunk(chunk.add(Int128.ONE), Int128.ZERO);
        assertEquals(2, cache.getColor(b));
        assertEquals(3, cache.getColor(a));
    }
}
