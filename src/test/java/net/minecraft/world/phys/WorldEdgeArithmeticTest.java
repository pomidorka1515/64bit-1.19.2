package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Int128;
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
        assertEquals(Int128.MIN, WorldBounds.addDoubleBlockOffset(Int128.MIN, -1.0D));
        assertEquals(Long.MAX_VALUE, Mth.square(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.multiplySaturated(Long.MIN_VALUE, -1L));
        assertEquals(Long.MAX_VALUE, WorldBounds.addSubtract(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        BlockPos edge = new BlockPos(Int128.MAX.subtract(Int128.fromLong(2)), 0, Int128.MIN.add(Int128.fromLong(2)));
        assertEquals(new BlockPos(Int128.MAX.subtract(Int128.ONE), 0, Int128.MIN.add(Int128.ONE)), edge.offset(1.0D, 0.0D, -1.0D));
        assertEquals(BlockPos.ZERO, edge.cross(edge));
        assertEquals(Int128.MAX, BlockPos.ZERO.subtract(new BlockPos(Int128.MIN, 0, Int128.ZERO)).getX());
        assertTrue(new Vec3i(Int128.MAX, 0, Int128.ZERO).compareTo(new Vec3i(Int128.MIN, 0, Int128.ZERO)) > 0);
        assertEquals(Integer.MAX_VALUE, edge.distManhattan(BlockPos.ZERO));
    }

    @Test
    void samplesStayWithinBoundsAndFullWidthRangesDoNotThrow() {
        RandomSource random = RandomSource.create(42L);
        for (BlockPos pos : BlockPos.randomBetweenClosed(random, 100, Int128.MAX.subtract(Int128.fromLong(3)), 0, Int128.MIN, Int128.MAX, 0, Int128.MIN.add(Int128.fromLong(3)))) {
            assertTrue(pos.getX().compareTo(Int128.MAX.subtract(Int128.fromLong(3))) >= 0);
            assertTrue(pos.getZ().compareTo(Int128.MIN.add(Int128.fromLong(3))) <= 0);
        }
        assertDoesNotThrow(() -> BlockPos.randomBetweenClosed(random, 1, Int128.MIN, 0, Int128.MIN, Int128.MAX, 0, Int128.MAX).iterator().next());
    }

    @Test
    void cursorsRemainLazyAndTerminateAtBothEdges() {
        Cursor3D cursor = new Cursor3D(Int128.MAX.subtract(Int128.ONE), 0, Int128.MIN, Int128.MAX, 0, Int128.MIN);
        assertTrue(cursor.advance());
        assertEquals(Int128.MAX.subtract(Int128.ONE), cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Int128.MAX, cursor.nextX());
        assertFalse(cursor.advance());
        assertEquals(2L, SectionPos.betweenClosedStream(Long.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE).limit(2).count());
        assertTrue(new Node(Int128.MIN, 0, Int128.ZERO).distanceTo(new Node(Int128.MAX, 0, Int128.ZERO)) > 1.0E18F);
    }
}
