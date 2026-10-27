package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class Int10882ByteMapTest {
    @Test void primitiveValuesAndWideIdentity() {
        Int10882ByteMap map = new Int10882ByteMap(0);
        map.defaultReturnValue((byte)-7);
        for (int i = -400; i < 400; i++) assertEquals((byte)-7, map.put((long)i, (byte)i));
        for (int i = 2; i < 17; i++) {
            long[] limbs = new long[17]; limbs[i] = 1;
            assertEquals((byte)-7, map.put(Int1088.of(limbs), (byte)i));
        }
        for (int i = -400; i < 400; i += 2) assertEquals((byte)i, map.remove((long)i));
        for (int i = -400; i < 400; i++) assertEquals((i & 1) != 0 ? (byte)i : (byte)-7, map.get((long)i));
        for (int i = 2; i < 17; i++) {
            long[] limbs = new long[17]; limbs[i] = 1;
            assertEquals((byte)i, map.get(Int1088.of(limbs)));
        }
        var cursor = map.cursor(); assertTrue(cursor.advance()); map.put(2000L, (byte)2);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        map.clear(); assertTrue(map.isEmpty());
    }
}
