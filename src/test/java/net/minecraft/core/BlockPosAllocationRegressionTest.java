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
            BlockPos pos = new RawOnlyBlockPos(random.nextLong(), random.nextLong(), random.nextInt(),
                    random.nextLong(), random.nextLong());
            BlockPos other = new RawOnlyBlockPos(random.nextLong(), random.nextLong(), random.nextInt(),
                    random.nextLong(), random.nextLong());
            int xHash = 31 * Long.hashCode(pos.xHi()) + Long.hashCode(pos.xLo());
            int zHash = 31 * Long.hashCode(pos.zHi()) + Long.hashCode(pos.zLo());
            assertEquals((pos.getY() + zHash * 31) * 31 + xHash, pos.hashCode());
            assertEquals(pos.hashCode(), Vec3i.hashCode(pos.xHi(), pos.xLo(), pos.getY(), pos.zHi(), pos.zLo()));
            int comparison = Int128.of(pos.xHi(), pos.xLo()).compareTo(Int128.of(other.xHi(), other.xLo()));
            if (comparison == 0) comparison = Integer.compare(pos.getY(), other.getY());
            if (comparison == 0) comparison = Int128.of(pos.zHi(), pos.zLo()).compareTo(Int128.of(other.zHi(), other.zLo()));
            assertEquals(comparison, pos.compareTo(other));
            assertEquals(pos.compareTo(other), pos.compareTo(other.xHi(), other.xLo(), other.getY(), other.zHi(), other.zLo()));
            assertEquals(0, pos.compareTo(pos.xHi(), pos.xLo(), pos.getY(), pos.zHi(), pos.zLo()));
        }
        BlockPos pos = new RawOnlyBlockPos(17L, Long.MIN_VALUE, 64, -18L, Long.MIN_VALUE);
        assertTrue(pos.compareTo(17L, Long.MAX_VALUE, Integer.MAX_VALUE, 0L, 0L) > 0);
        assertTrue(pos.compareTo(17L, Long.MIN_VALUE, 65, Long.MIN_VALUE, 0L) < 0);
        assertTrue(pos.compareTo(17L, Long.MIN_VALUE, 64, -18L, Long.MAX_VALUE) > 0);
    }

    @Test
    void scratchOffsetsMatchImmutableResultsAtCarriesAndSaturation() {
        Int128[] edges = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.of(17L, -1L), Int128.of(-18L, 0L)};
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        for (Int128 x : edges) {
            for (Int128 z : edges) {
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
        BlockPos origin = new RawOnlyBlockPos(17L, -16L, 64, -18L, 16L);
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 20; ++i) checksumSink = scratchHotLoop(origin, scratch);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        checksumSink = scratchHotLoop(origin, scratch);
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(bytes < 1024L, "scratch loop allocated " + bytes + " bytes");
    }

    private static long scratchHotLoop(BlockPos origin, BlockPos.MutableBlockPos scratch) {
        long checksum = 1L;
        for (int i = 0; i < 100000; ++i) {
            scratch.setWithOffset(origin, i & 15, (i >>> 4) & 15, (i >>> 8) & 15);
            checksum += Vec3i.hashCode(scratch.xHi(), scratch.xLo(), scratch.getY(), scratch.zHi(), scratch.zLo())
                    + scratch.compareTo(origin.xHi(), origin.xLo(), origin.getY(), origin.zHi(), origin.zLo())
                    + rawChunkHash(scratch);
        }
        return checksum;
    }

    private static int rawChunkHash(BlockPos pos) {
        return ChunkPos.hash(pos.xHi() >> 4, Int128Math.rightShiftLo(pos.xHi(), pos.xLo(), 4),
                pos.zHi() >> 4, Int128Math.rightShiftLo(pos.zHi(), pos.zLo(), 4));
    }

    @Test
    void hotPathsNeverMaterializeCoordinateObjects() {
        BlockPos pos = new RawOnlyBlockPos(17L, -1L, 64, -18L, 0L);
        BlockPos other = new RawOnlyBlockPos(-2L, 3L, 7, 4L, -5L);
        assertEquals(new BlockPos(18L, 0L, 66, -19L, -1L), pos.offset(1L, 2, -1L));
        assertEquals(new BlockPos(18L, 0L, 66, -19L, -1L), pos.offset(1.5D, 2.0D, -0.5D));
        assertEquals(pos, new BlockPos(pos));
        assertEquals(pos.hashCode(), new BlockPos(pos).hashCode());
        assertTrue(pos.compareTo(other) > 0);
        assertEquals(new BlockPos(16L, 2L, 71, -14L, -5L), pos.offset(other));
        assertEquals(new BlockPos(19L, -4L, 57, -23L, 5L), pos.subtract(other));
        assertEquals(pos.offset(other), pos.mutable().move(other));
        assertEquals(pos.offset(1L, 0, 0L), pos.east());
    }

    @Test
    void primitiveDoubleOffsetsPreserveSaturationAndSpecialValues() {
        double[] offsets = {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                -0x1p128, Math.nextUp(-0x1p128), -0x1p127, Math.nextDown(-0x1p127),
                0x1p127, Math.nextDown(0x1p128), 0x1p128, -1.5D, -0.0D, 0.5D};
        Int128[] coordinates = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.of(17L, -1L)};
        for (Int128 coordinate : coordinates) {
            for (double offset : offsets) {
                Int128 expected = WorldBounds.addDoubleBlockOffset(coordinate, offset);
                BlockPos actual = new BlockPos(coordinate, 64, coordinate).offset(offset, 0.0D, offset);
                assertEquals(expected, actual.getX());
                assertEquals(expected, actual.getZ());
            }
        }
    }

    @Test
    void primitiveSubtractionMatchesSaturatingArithmetic() {
        Random random = new Random(0xB10C128L);
        Int128[] edges = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.fromLong(-1L)};
        for (Int128 a : edges) {
            for (Int128 b : edges) assertDifference(a, b);
        }
        for (int i = 0; i < 10000; ++i) {
            assertDifference(Int128.of(random.nextLong(), random.nextLong()),
                    Int128.of(random.nextLong(), random.nextLong()));
        }
    }

    private static void assertDifference(Int128 a, Int128 b) {
        Int128 expected = a.subtractSaturated(b);
        assertEquals(expected.hi(), Int128Math.subtractSaturatedHi(a.hi(), a.lo(), b.hi(), b.lo()));
        assertEquals(expected.lo(), Int128Math.subtractSaturatedLo(a.hi(), a.lo(), b.hi(), b.lo()));
        assertEquals(new BlockPos(expected, 5, expected), new BlockPos(a, 12, a).subtract(new BlockPos(b, 7, b)));
    }

    private static final class RawOnlyBlockPos extends BlockPos {
        RawOnlyBlockPos(long xHi, long xLo, int y, long zHi, long zLo) {
            super(xHi, xLo, y, zHi, zLo);
        }

        @Override
        public Int128 getX() {
            throw new AssertionError("Hot path materialized X");
        }

        @Override
        public Int128 getZ() {
            throw new AssertionError("Hot path materialized Z");
        }
    }
}
