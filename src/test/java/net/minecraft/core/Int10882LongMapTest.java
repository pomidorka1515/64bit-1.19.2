package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class Int10882LongMapTest {
    @Test void primitiveValuesAndWideIdentity() {
        Int10882LongMap map = new Int10882LongMap(0);
        map.defaultReturnValue(-7);
        for (int i = -400; i < 400; i++) assertEquals(-7, map.put((long)i, i * 3L));
        for (int i = 2; i < 17; i++) {
            long[] limbs = new long[17]; limbs[i] = 1;
            assertEquals(-7, map.put(Int1088.of(limbs), i));
        }
        for (int i = -400; i < 400; i += 2) assertEquals(i * 3L, map.remove((long)i));
        for (int i = -400; i < 400; i++) assertEquals((i & 1) != 0 ? i * 3L : -7, map.get((long)i));
        for (int i = 2; i < 17; i++) {
            long[] limbs = new long[17]; limbs[i] = 1;
            assertEquals(i, map.get(Int1088.of(limbs)));
        }
        var cursor = map.cursor(); assertTrue(cursor.advance()); map.put(2000L, 2);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        map.clear(); assertTrue(map.isEmpty());
    }
}
