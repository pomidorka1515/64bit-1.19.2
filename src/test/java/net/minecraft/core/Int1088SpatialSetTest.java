package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class Int1088SpatialSetTest {
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
