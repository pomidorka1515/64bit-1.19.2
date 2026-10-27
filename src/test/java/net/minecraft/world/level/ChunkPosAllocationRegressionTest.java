package net.minecraft.world.level;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPosAllocationRegressionTest {
    @Test
    void blockAndRegionCoordinatesRetainAllLimbs() {
        for (Int1088 coordinate : List.of(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK,
                Int1088.of(-1L, 123L), Int1088.of(0L, -124L), Int1088.fromLong(-1L))) {
            ChunkPos pos = new ChunkPos(coordinate, coordinate);
            assertEquals(WorldBounds.chunkToBlock(coordinate), pos.getMinBlockX());
            assertEquals(WorldBounds.chunkToBlock(coordinate), pos.getMinBlockZ());
            assertEquals(coordinate.shiftRight(5), pos.getRegionX());
            assertEquals(coordinate.shiftRight(5), pos.getRegionZ());
            assertEquals(pos.getRegionX(), pos.x().shiftRight(5));
            assertEquals(pos.getRegionZ(), pos.z().shiftRight(5));
        }
    }

    @Test
    void rangesCrossUnsignedLowLimbBoundariesInBothDirections() {
        ChunkPos start = new ChunkPos(Int1088.of(-1L, 7L), Int1088.of(-1L, -8L));
        ChunkPos end = new ChunkPos(Int1088.of(0L, 8L), Int1088.of(0L, -7L));
        List<ChunkPos> forward = List.of(start,
                new ChunkPos(end.x(), start.z()), new ChunkPos(start.x(), end.z()), end);
        List<ChunkPos> backward = List.of(end,
                new ChunkPos(start.x(), end.z()), new ChunkPos(end.x(), start.z()), start);
        assertEquals(forward, ChunkPos.rangeClosed(start, end).toList());
        assertEquals(backward, ChunkPos.rangeClosed(end, start).toList());
        assertEquals(1L, start.getChessboardDistance(end));
    }

    @Test
    void rangesTerminateAtWorldEdgesAndDistanceSaturates() {
        ChunkPos min = new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK);
        ChunkPos max = new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK);
        assertEquals(List.of(min), ChunkPos.rangeClosed(min, min).toList());
        assertEquals(List.of(max), ChunkPos.rangeClosed(max, max).toList());
        assertEquals(4L, ChunkPos.rangeClosed(min, 1).count());
        assertEquals(4L, ChunkPos.rangeClosed(max, 1).count());
        assertEquals(Long.MAX_VALUE, min.getChessboardDistance(max));
        assertEquals(Long.MAX_VALUE, max.getChessboardDistance(min));
    }

    @Test
    void cursorRegionLimbsMatchShiftsAcrossRegionAndLimbBoundaries() {
        ChunkPos.RangeCursor cursor = new ChunkPos.RangeCursor();
        for (Int1088 boundary : List.of(Int1088.ZERO, Int1088.fromLong(32L), Int1088.fromLong(-32L),
                Int1088.of(0L, 7L), Int1088.of(0L, -8L), Int1088.of(0L, 32L), Int1088.of(0L, -32L),
                WorldBounds.MIN_CHUNK.add(Int1088.fromLong(33L)),
                WorldBounds.MAX_CHUNK.subtract(Int1088.fromLong(33L)))) {
            Int1088 low = boundary.subtract(Int1088.fromLong(33L));
            Int1088 high = boundary.add(Int1088.fromLong(33L));
            for (boolean reverseX : new boolean[]{false, true}) {
                for (boolean reverseZ : new boolean[]{false, true}) {
                    ChunkPos start = new ChunkPos(reverseX ? high : low, reverseZ ? high : low);
                    ChunkPos end = new ChunkPos(reverseX ? low : high, reverseZ ? low : high);
                    cursor.reset(start, end);
                    int count = 0;
                    while (cursor.advance()) {
                        Int1088 expectedX = cursor.x().shiftRight(5);
                        Int1088 expectedZ = cursor.z().shiftRight(5);
                        assertEquals(expectedX, cursor.getRegionX());
                        assertEquals(expectedZ, cursor.getRegionZ());
                        ++count;
                    }
                    assertEquals(67 * 67, count);
                    assertFalse(cursor.advance());
                }
            }
        }
    }

    @Test
    void cursorMatchesStreamInEveryDirectionAndCanBeReused() {
        ChunkPos.RangeCursor cursor = new ChunkPos.RangeCursor();
        assertFalse(cursor.advance());
        for (Int1088 low : List.of(Int1088.of(-1L, 7L), Int1088.fromLong(-1L), WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK.subtract(Int1088.ONE))) {
            for (int dx : new int[]{0, 1}) {
                for (int dz : new int[]{0, 1}) {
                    ChunkPos start = new ChunkPos(low.add(Int1088.fromLong(dx)), low.add(Int1088.fromLong(dz)));
                    ChunkPos end = new ChunkPos(low.add(Int1088.fromLong(1 - dx)), low.add(Int1088.fromLong(1 - dz)));
                    assertSame(cursor, cursor.reset(start, end));
                    List<ChunkPos> actual = new ArrayList<>();
                    while (cursor.advance()) {
                        ChunkPos snapshot = cursor.toChunkPos();
                        actual.add(snapshot);
                        assertEquals(snapshot.xLimb(1), cursor.xLimb(1));
                        assertEquals(snapshot.xLimb(0), cursor.xLimb(0));
                        assertEquals(snapshot.zLimb(1), cursor.zLimb(1));
                        assertEquals(snapshot.zLimb(0), cursor.zLimb(0));
                        assertEquals(snapshot.getRegionX(), cursor.getRegionX());
                        assertEquals(snapshot.getRegionZ(), cursor.getRegionZ());
                        assertEquals(snapshot.getChessboardDistance(start), cursor.getChessboardDistance(start));
                    }
                    assertFalse(cursor.advance());
                    assertEquals(ChunkPos.rangeClosed(start, end).toList(), actual);
                    assertEquals(start, actual.get(0));
                    assertEquals(end, actual.get(actual.size() - 1));
                }
            }
        }
        for (Int1088 edge : List.of(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK)) {
            ChunkPos center = new ChunkPos(edge, edge);
            cursor.reset(center, 1);
            int count = 0;
            while (cursor.advance()) {
                ++count;
            }
            assertEquals(4, count);
            cursor.reset(center, center);
            assertTrue(cursor.advance());
            ChunkPos snapshot = cursor.toChunkPos();
            assertFalse(cursor.advance());
            cursor.reset(ChunkPos.ZERO, ChunkPos.ZERO);
            assertEquals(center, snapshot);
        }
    }
}
