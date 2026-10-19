package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class WorldBoundsTest {
    @Test
    void fullWidthChunkDomainContainsExactlyTheRepresentableBlocks() {
        assertEquals(Int128.MIN, WorldBounds.MIN_BLOCK_128);
        assertEquals(Int128.MAX, WorldBounds.MAX_BLOCK_128);
        assertEquals(Int128.of(Long.MIN_VALUE >> 4, 0L), WorldBounds.MIN_CHUNK_128);
        assertEquals(Int128.of(Long.MAX_VALUE >> 4, -1L), WorldBounds.MAX_CHUNK_128);
        assertEquals(Int128.MIN, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK_128));
        assertEquals(Int128.MAX.subtract(Int128.fromLong(15L)), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK_128));
        assertEquals(WorldBounds.MIN_CHUNK_128, WorldBounds.clampChunk(Int128.MIN));
        assertEquals(WorldBounds.MAX_CHUNK_128, WorldBounds.clampChunk(Int128.MAX));
        assertEquals(Int128.MIN, WorldBounds.chunkToBlock(Int128.MIN));
        assertEquals(Int128.MAX.subtract(Int128.fromLong(15L)), WorldBounds.chunkToBlock(Int128.MAX));
        assertEquals(Int128.fromLong(-16L), WorldBounds.chunkToBlock(Int128.fromLong(-1L)));
    }

    @Test
    void fullWidthChunkOffsetsSaturateOrRejectAtTheEdges() {
        for (long offset : new long[] {1L, Long.MAX_VALUE}) {
            assertEquals(WorldBounds.MAX_CHUNK_128, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK_128, offset));
            assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK_128, offset));
        }
        for (long offset : new long[] {-1L, Long.MIN_VALUE}) {
            assertEquals(WorldBounds.MIN_CHUNK_128, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK_128, offset));
            assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK_128, offset));
        }
        for (Int128 edge : new Int128[] {WorldBounds.MIN_CHUNK_128, WorldBounds.MAX_CHUNK_128}) {
            assertEquals(edge, WorldBounds.tryAddChunkOffset(edge, 0L));
        }
        assertEquals(WorldBounds.MAX_CHUNK_128.subtract(Int128.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK_128, -1L));
        assertEquals(WorldBounds.MIN_CHUNK_128.add(Int128.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK_128, 1L));
        assertNull(WorldBounds.tryAddChunkOffset(Int128.MAX, Long.MIN_VALUE));
        assertNull(WorldBounds.tryAddChunkOffset(Int128.MIN, Long.MAX_VALUE));
        assertEquals(WorldBounds.MAX_CHUNK_128, WorldBounds.addChunkOffset(Int128.MAX, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK_128, WorldBounds.addChunkOffset(Int128.MIN, Long.MIN_VALUE));
    }

    @Test
    void fullWidthConversionsAndOffsetsMatchBigInteger() {
        BigInteger minBlock = BigInteger.ONE.shiftLeft(127).negate();
        BigInteger maxBlock = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
        BigInteger minChunk = minBlock.shiftRight(4);
        BigInteger maxChunk = maxBlock.shiftRight(4);
        Random random = new Random(0x5201L);
        for (int i = 0; i < 1000; ++i) {
            Int128 coordinate = Int128.of(random.nextLong(), random.nextLong());
            BigInteger value = new BigInteger(coordinate.toString());
            BigInteger clamped = value.max(minChunk).min(maxChunk);
            assertEquals(clamped.toString(), WorldBounds.clampChunk(coordinate).toString());
            assertEquals(clamped.shiftLeft(4).toString(), WorldBounds.chunkToBlock(coordinate).toString());

            Int128 chunk = coordinate.shiftRight(4);
            assertEquals(value.shiftRight(4).toString(), chunk.toString());
            long offset = random.nextLong();
            BigInteger sum = value.shiftRight(4).add(BigInteger.valueOf(offset));
            assertEquals(sum.max(minChunk).min(maxChunk).toString(), WorldBounds.addChunkOffset(chunk, offset).toString());
            if (sum.compareTo(minChunk) < 0 || sum.compareTo(maxChunk) > 0) {
                assertNull(WorldBounds.tryAddChunkOffset(chunk, offset));
            } else {
                assertEquals(sum.toString(), WorldBounds.tryAddChunkOffset(chunk, offset).toString());
            }

            // Legal region coordinates round-trip their chunk and local index exactly.
            Int128 region = chunk.shiftRight(5);
            long local = chunk.lo() & 31L;
            assertEquals(chunk, WorldBounds.regionToChunk(region, local));
        }
    }

    @Test
    void fullWidthRegionConversionSaturatesWithoutWrapping() {
        assertEquals(Int128.MAX, WorldBounds.regionToChunk(Int128.MAX, Long.MAX_VALUE));
        assertEquals(Int128.MIN, WorldBounds.regionToChunk(Int128.MIN, Long.MIN_VALUE));
        assertEquals(Int128.MAX.subtract(Int128.ONE), WorldBounds.regionToChunk(Int128.MAX, -1L));
        assertEquals(Int128.MIN.add(Int128.ONE), WorldBounds.regionToChunk(Int128.MIN, 1L));
        assertEquals(Int128.fromLong(-1L), WorldBounds.regionToChunk(Int128.fromLong(-1L), 31L));
        assertEquals(WorldBounds.MIN_CHUNK_128,
                WorldBounds.regionToChunk(WorldBounds.MIN_CHUNK_128.shiftRight(5), 0L));
        assertEquals(WorldBounds.MAX_CHUNK_128,
                WorldBounds.regionToChunk(WorldBounds.MAX_CHUNK_128.shiftRight(5), 31L));
    }

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
        assertEquals(1L, new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK).getChessboardDistance(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK - 1L)));
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
    void doubleBlockOffsetsKeepTheIntegerCoordinate() {
        long huge = 52999999999999981L;
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, 0.5D));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, 0.0D));
        assertEquals(huge + 3L, WorldBounds.addDoubleBlockOffset(huge, 3.9D));
        assertEquals(huge - 2L, WorldBounds.addDoubleBlockOffset(huge, -1.1D));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, Double.NaN));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, Double.POSITIVE_INFINITY));
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
    void sectionNotificationsAtWorldEdgesNeverWrapToTheOppositeSide() {
        List<SectionPos> atMaximum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Long.MAX_VALUE, 64, Long.MAX_VALUE, atMaximum::add);
        assertTrue(atMaximum.stream().allMatch(section -> section.x() >= WorldBounds.MAX_CHUNK - 1L && section.x() <= WorldBounds.MAX_CHUNK && section.z() >= WorldBounds.MAX_CHUNK - 1L && section.z() <= WorldBounds.MAX_CHUNK));

        List<SectionPos> atMinimum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Long.MIN_VALUE, 64, Long.MIN_VALUE, atMinimum::add);
        assertTrue(atMinimum.stream().allMatch(section -> section.x() >= WorldBounds.MIN_CHUNK && section.x() <= WorldBounds.MIN_CHUNK + 1L && section.z() >= WorldBounds.MIN_CHUNK && section.z() <= WorldBounds.MIN_CHUNK + 1L));
    }

    @Test
    void sectionRangesAtWorldEdgesRemainInsideTheChunkDomain() {
        assertTrue(SectionPos.cube(SectionPos.of(WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK), 2)
                        .allMatch(section -> WorldBounds.isValidChunk(section.x(), section.z())));
        assertTrue(SectionPos.aroundChunk(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK), 2, 0, 1)
                        .allMatch(section -> WorldBounds.isValidChunk(section.x(), section.z())));
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
        assertFalse(WorldBounds.isPositiveIntSpan((long) Integer.MAX_VALUE + 1L));
        assertTrue(WorldBounds.isVoxelShapeSize(0, 0, 0));
        assertTrue(WorldBounds.isVoxelShapeSize(3, 7, 7));
        assertFalse(WorldBounds.isVoxelShapeSize(Integer.MAX_VALUE, 2, 1));
    }

    @Test
    void cursorTraversesOverflowingRangesLazily() {
        Cursor3D cursor = new Cursor3D(Long.MIN_VALUE, 0, 0L, Long.MAX_VALUE, 0, 0L);
        assertTrue(cursor.advance());
        assertEquals(Long.MIN_VALUE, cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Long.MIN_VALUE + 1L, cursor.nextX());
    }

    @Test
    void cursorVisitsEdgeBlocksInOrderAndStops() {
        for (long origin : new long[] {Long.MIN_VALUE, Long.MAX_VALUE - 1L}) {
            Cursor3D cursor = new Cursor3D(origin, 4, origin, origin + 1L, 4, origin + 1L);
            for (int z = 0; z < 2; ++z) {
                for (int x = 0; x < 2; ++x) {
                    assertTrue(cursor.advance());
                    assertEquals(origin + x, cursor.nextX());
                    assertEquals(4, cursor.nextY());
                    assertEquals(origin + z, cursor.nextZ());
                    assertEquals(Cursor3D.TYPE_CORNER, cursor.getNextType());
                }
            }
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void sectionOffsetsAndConversionsStayAtWorldEdges() {
        SectionPos maximum = SectionPos.of(WorldBounds.MAX_CHUNK, 4, WorldBounds.MAX_CHUNK);
        SectionPos minimum = SectionPos.of(WorldBounds.MIN_CHUNK, 4, WorldBounds.MIN_CHUNK);
        assertEquals(WorldBounds.MAX_CHUNK, maximum.offset(1L, 0, 1L).x());
        assertEquals(WorldBounds.MIN_CHUNK, minimum.offset(-1L, 0, -1L).z());
        assertEquals(Long.MAX_VALUE, maximum.maxBlockX());
        assertEquals(Long.MIN_VALUE, minimum.minBlockZ());
        assertEquals(Long.MAX_VALUE, SectionPos.sectionToBlockCoord(WorldBounds.MAX_CHUNK, 16L));
        assertEquals(Long.MIN_VALUE, SectionPos.sectionToBlockCoord(WorldBounds.MIN_CHUNK, -1L));
        assertEquals(1L << 40, SectionPos.posToSectionCoord((double) (1L << 44)));
        assertEquals(4L, SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK - 1L, 0, WorldBounds.MAX_CHUNK - 1L,
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).count());
        List<SectionPos> sections = SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK - 1L, 0, WorldBounds.MAX_CHUNK - 1L,
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).toList();
        assertEquals(4, sections.size());
        assertEquals(maximum.offset(0L, -4, 0L), sections.get(3));
        assertEquals(0L, SectionPos.betweenClosedStream(1L, 0, 0L, 0L, 0, 0L).count());
    }
}
