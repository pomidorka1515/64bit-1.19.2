package net.minecraft.client.renderer.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

class ModelBlockRendererCacheTest {
    @Test
    void cacheKeysRetainBothHighHalvesAndEndpointIdentity() {
        ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
        cache.disable();
        try {
            BlockPos[] positions = {
                    new BlockPos(Int128.ZERO, 64, Int128.ZERO),
                    new BlockPos(Int128.of(1, 0), 64, Int128.ZERO),
                    new BlockPos(Int128.ZERO, 64, Int128.of(-1, 0)),
                    new BlockPos(Int128.MIN, 64, Int128.MAX),
                    new BlockPos(Int128.MAX, 64, Int128.MIN)
            };
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int i = 0; i < positions.length; ++i) {
                cursor.set(positions[i]);
                cache.cacheLightColor(cursor, i + 100);
            }
            cursor.set(12, 64, 12);
            for (int i = 0; i < positions.length; ++i) {
                assertEquals(i + 100, cache.getCachedLightColor(positions[i]));
            }
            assertEquals(Integer.MAX_VALUE, cache.getCachedLightColor(cursor));
        } finally {
            cache.disable();
        }
    }

    @Test
    void cacheKeysRemainStableWhenMutableInputMoves() {
        ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
        cache.disable();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 100; ++x) {
            cursor.set(x, 64, 0);
            cache.cacheLightColor(cursor, x);
        }

        cursor.set(200, 64, 0);

        assertEquals(0, cache.getCachedLightColor(new BlockPos(0, 64, 0)));
        assertEquals(50, cache.getCachedLightColor(new BlockPos(50, 64, 0)));
        assertEquals(99, cache.getCachedLightColor(new BlockPos(99, 64, 0)));
        assertEquals(Integer.MAX_VALUE, cache.getCachedLightColor(cursor));
        cache.disable();
    }
}
