package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class Int1088SetTest {
    @Test void growthDeletionAndCursor() {
        Int1088Set set = new Int1088Set(0);
        for (int i = -300; i < 300; i++) assertTrue(set.add((long)i));
        for (int i = 2; i < 17; i++) {
            long[] limbs = new long[17]; limbs[i] = 1;
            assertTrue(set.add(Int1088.of(limbs)));
            assertFalse(set.add(Int1088.of(limbs)));
        }
        for (int i = -300; i < 300; i += 2) assertTrue(set.remove((long)i));
        for (int i = -300; i < 300; i++) assertEquals((i & 1) != 0, set.contains((long)i));
        var cursor = set.cursor(); assertTrue(cursor.advance());
        set.add(1000L); assertThrows(ConcurrentModificationException.class, cursor::advance);
        set.clear(); assertTrue(set.isEmpty());
    }
}
