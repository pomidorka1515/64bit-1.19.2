package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.Cursor3D;
import org.junit.jupiter.api.Test;

class WorldBoundsTest {
   @Test
   void chunkDomainIsTheDomainOfChunksContainingLongBlocks() {
      assertEquals(Long.MIN_VALUE >> 4, WorldBounds.MIN_CHUNK);
      assertEquals(Long.MAX_VALUE >> 4, WorldBounds.MAX_CHUNK);
      assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK, 0L).x);
      assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK, 0L).x);
      assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK - 1L, 0L).x);
      assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK + 1L, 0L).x);
   }

   @Test
   void offsetsNeverWrapToTheOppositeSide() {
      assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, 1L));
      assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, -1L));
      assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, 1L));
      assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, -1L));
      assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, Long.MAX_VALUE));
      assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, Long.MIN_VALUE));
      assertEquals(1L, new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK).getChessboardDistance(
            new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK - 1L)));
   }

   @Test
   void cursorRejectsOverflowingRanges() {
      assertThrows(ArithmeticException.class, () -> new Cursor3D(Long.MIN_VALUE, 0, 0L, Long.MAX_VALUE, 0, 0L));
   }
}
