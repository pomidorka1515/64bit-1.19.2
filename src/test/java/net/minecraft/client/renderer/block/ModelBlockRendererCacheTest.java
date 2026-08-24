package net.minecraft.client.renderer.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ModelBlockRendererCacheTest {
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
