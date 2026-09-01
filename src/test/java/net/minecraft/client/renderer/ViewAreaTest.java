package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class ViewAreaTest {
   @Test
   void positiveWorldEdgeUsesAUniqueLegalRenderWindow() {
      assertContiguousWindow(WorldBounds.MAX_CHUNK, 33, WorldBounds.MAX_CHUNK - 32L);
   }

   @Test
   void aNearPositiveWorldEdgeShiftsTheFullWindowInsideTheWorld() {
      assertContiguousWindow(WorldBounds.MAX_CHUNK - 1L, 33, WorldBounds.MAX_CHUNK - 32L);
   }

   @Test
   void negativeWorldEdgeUsesAUniqueLegalRenderWindow() {
      assertContiguousWindow(WorldBounds.MIN_CHUNK, 33, WorldBounds.MIN_CHUNK);
   }

   private static void assertContiguousWindow(long center, int width, long expectedFirst) {
      long first = ViewArea.firstChunkInView(center, width);
      assertEquals(expectedFirst, first);

      Set<Long> chunks = new HashSet<>();
      for (int offset = 0; offset < width; ++offset) {
         long chunk = first + offset;
         assertTrue(WorldBounds.isValidChunkCoordinate(chunk));
         chunks.add(chunk);
      }

      assertEquals(width, chunks.size());
      assertEquals(expectedFirst + width - 1L, first + width - 1L);
   }
}
