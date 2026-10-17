package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.pathfinder.Node;
import org.junit.jupiter.api.Test;

class WorldEdgeArithmeticTest {
    @Test
    void clampsOverflowWithoutLosingLocalOffsetsOrCancellation() {
        assertEquals(-(1L << 62), WorldBounds.middleBlockCoordinate(Long.MIN_VALUE, 0));
        assertEquals(Long.MIN_VALUE, WorldBounds.addDoubleBlockOffset(-1L, -0x1.0p64));
        assertEquals(Long.MAX_VALUE, Mth.square(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.multiplySaturated(Long.MIN_VALUE, -1L));
        assertEquals(Long.MAX_VALUE, WorldBounds.addSubtract(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        BlockPos edge = new BlockPos(Long.MAX_VALUE - 2, 0, Long.MIN_VALUE + 2);
        assertEquals(new BlockPos(Long.MAX_VALUE - 1, 0, Long.MIN_VALUE + 1), edge.offset(1.0D, 0.0D, -1.0D));
        assertEquals(BlockPos.ZERO, edge.cross(edge));
        assertEquals(Long.MAX_VALUE, BlockPos.ZERO.subtract(new BlockPos(Long.MIN_VALUE, 0, 0)).getX());
        assertTrue(new Vec3i(Long.MAX_VALUE, 0, 0).compareTo(new Vec3i(Long.MIN_VALUE, 0, 0)) > 0);
        assertEquals(Integer.MAX_VALUE, edge.distManhattan(BlockPos.ZERO));
    }

    @Test
    void samplesStayWithinBoundsAndFullWidthRangesDoNotThrow() {
        RandomSource random = RandomSource.create(42L);
        for (BlockPos pos : BlockPos.randomBetweenClosed(random, 100, Long.MAX_VALUE - 3, 0, Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MIN_VALUE + 3)) {
            assertTrue(pos.getX() >= Long.MAX_VALUE - 3);
            assertTrue(pos.getZ() <= Long.MIN_VALUE + 3);
        }
        assertDoesNotThrow(() -> BlockPos.randomBetweenClosed(random, 1, Long.MIN_VALUE, 0, Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE).iterator().next());
    }

    @Test
    void cursorsRemainLazyAndTerminateAtBothEdges() {
        Cursor3D cursor = new Cursor3D(Long.MAX_VALUE - 1, 0, Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MIN_VALUE);
        assertTrue(cursor.advance());
        assertEquals(Long.MAX_VALUE - 1, cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Long.MAX_VALUE, cursor.nextX());
        assertFalse(cursor.advance());
        assertEquals(2L, SectionPos.betweenClosedStream(Long.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE).limit(2).count());
        assertTrue(new Node(Long.MIN_VALUE, 0, 0).distanceTo(new Node(Long.MAX_VALUE, 0, 0)) > 1.0E18F);
    }
}
