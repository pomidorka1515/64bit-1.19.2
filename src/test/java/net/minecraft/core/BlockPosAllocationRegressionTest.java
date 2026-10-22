package net.minecraft.core;

import java.util.Random;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPosAllocationRegressionTest {
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
