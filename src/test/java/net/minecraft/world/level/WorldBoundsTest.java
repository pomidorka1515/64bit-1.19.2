package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
   void forwardRangeHelpersStopAtBothWorldEdges() {
      assertTrue(WorldBounds.isAscendingBlockRange(Long.MAX_VALUE - 1L, Long.MAX_VALUE));
      assertTrue(WorldBounds.canAdvanceBlock(Long.MAX_VALUE - 1L, Long.MAX_VALUE));
      assertFalse(WorldBounds.canAdvanceBlock(Long.MAX_VALUE, Long.MAX_VALUE));
      assertFalse(WorldBounds.isAscendingBlockRange(Long.MAX_VALUE, Long.MIN_VALUE));
      assertTrue(WorldBounds.isAscendingIntRange(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
      assertTrue(WorldBounds.canAdvanceInt(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
      assertFalse(WorldBounds.canAdvanceInt(Integer.MAX_VALUE, Integer.MAX_VALUE));
      assertFalse(WorldBounds.isAscendingIntRange(Integer.MAX_VALUE, Integer.MIN_VALUE));
   }

   @Test
   void blockOffsetsNeverWrapToTheOppositeSide() {
      assertEquals(WorldBounds.MAX_BLOCK, WorldBounds.addBlockOffset(WorldBounds.MAX_BLOCK, 1L));
      assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.addBlockOffset(WorldBounds.MIN_BLOCK, -1L));
      assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.subtractBlockOffset(WorldBounds.MIN_BLOCK, 1L));
      assertEquals(0L, WorldBounds.subtractBlockOffset(Long.MIN_VALUE, Long.MIN_VALUE));
      assertEquals(WorldBounds.MAX_BLOCK, WorldBounds.subtractBlockOffset(WorldBounds.MAX_BLOCK, Long.MIN_VALUE));
      assertEquals(0L, WorldBounds.middleBlockCoordinate(Long.MIN_VALUE, Long.MAX_VALUE));
      assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(Long.MAX_VALUE, Long.MIN_VALUE));
      assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(Long.MIN_VALUE, Long.MAX_VALUE));
      assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(0L, Long.MIN_VALUE));
      assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(-1L, Long.MAX_VALUE));
      assertEquals(1L, WorldBounds.middleBlockCoordinate(0L, 1L));
      assertEquals(-1L, WorldBounds.middleBlockCoordinate(-2L, -1L));
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
   void finalChunkMembershipDoesNotWrapItsUpperBound() {
      long firstBlock = WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK);
      assertTrue(WorldBounds.isWithinChunk(firstBlock, firstBlock));
      assertTrue(WorldBounds.isWithinChunk(WorldBounds.MAX_BLOCK, firstBlock));
      assertFalse(WorldBounds.isWithinChunk(WorldBounds.MIN_BLOCK, firstBlock));
   }

   @Test
   void blockRangesAtWorldEdgesReachTheirEndpointThenStop() {
      List<BlockPos> atMaximum = new ArrayList<>();
      BlockPos.betweenClosed(Long.MAX_VALUE - 1L, 0, Long.MAX_VALUE - 1L, Long.MAX_VALUE, 0, Long.MAX_VALUE).forEach(atMaximum::add);
      assertEquals(4, atMaximum.size());
      assertEquals(new BlockPos(Long.MAX_VALUE, 0, Long.MAX_VALUE), atMaximum.get(3));

      List<BlockPos> atMinimum = new ArrayList<>();
      BlockPos.betweenClosed(Long.MIN_VALUE, 0, Long.MIN_VALUE, Long.MIN_VALUE + 1L, 0, Long.MIN_VALUE + 1L).forEach(atMinimum::add);
      assertEquals(4, atMinimum.size());
      assertEquals(new BlockPos(Long.MIN_VALUE + 1L, 0, Long.MIN_VALUE + 1L), atMinimum.get(3));

      assertFalse(BlockPos.betweenClosed(Long.MAX_VALUE, 0, 0L, Long.MIN_VALUE, 0, 0L).iterator().hasNext());
   }

   @Test
   void boundingBoxSpansAndCentersRemainValidAtWorldEdges() {
      BoundingBox fullWorld = new BoundingBox(Long.MIN_VALUE, 0, Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE);
      assertEquals(Long.MAX_VALUE, fullWorld.getXSpan());
      assertEquals(Long.MAX_VALUE, fullWorld.getZSpan());
      assertEquals(0L, fullWorld.getCenter().getX());
      assertEquals(0L, fullWorld.getCenter().getZ());
   }

   @Test
   void boundingBoxesStayOrderedWhenExpandedAtWorldEdges() {
      BoundingBox positiveEdge = new BoundingBox(Long.MAX_VALUE - 2L, 0, Long.MAX_VALUE - 2L, Long.MAX_VALUE, 6, Long.MAX_VALUE);
      BoundingBox expanded = positiveEdge.inflatedBy(3);
      assertEquals(Long.MAX_VALUE - 5L, expanded.minX());
      assertEquals(Long.MAX_VALUE, expanded.maxX());
      assertEquals(6L, expanded.getXSpan());
      assertEquals(Long.MAX_VALUE - 5L, expanded.minZ());
      assertEquals(Long.MAX_VALUE, expanded.maxZ());
      assertEquals(6L, expanded.getZSpan());
      assertTrue(expanded.isInside(new BlockPos(Long.MAX_VALUE, 3, Long.MAX_VALUE)));
      assertFalse(expanded.intersects(Long.MAX_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE));
   }

   @Test
   void blockRangeHelpersSaturateRatherThanWrap() {
      assertEquals(3L, WorldBounds.inclusiveBlockSpan(Long.MAX_VALUE - 2L, Long.MAX_VALUE));
      assertEquals(0L, WorldBounds.inclusiveBlockSpan(Long.MAX_VALUE, Long.MIN_VALUE));
      assertEquals(Long.MAX_VALUE, WorldBounds.inclusiveBlockSpan(Long.MIN_VALUE, Long.MAX_VALUE));
      assertEquals(2L, WorldBounds.blockOffsetInRange(Long.MAX_VALUE, Long.MAX_VALUE - 2L, Long.MAX_VALUE));
      assertTrue(WorldBounds.isPositiveIntSpan(1L));
      assertTrue(WorldBounds.isPositiveIntSpan(Integer.MAX_VALUE));
      assertFalse(WorldBounds.isPositiveIntSpan(0L));
      assertFalse(WorldBounds.isPositiveIntSpan((long)Integer.MAX_VALUE + 1L));
      assertTrue(WorldBounds.isVoxelShapeSize(0, 0, 0));
      assertTrue(WorldBounds.isVoxelShapeSize(3, 7, 7));
      assertFalse(WorldBounds.isVoxelShapeSize(Integer.MAX_VALUE, 2, 1));
   }

   @Test
   void cursorRejectsOverflowingRanges() {
      assertThrows(ArithmeticException.class, () -> new Cursor3D(Long.MIN_VALUE, 0, 0L, Long.MAX_VALUE, 0, 0L));
   }
}
