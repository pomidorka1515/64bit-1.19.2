package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.math.BigInteger;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088LinkedSetTest {
    @Test
    void randomOperationsMatchFastutil() {
        var actual = new Int1088LinkedSet(1);
        var expected = new ObjectLinkedOpenHashSet<BigInteger>();
        Random random = new Random(1062);
        Int1088[] keys = new Int1088[512];
        keys[0] = Int1088.ZERO;
        for (int k = 1; k < keys.length; ++k) {
            long[] limbs = new long[Int1088.LIMBS];
            for (int limb = 0; limb < limbs.length; ++limb) limbs[limb] = random.nextLong();
            keys[k] = Int1088.of(limbs);
        }
        keys[1] = Int1088.MIN;
        keys[2] = Int1088.MAX;
        // Force multiple growths, including the zero and extreme keys.
        for (Int1088 key : keys) {
            assertEquals(expected.add(big(key)), actual.add(key));
        }
        assertOrder(expected, actual);
        for (int i = 0; i < 100000; ++i) {
            Int1088 value = keys[random.nextInt(keys.length)];
            BigInteger key = big(value);
            switch (random.nextInt(7)) {
                case 0 -> assertEquals(expected.add(key), actual.add(value));
                case 1 -> assertEquals(expected.addAndMoveToFirst(key), actual.addAndMoveToFirst(value));
                case 2 -> assertEquals(expected.remove(key), actual.remove(value));
                case 3 -> assertEquals(expected.contains(key), actual.contains(value));
                case 4 -> {
                    boolean present = expected.contains(key);
                    if (present) expected.addAndMoveToFirst(key);
                    assertEquals(present, actual.containsAndMoveToFirst(value));
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
        assertTrue(actual.add(0L));
        actual.removeLast();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        assertTrue(actual.add(0L));
        actual.removeFirst();
        assertFalse(actual.cursor().advance());
    }

    private static BigInteger big(Int1088 value) {
        return new BigInteger(value.toDecimalString());
    }

    private static void assertOrder(ObjectLinkedOpenHashSet<BigInteger> expected, Int1088LinkedSet actual) {
        var cursor = actual.cursor();
        // Older fastutil can retain a stale iterator head after singleton endpoint removal.
        if (!expected.isEmpty()) {
            for (BigInteger key : expected) {
                assertTrue(cursor.advance());
                assertEquals(key, big(cursor.key()));
            }
        }
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::key);
    }

    @Test
    void evictionReinsertionAndCursorContracts() {
        var set = new Int1088LinkedSet();
        assertTrue(set.add(0L));
        assertTrue(set.add(Int1088.MIN));
        assertTrue(set.add(Int1088.MAX));
        assertFalse(set.addAndMoveToFirst(Int1088.MAX));
        set.removeLast();
        assertFalse(set.contains(Int1088.MIN));
        assertTrue(set.add(Int1088.MIN));
        var cursor = set.cursor();
        assertThrows(NoSuchElementException.class, cursor::key);
        assertTrue(cursor.advance());
        assertEquals(Int1088.MAX, cursor.key());
        assertTrue(cursor.advance());
        assertEquals(Int1088.ZERO, cursor.key());
        assertTrue(cursor.advance());
        assertEquals(Int1088.MIN, cursor.key());
        assertFalse(cursor.advance());
        set.containsAndMoveToFirst(0L);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertThrows(IllegalArgumentException.class, () -> new Int1088LinkedSet(-1));
    }
}
