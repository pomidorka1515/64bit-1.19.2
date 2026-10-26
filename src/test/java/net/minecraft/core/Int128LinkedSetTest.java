package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.math.BigInteger;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128LinkedSetTest {
    @Test
    void randomOperationsMatchFastutil() {
        var actual = new Int128LinkedSet(1);
        var expected = new ObjectLinkedOpenHashSet<BigInteger>();
        Random random = new Random(1062);
        long[] hi = new long[512], lo = new long[512];
        for (int k = 1; k < hi.length; ++k) {
            hi[k] = random.nextLong(); lo[k] = random.nextLong();
        }
        hi[1] = Long.MIN_VALUE; lo[1] = 0;
        hi[2] = Long.MAX_VALUE; lo[2] = -1;
        // Force multiple growths, including the zero and extreme keys.
        for (int k = 0; k < hi.length; ++k) {
            assertEquals(expected.add(Int128MapTest.big(hi[k], lo[k])), actual.add(hi[k], lo[k]));
        }
        assertOrder(expected, actual);
        for (int i = 0; i < 100000; ++i) {
            int k = random.nextInt(hi.length);
            BigInteger key = Int128MapTest.big(hi[k], lo[k]);
            switch (random.nextInt(7)) {
                case 0 -> assertEquals(expected.add(key), actual.add(hi[k], lo[k]));
                case 1 -> assertEquals(expected.addAndMoveToFirst(key), actual.addAndMoveToFirst(hi[k], lo[k]));
                case 2 -> assertEquals(expected.remove(key), actual.remove(hi[k], lo[k]));
                case 3 -> assertEquals(expected.contains(key), actual.contains(hi[k], lo[k]));
                case 4 -> {
                    boolean present = expected.contains(key);
                    if (present) expected.addAndMoveToFirst(key);
                    assertEquals(present, actual.containsAndMoveToFirst(hi[k], lo[k]));
                }
                case 5 -> { if (!expected.isEmpty()) { expected.removeFirst(); actual.removeFirst(); } }
                default -> { if (!expected.isEmpty()) { expected.removeLast(); actual.removeLast(); } }
            }
            assertEquals(expected.size(), actual.size());
            if (i % 100 == 0) assertOrder(expected, actual);
        }
        assertOrder(expected, actual);
        actual.clear();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        assertThrows(NoSuchElementException.class, actual::removeFirst);
        assertThrows(NoSuchElementException.class, actual::removeLast);
        assertTrue(actual.add(0, 0));
        actual.removeLast();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        assertTrue(actual.add(0, 0));
        actual.removeFirst();
        assertFalse(actual.cursor().advance());
    }

    private static void assertOrder(ObjectLinkedOpenHashSet<BigInteger> expected, Int128LinkedSet actual) {
        var cursor = actual.cursor();
        // Older fastutil can retain a stale iterator head after singleton endpoint removal.
        if (!expected.isEmpty()) {
            for (BigInteger key : expected) {
                assertTrue(cursor.advance());
                assertEquals(key, Int128MapTest.big(cursor.hi(), cursor.lo()));
            }
        }
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::lo);
    }

    @Test
    void evictionReinsertionAndCursorContracts() {
        var set = new Int128LinkedSet();
        assertTrue(set.add(0, 0));
        assertTrue(set.add(Long.MIN_VALUE, 0));
        assertTrue(set.add(Long.MAX_VALUE, -1));
        assertFalse(set.addAndMoveToFirst(Long.MAX_VALUE, -1));
        set.removeLast();
        assertFalse(set.contains(Long.MIN_VALUE, 0));
        assertTrue(set.add(Long.MIN_VALUE, 0));
        var cursor = set.cursor();
        assertThrows(NoSuchElementException.class, cursor::hi);
        assertTrue(cursor.advance());
        assertEquals(Long.MAX_VALUE, cursor.hi());
        assertTrue(cursor.advance());
        assertEquals(0, cursor.hi());
        assertTrue(cursor.advance());
        assertEquals(Long.MIN_VALUE, cursor.hi());
        assertFalse(cursor.advance());
        set.containsAndMoveToFirst(0, 0);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertThrows(IllegalArgumentException.class, () -> new Int128LinkedSet(-1));
    }
}
