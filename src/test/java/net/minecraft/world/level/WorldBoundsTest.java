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
   void surfaceArithmeticIsFiniteAndSaturating() {
      assertEquals(Integer.MAX_VALUE, WorldBounds.addSaturated(Integer.MAX_VALUE, 1));
      assertEquals(Integer.MIN_VALUE, WorldBounds.addSaturated(Integer.MIN_VALUE, -1));
      assertEquals(Integer.MAX_VALUE, WorldBounds.multiplySaturated(Integer.MAX_VALUE, 2));
      assertEquals(Integer.MIN_VALUE, WorldBounds.multiplySaturated(Integer.MIN_VALUE, 2));
      assertEquals(-1.0D, WorldBounds.clampNoise(Double.NEGATIVE_INFINITY));
      assertEquals(0.0D, WorldBounds.clampNoise(Double.NaN));
      assertEquals(1.0D, WorldBounds.clampNoise(Double.POSITIVE_INFINITY));
      assertEquals(WorldBounds.MAX_BLOCK - 15L, WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK));
      assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK));
   }

   @Test
   void cursorRejectsOverflowingRanges() {
      assertThrows(ArithmeticException.class, () -> new Cursor3D(Long.MIN_VALUE, 0, 0L, Long.MAX_VALUE, 0, 0L));
   }
}
