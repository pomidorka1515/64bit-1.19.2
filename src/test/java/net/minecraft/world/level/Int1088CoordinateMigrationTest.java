package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

class Int1088CoordinateMigrationTest {
    private static final Int1088 WIDE = Int1088.ONE.shiftLeftSaturated(1024);

    @Test
    void distinctUpperLimbsRemainDistinctInChunkMaps() {
        ChunkPos first = new ChunkPos(WIDE, Int1088.ZERO);
        ChunkPos second = new ChunkPos(WIDE.addSaturated(Int1088.ONE.shiftLeftSaturated(512)), Int1088.ZERO);
        ChunkPosMap<String> map = new ChunkPosMap<>();
        map.put(first, "first");
        map.put(second, "second");
        assertNotEquals(first, second);
        assertEquals("first", map.get(first.x(), first.z()));
        assertEquals("second", map.get(second.x(), second.z()));
        assertNull(map.get(0L, 0L));
    }

    @Test
    void sectionAndBlockTraversalPreserveUpperLimbsAtEdges() {
        BlockPos pos = new BlockPos(WIDE, 3, WIDE.negate());
        SectionPos section = SectionPos.of(pos);
        assertTrue(section.contains(pos));
        assertEquals(WIDE, section.minBlockX());
        List<BlockPos> points = BlockPos.betweenClosedStream(
                Int1088.MAX.subtractSaturated(Int1088.ONE), 0, WIDE,
                Int1088.MAX, 0, WIDE).map(BlockPos::immutable).toList();
        assertEquals(2, points.size());
        assertEquals(Int1088.MAX, points.get(1).getX());
        assertEquals(WIDE, points.get(1).getZ());
    }

    @Test
    void chunkAndMidpointSaturateAtFullWidthEdges() {
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.regionToChunk(Int1088.MAX, 31));
        assertEquals(Int1088.ZERO, WorldBounds.middleBlockCoordinate(Int1088.MIN, Int1088.MAX));
        assertEquals(Int1088.MAX, WorldBounds.addBlockOffset(Int1088.MAX, 1));
        assertEquals(Int1088.MIN, WorldBounds.subtractBlockOffset(Int1088.MIN, 1));
    }
}
