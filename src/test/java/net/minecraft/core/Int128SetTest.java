package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Int128SetTest {
    @Test
    void randomOperationsMatchHashSet() {
        Random random = new Random(106);
        Int128Set actual = new Int128Set(0);
        Set<BigInteger> expected = new HashSet<>();
        long[] hi = new long[2048];
        long[] lo = new long[2048];
        hi[1] = Long.MIN_VALUE;
        hi[2] = Long.MAX_VALUE;
        lo[2] = -1;
        for (int i = 3; i < hi.length; ++i) {
            hi[i] = random.nextLong();
            lo[i] = random.nextLong();
        }
        for (int i = 0; i < 100000; ++i) {
            int k = random.nextInt(hi.length);
            BigInteger key = Int128MapTest.big(hi[k], lo[k]);
            switch (random.nextInt(3)) {
                case 0 -> assertEquals(expected.add(key), actual.add(hi[k], lo[k]));
                case 1 -> assertEquals(expected.remove(key), actual.remove(hi[k], lo[k]));
                default -> assertEquals(expected.contains(key), actual.contains(hi[k], lo[k]));
            }
            assertEquals(expected.size(), actual.size());
            if (i % 1000 == 0) {
                Set<BigInteger> seen = new HashSet<>();
                var cursor = actual.cursor();
                while (cursor.advance()) assertTrue(seen.add(Int128MapTest.big(cursor.hi(), cursor.lo())));
                assertEquals(expected, seen);
            }
        }
        actual.clear();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        for (int i = 0; i < 3; ++i) {
            assertTrue(actual.add(hi[i], lo[i]));
            assertFalse(actual.add(hi[i], lo[i]));
            assertTrue(actual.contains(hi[i], lo[i]));
            assertTrue(actual.remove(hi[i], lo[i]));
            assertFalse(actual.remove(hi[i], lo[i]));
        }
    }

    @Test
    void cursorContractsAndInvalidCapacity() {
        Int128Set set = new Int128Set();
        var cursor = set.cursor();
        assertThrows(NoSuchElementException.class, cursor::hi);
        set.add(0, 0);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        cursor = set.cursor();
        assertTrue(cursor.advance());
        assertEquals(0, cursor.hi());
        assertEquals(0, cursor.lo());
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::lo);
        assertThrows(IllegalArgumentException.class, () -> new Int128Set(-1));
    }
}
