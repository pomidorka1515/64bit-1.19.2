package net.minecraft.world.level;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPosAllocationRegressionTest {
    @Test
    void blockAndRegionCoordinatesRetainAllLimbs() {
        for (Int128 coordinate : List.of(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK,
                Int128.of(123L, -1L), Int128.of(-124L, 0L), Int128.fromLong(-1L))) {
            ChunkPos pos = new ChunkPos(coordinate, coordinate);
            assertEquals(WorldBounds.chunkToBlock(coordinate), pos.getMinBlockX());
            assertEquals(WorldBounds.chunkToBlock(coordinate), pos.getMinBlockZ());
            assertEquals(coordinate.shiftRight(5), pos.getRegionX());
            assertEquals(coordinate.shiftRight(5), pos.getRegionZ());
            assertSame(pos.getRegionX(), pos.getRegionX());
            assertSame(pos.getRegionZ(), pos.getRegionZ());
            assertEquals(pos.getRegionX(), Int128.of(pos.getRegionXHi(), pos.getRegionXLo()));
            assertEquals(pos.getRegionZ(), Int128.of(pos.getRegionZHi(), pos.getRegionZLo()));
        }
    }

    @Test
    void rangesCrossUnsignedLowLimbBoundariesInBothDirections() {
        ChunkPos start = new ChunkPos(Int128.of(7L, -1L), Int128.of(-8L, -1L));
        ChunkPos end = new ChunkPos(Int128.of(8L, 0L), Int128.of(-7L, 0L));
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
        for (Int128 boundary : List.of(Int128.ZERO, Int128.fromLong(32L), Int128.fromLong(-32L),
                Int128.of(7L, 0L), Int128.of(-8L, 0L), Int128.of(32L, 0L), Int128.of(-32L, 0L),
                WorldBounds.MIN_CHUNK.add(Int128.fromLong(33L)),
                WorldBounds.MAX_CHUNK.subtract(Int128.fromLong(33L)))) {
            Int128 low = boundary.subtract(Int128.fromLong(33L));
            Int128 high = boundary.add(Int128.fromLong(33L));
            for (boolean reverseX : new boolean[]{false, true}) {
                for (boolean reverseZ : new boolean[]{false, true}) {
                    ChunkPos start = new ChunkPos(reverseX ? high : low, reverseZ ? high : low);
                    ChunkPos end = new ChunkPos(reverseX ? low : high, reverseZ ? low : high);
                    cursor.reset(start, end);
                    int count = 0;
                    while (cursor.advance()) {
                        Int128 expectedX = Int128.of(cursor.xHi(), cursor.xLo()).shiftRight(5);
                        Int128 expectedZ = Int128.of(cursor.zHi(), cursor.zLo()).shiftRight(5);
                        assertEquals(expectedX.hi(), cursor.getRegionXHi());
                        assertEquals(expectedX.lo(), cursor.getRegionXLo());
                        assertEquals(expectedZ.hi(), cursor.getRegionZHi());
                        assertEquals(expectedZ.lo(), cursor.getRegionZLo());
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
        for (Int128 low : List.of(Int128.of(7L, -1L), Int128.fromLong(-1L), WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK.subtract(Int128.ONE))) {
            for (int dx : new int[]{0, 1}) {
                for (int dz : new int[]{0, 1}) {
                    ChunkPos start = new ChunkPos(low.add(Int128.fromLong(dx)), low.add(Int128.fromLong(dz)));
                    ChunkPos end = new ChunkPos(low.add(Int128.fromLong(1 - dx)), low.add(Int128.fromLong(1 - dz)));
                    assertSame(cursor, cursor.reset(start, end));
                    List<ChunkPos> actual = new ArrayList<>();
                    while (cursor.advance()) {
                        ChunkPos snapshot = cursor.toChunkPos();
                        actual.add(snapshot);
                        assertEquals(snapshot.xHi(), cursor.xHi());
                        assertEquals(snapshot.xLo(), cursor.xLo());
                        assertEquals(snapshot.zHi(), cursor.zHi());
                        assertEquals(snapshot.zLo(), cursor.zLo());
                        assertEquals(snapshot.getRegionX(), Int128.of(cursor.getRegionXHi(), cursor.getRegionXLo()));
                        assertEquals(snapshot.getRegionZ(), Int128.of(cursor.getRegionZHi(), cursor.getRegionZLo()));
                        assertEquals(snapshot.getChessboardDistance(start), cursor.getChessboardDistance(start));
                    }
                    assertFalse(cursor.advance());
                    assertEquals(ChunkPos.rangeClosed(start, end).toList(), actual);
                    assertEquals(start, actual.get(0));
                    assertEquals(end, actual.get(actual.size() - 1));
                }
            }
        }
        for (Int128 edge : List.of(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK)) {
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
