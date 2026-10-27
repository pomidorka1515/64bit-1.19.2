package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class Int1088SpatialSetTest {
    @Test void everyLimbSurvivesGrowthDeletionReinsertionAndOrderedDrain() {
        Int1088SpatialSet set = new Int1088SpatialSet(0);
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        positions.add(new BlockPos(0L, -4, 0L));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 value = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                positions.add(new BlockPos(value, -4, Int1088.ZERO));
                positions.add(new BlockPos(Int1088.ZERO, -4, value));
            }
        }
        positions.add(new BlockPos(Int1088.MIN, Integer.MIN_VALUE, Int1088.MAX));
        positions.add(new BlockPos(Int1088.MAX, Integer.MAX_VALUE, Int1088.MIN));
        for (BlockPos pos : positions) {
            assertFalse(set.add(pos.getX(), pos.getY(), pos.getZ()));
            assertTrue(set.add(pos.getX(), pos.getY(), pos.getZ()));
        }
        java.util.List<BlockPos> expected = new java.util.ArrayList<>();
        for (int i = 0; i < positions.size(); ++i) {
            BlockPos pos = positions.get(i);
            if (i % 3 == 0) {
                assertTrue(set.rem(pos.getX(), pos.getY(), pos.getZ()));
                assertFalse(set.rem(pos.getX(), pos.getY(), pos.getZ()));
            } else expected.add(pos);
        }
        for (int i = 0; i < positions.size(); i += 3) {
            BlockPos pos = positions.get(i);
            assertFalse(set.add(pos.getX(), pos.getY(), pos.getZ()));
            expected.add(pos);
        }
        Int1088SpatialSet.Position out = new Int1088SpatialSet.Position();
        for (BlockPos pos : expected) {
            set.removeFirstLong(out);
            assertEquals(pos, new BlockPos(Int1088.fromLimbs(out.x, 0, out.xLength), out.y,
                    Int1088.fromLimbs(out.z, 0, out.zLength)));
        }
        assertTrue(set.isEmpty());
    }

    @Test void wideViewsOrderingAndGroupDeletion() {
        Int1088SpatialSet set = new Int1088SpatialSet(0);
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        long[] x = new long[19], z = new long[20];
        wide.copyFixed(x, 1); Int1088.MIN.copyFixed(z, 2);
        assertFalse(set.add(x, 1, 17, -3, z, 2, 17));
        assertTrue(set.add(wide, -3, Int1088.MIN));
        assertFalse(set.add(wide, -4, Int1088.MIN));
        for (int i = 0; i < 300; i++) assertFalse(set.add(Int1088.fromLong(i * 4L), i, Int1088.fromLong(-i * 4L)));
        for (int i = 0; i < 300; i += 2) assertTrue(set.rem(Int1088.fromLong(i * 4L), i, Int1088.fromLong(-i * 4L)));
        Int1088SpatialSet.Position out = new Int1088SpatialSet.Position();
        set.removeFirstLong(out);
        assertEquals(wide, Int1088.fromLimbs(out.x, 0, out.xLength));
        assertEquals(Int1088.MIN, Int1088.fromLimbs(out.z, 0, out.zLength));
        assertEquals(-4, out.y);
        set.removeFirstLong(out); assertEquals(-3, out.y);
        for (int i = 1; i < 300; i += 2) {
            set.removeFirstLong(out);
            assertEquals(Int1088.fromLong(i * 4L), Int1088.fromLimbs(out.x, 0, out.xLength));
            assertEquals(Int1088.fromLong(-i * 4L), Int1088.fromLimbs(out.z, 0, out.zLength));
            assertEquals(i, out.y);
        }
        assertTrue(set.isEmpty());
        assertThrows(NoSuchElementException.class, () -> set.removeFirstLong(out));
    }
}
