package net.minecraft.client.renderer.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelBlockRendererBrightnessTest {
    @Test
    void primitiveBrightnessLookupPreservesMissesHitsMutableKeysAndClear() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
        cache.disable();
        cache.enable();
        try {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 100; ++i) {
                cursor.set(i, 64, 0);
                assertEquals(0.2F, cache.getShadeBrightness(Blocks.STONE.defaultBlockState(), null, cursor));
            }
            cursor.set(Int1088.of(0, 1), 64, Int1088.ZERO);
            assertEquals(1.0F, cache.getShadeBrightness(Blocks.AIR.defaultBlockState(), null, cursor));
            cursor.set(50, 64, 0);
            // Null state ensures this really is a hit, not recomputation.
            assertEquals(0.2F, cache.getShadeBrightness(null, null, cursor));
            assertEquals(1.0F, cache.getShadeBrightness(null, null,
                    new BlockPos(Int1088.of(0, 1), 64, Int1088.ZERO)));
            // The oldest entry was evicted, and disable clears all entries.
            assertEquals(1.0F, cache.getShadeBrightness(Blocks.AIR.defaultBlockState(), null, new BlockPos(0, 64, 0)));
            cache.disable();
            cache.enable();
            assertEquals(1.0F, cache.getShadeBrightness(Blocks.AIR.defaultBlockState(), null, cursor));
        } finally {
            cache.disable();
        }
    }
}
