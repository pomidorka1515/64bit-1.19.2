package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

class ChunkPosTest {
    @Test
    void blockChunkAndRegionConversionsMatchIndependentSignedShiftOracle() {
        List<BigInteger> values = new ArrayList<>(List.of(BigInteger.ZERO, BigInteger.ONE,
                BigInteger.ONE.negate(), BigInteger.ONE.shiftLeft(1087).negate(),
                BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE)));
        for (int bit : new int[] {63, 127, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                for (int delta : new int[] {-1, 0, 1}) {
                    values.add(BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign))
                            .add(BigInteger.valueOf(delta)));
                }
            }
        }
        for (BigInteger block : values) {
            Int1088 coordinate = Int1088.parseDecimal(block.toString());
            ChunkPos chunk = new ChunkPos(new BlockPos(coordinate, 100, coordinate));
            BigInteger expected = block.shiftRight(4);
            assertEquals(expected.toString(), chunk.x().toString());
            assertEquals(expected.toString(), chunk.z().toString());
            assertEquals(expected.shiftLeft(4).toString(), chunk.getMinBlockX().toString());
            assertEquals(expected.shiftLeft(4).add(BigInteger.valueOf(15)).toString(), chunk.getMaxBlockZ().toString());
            assertEquals(expected.shiftRight(5).toString(), chunk.getRegionX().toString());
            assertEquals(expected.and(BigInteger.valueOf(31)).intValue(), chunk.getRegionLocalZ());
            assertEquals(chunk.x(), WorldBounds.regionToChunk(chunk.getRegionX(), chunk.getRegionLocalX()));
            assertEquals(chunk, new ChunkPos(chunk.getBlockAt(15, -64, 0)));
        }
    }

    @Test
    void ordinaryChunkLocationsAndNegativeRoundingRemainUnchanged() {
        ChunkPos chunk = new ChunkPos(new BlockPos(-1, 72, -513));
        assertEquals(new ChunkPos(-1, -33), chunk);
        assertEquals(Int1088.fromLong(-1), chunk.getRegionX());
        assertEquals(Int1088.fromLong(-2), chunk.getRegionZ());
        assertEquals(31, chunk.getRegionLocalX());
        assertEquals(new BlockPos(-8, 72, -520), chunk.getMiddleBlockPosition(72));
        assertEquals(2, chunk.getChessboardDistance(new ChunkPos(1, -32)));
    }

    @Test
    void rangeCursorKeepsUpperLimbsInBothDirectionsAndStopsAtChunkEdges() {
        for (Int1088 start : List.of(WorldBounds.MIN_CHUNK,
                Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.ONE),
                WorldBounds.MAX_CHUNK.subtractSaturated(Int1088.ONE))) {
            ChunkPos first = new ChunkPos(start, start);
            ChunkPos last = new ChunkPos(start.addSaturated(Int1088.ONE), start.addSaturated(Int1088.ONE));
            List<ChunkPos> expected = List.of(first, new ChunkPos(last.x(), first.z()),
                    new ChunkPos(first.x(), last.z()), last);
            assertEquals(expected, ChunkPos.rangeClosed(first, last).toList());
            assertEquals(List.of(last, expected.get(2), expected.get(1), first),
                    ChunkPos.rangeClosed(last, first).toList());
            ChunkPos.RangeCursor cursor = ChunkPos.rangeClosedCursor(last, last);
            assertTrue(cursor.advance());
            assertEquals(last, cursor.toChunkPos());
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
    }
}
