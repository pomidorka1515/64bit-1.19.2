package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Int1088;
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
        assertEquals(Int1088.MIN, WorldBounds.addDoubleBlockOffset(Int1088.MIN, -1.0D));
        assertEquals(Long.MAX_VALUE, Mth.square(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.multiplySaturated(Long.MIN_VALUE, -1L));
        assertEquals(Long.MAX_VALUE, WorldBounds.addSubtract(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        BlockPos edge = new BlockPos(Int1088.MAX.subtract(Int1088.fromLong(2)), 0, Int1088.MIN.add(Int1088.fromLong(2)));
        assertEquals(new BlockPos(Int1088.MAX.subtract(Int1088.ONE), 0, Int1088.MIN.add(Int1088.ONE)), edge.offset(1.0D, 0.0D, -1.0D));
        assertEquals(BlockPos.ZERO, edge.cross(edge));
        assertEquals(Int1088.MAX, BlockPos.ZERO.subtract(new BlockPos(Int1088.MIN, 0, Int1088.ZERO)).getX());
        assertTrue(new Vec3i(Int1088.MAX, 0, Int1088.ZERO).compareTo(new Vec3i(Int1088.MIN, 0, Int1088.ZERO)) > 0);
        assertEquals(Integer.MAX_VALUE, edge.distManhattan(BlockPos.ZERO));
    }

    @Test
    void samplesStayWithinBoundsAndFullWidthRangesDoNotThrow() {
        RandomSource random = RandomSource.create(42L);
        for (BlockPos pos : BlockPos.randomBetweenClosed(random, 100, Int1088.MAX.subtract(Int1088.fromLong(3)), 0, Int1088.MIN, Int1088.MAX, 0, Int1088.MIN.add(Int1088.fromLong(3)))) {
            assertTrue(pos.getX().compareTo(Int1088.MAX.subtract(Int1088.fromLong(3))) >= 0);
            assertTrue(pos.getZ().compareTo(Int1088.MIN.add(Int1088.fromLong(3))) <= 0);
        }
        assertDoesNotThrow(() -> BlockPos.randomBetweenClosed(random, 1, Int1088.MIN, 0, Int1088.MIN, Int1088.MAX, 0, Int1088.MAX).iterator().next());
    }

    @Test
    void cursorsRemainLazyAndTerminateAtBothEdges() {
        Cursor3D cursor = new Cursor3D(Int1088.MAX.subtract(Int1088.ONE), 0, Int1088.MIN, Int1088.MAX, 0, Int1088.MIN);
        assertTrue(cursor.advance());
        assertEquals(Int1088.MAX.subtract(Int1088.ONE), cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Int1088.MAX, cursor.nextX());
        assertFalse(cursor.advance());
        assertEquals(2L, SectionPos.betweenClosedStream(Long.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE).limit(2).count());
        assertTrue(new Node(Int1088.MIN, 0, Int1088.ZERO).distanceTo(new Node(Int1088.MAX, 0, Int1088.ZERO)) > 1.0E18F);
    }
}
