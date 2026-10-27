package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class Int1088LinkedMapTest {
    @Test void orderingNullsAndSet() {
        Int1088LinkedMap<Integer> map = new Int1088LinkedMap<>(0);
        for (int i = 0; i < 200; i++) map.put((long)i, i);
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        map.put(wide, null);
        assertTrue(map.containsKey(wide));
        assertEquals(100, map.getAndMoveToFirst(100L));
        var cursor = map.cursor(); assertTrue(cursor.advance()); assertEquals(Int1088.fromLong(100), cursor.key());
        map.putAndMoveToFirst(wide, 200);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertEquals(200, map.removeFirst());
        assertEquals(199, map.removeLast());
        for (int i = 0; i < 199; i++) assertEquals(i, map.remove((long)i));
        assertThrows(NoSuchElementException.class, map::removeFirst);
        Int1088LinkedSet set = new Int1088LinkedSet();
        assertTrue(set.add(wide)); assertTrue(set.add(1L));
        assertFalse(set.addAndMoveToFirst(wide));
        var sc = set.cursor(); assertTrue(sc.advance()); assertEquals(wide, sc.key());
        set.removeFirst(); assertFalse(set.contains(wide)); assertTrue(set.contains(1L));
    }
}
