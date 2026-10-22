package net.minecraft.world.level;

import java.util.List;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
