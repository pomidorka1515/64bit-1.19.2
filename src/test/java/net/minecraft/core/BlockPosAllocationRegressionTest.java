package net.minecraft.core;

import java.lang.management.ManagementFactory;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPosAllocationRegressionTest {
    private static volatile long checksumSink;

    @Test
    void rawHashAndComparisonPreserveFullWidthSemantics() {
        Random random = new Random(0xB10C128L);
        for (int i = 0; i < 10000; ++i) {
            BlockPos pos = new BlockPos(Int1088.of(random.nextLong(), random.nextLong()), random.nextInt(), Int1088.of(random.nextLong(), random.nextLong()));
            BlockPos other = new BlockPos(Int1088.of(random.nextLong(), random.nextLong()), random.nextInt(), Int1088.of(random.nextLong(), random.nextLong()));
            int xHash = pos.getX().hashXZ();
            int zHash = pos.getZ().hashXZ();
            assertEquals((pos.getY() + zHash * 31) * 31 + xHash, pos.hashCode());
            int comparison = pos.getX().compareTo(other.getX());
            if (comparison == 0) comparison = Integer.compare(pos.getY(), other.getY());
            if (comparison == 0) comparison = pos.getZ().compareTo(other.getZ());
            assertEquals(comparison, pos.compareTo(other));
            assertEquals(0, pos.compareTo(new BlockPos(pos)));
        }
        BlockPos pos = new BlockPos(Int1088.of(Long.MIN_VALUE, 17L), 64, Int1088.of(Long.MIN_VALUE, -18L));
        assertTrue(pos.compareTo(new BlockPos(Int1088.of(Long.MAX_VALUE, 17L), Integer.MAX_VALUE, Int1088.ZERO)) > 0);
        assertTrue(pos.compareTo(new BlockPos(pos.getX(), 65, pos.getZ())) < 0);

    }

    @Test
    void scratchOffsetsMatchImmutableResultsAtCarriesAndSaturation() {
        Int1088[] edges = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.of(-1L, 17L), Int1088.of(0L, -18L)};
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        for (Int1088 x : edges) {
            for (Int1088 z : edges) {
                BlockPos origin = new BlockPos(x, Integer.MAX_VALUE, z);
                for (long delta : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
                    BlockPos expected = origin.offset(delta, 1, delta);
                    assertSame(scratch, scratch.setWithOffset(origin, delta, 1, delta));
                    assertEquals(expected, scratch);
                    BlockPos.MutableBlockPos aliased = origin.mutable();
                    assertEquals(expected, aliased.setWithOffset(aliased, delta, 1, delta));
                    assertEquals(new ChunkPos(expected).hashCode(), rawChunkHash(scratch));
                }
            }
        }
    }

    @Test
    void scratchHotLoopDoesNotAllocatePerPosition() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        BlockPos origin = new BlockPos(Int1088.of(-16L, 17L), 64, Int1088.of(16L, -18L));
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 20; ++i) checksumSink = scratchHotLoop(origin, scratch);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        checksumSink = scratchHotLoop(origin, scratch);
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(bytes < 1024L, "scratch loop allocated " + bytes + " bytes");
    }

    @Test
    void traversalAllocatesOnlyIteratorScratch() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        for (Int1088 origin : new Int1088[] {Int1088.fromLong(-8), Int1088.fromLong(Long.MAX_VALUE - 7),
                Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.fromLong(8)),
                Int1088.MIN, Int1088.MAX.subtractSaturated(Int1088.fromLong(15))}) {
            var iterable = BlockPos.betweenClosed(origin, 0, origin, origin.addSaturated(Int1088.fromLong(15)), 15,
                    origin.addSaturated(Int1088.fromLong(15)));
            for (int i = 0; i < 20; ++i) checksumSink = traverse(iterable);
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread);
            checksumSink = traverse(iterable);
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            assertTrue(bytes < 2048, "traversal allocated " + bytes + " bytes");
        }
    }

    @Test
    void randomTraversalAllocatesOnlyIteratorScratch() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var random = net.minecraft.util.RandomSource.create(0x108843);
        for (Int1088[] bounds : new Int1088[][] {{Int1088.fromLong(-8), Int1088.fromLong(7)},
                {Int1088.MIN, Int1088.MAX}}) {
            var iterable = BlockPos.randomBetweenClosed(random, 10000, bounds[0], 0, bounds[0], bounds[1], 15, bounds[1]);
            for (int i = 0; i < 20; ++i) checksumSink = traverse(iterable);
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread);
            checksumSink = traverse(iterable);
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            assertTrue(bytes < 4096, "random traversal allocated " + bytes + " bytes");
        }
    }

    @Test
    void cursorAdvanceAndBoundaryClassificationDoNotAllocate() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Int1088 origin = Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.fromLong(8));
        Int1088 end = origin.addSaturated(Int1088.fromLong(15));
        Cursor3D cursor = new Cursor3D(origin, 0, origin, end, 15, end);
        for (int i = 0; i < 20; ++i) {
            cursor.reset(origin, 0, origin, end, 15, end);
            checksumSink = traverse(cursor);
        }
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20; ++i) {
            cursor.reset(origin, 0, origin, end, 15, end);
            checksumSink = traverse(cursor);
        }
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(bytes < 1024, "cursor advance allocated " + bytes + " bytes");
    }

    private static long traverse(Cursor3D cursor) {
        long checksum = 0;
        while (cursor.advance()) checksum += cursor.nextXLimb(0) + cursor.nextZLimb(0) + cursor.nextY() + cursor.getNextType();
        return checksum;
    }

    private static long traverse(Iterable<BlockPos> positions) {
        long checksum = 0;
        for (BlockPos pos : positions) checksum += pos.xLimb(0) + pos.zLimb(0) + pos.getY();
        return checksum;
    }

    private static long scratchHotLoop(BlockPos origin, BlockPos.MutableBlockPos scratch) {
        long checksum = 1L;
        for (int i = 0; i < 100000; ++i) {
            scratch.setWithOffset(origin, i & 15, (i >>> 4) & 15, (i >>> 8) & 15);
            checksum += scratch.hashCode() + scratch.compareTo(origin);
        }
        return checksum;
    }

    private static int rawChunkHash(BlockPos pos) {
        return new ChunkPos(pos).hashCode();
    }

    @Test
    void positionOperationsPreserveWideOperands() {
        BlockPos pos = new BlockPos(Int1088.of(-1L, 17L), 64, Int1088.of(0L, -18L));
        BlockPos other = new BlockPos(Int1088.of(3L, -2L), 7, Int1088.of(-5L, 4L));
        assertEquals(pos.offset(1L, 2, -1L), pos.offset(1.5D, 2.0D, -0.5D));
        assertEquals(pos, new BlockPos(pos));
        assertEquals(pos.hashCode(), new BlockPos(pos).hashCode());
        assertTrue(pos.compareTo(other) > 0);
        assertEquals(pos.offset(other), pos.mutable().move(other));
        assertEquals(pos.offset(1L, 0, 0L), pos.east());
        assertEquals(pos.getX().subtractSaturated(other.getX()), pos.subtract(other).getX());
        assertEquals(pos.getZ().subtractSaturated(other.getZ()), pos.subtract(other).getZ());
    }

    @Test
    void primitiveDoubleOffsetsPreserveSaturationAndSpecialValues() {
        double[] offsets = {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                -0x1p128, Math.nextUp(-0x1p128), -0x1p127, Math.nextDown(-0x1p127),
                0x1p127, Math.nextDown(0x1p128), 0x1p128, -1.5D, -0.0D, 0.5D};
        Int1088[] coordinates = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.of(-1L, 17L)};
        for (Int1088 coordinate : coordinates) {
            for (double offset : offsets) {
                Int1088 expected = WorldBounds.addDoubleBlockOffset(coordinate, offset);
                BlockPos actual = new BlockPos(coordinate, 64, coordinate).offset(offset, 0.0D, offset);
                assertEquals(expected, actual.getX());
                assertEquals(expected, actual.getZ());
            }
        }
    }

    @Test
    void primitiveSubtractionMatchesSaturatingArithmetic() {
        Random random = new Random(0xB10C128L);
        Int1088[] edges = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.fromLong(-1L)};
        for (Int1088 a : edges) {
            for (Int1088 b : edges) assertDifference(a, b);
        }
        for (int i = 0; i < 10000; ++i) {
            assertDifference(Int1088.of(random.nextLong(), random.nextLong()),
                    Int1088.of(random.nextLong(), random.nextLong()));
        }
    }

    private static void assertDifference(Int1088 a, Int1088 b) {
        Int1088 expected = a.subtractSaturated(b);
        assertEquals(new BlockPos(expected, 5, expected), new BlockPos(a, 12, a).subtract(new BlockPos(b, 7, b)));
    }

}
