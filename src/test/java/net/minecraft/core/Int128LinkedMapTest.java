package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.math.BigInteger;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128LinkedMapTest {
    @Test
    void randomOperationsMatchFastutil() {
        Random random = new Random(106);
        var actual = new Int128LinkedMap<Integer>(1);
        var expected = new Object2ObjectLinkedOpenHashMap<BigInteger, Integer>();
        long[] hi = new long[256], lo = new long[256];
        for (int k = 1; k < hi.length; ++k) {
            hi[k] = random.nextLong(); lo[k] = random.nextLong();
        }
        hi[1] = Long.MIN_VALUE; lo[1] = 0;
        hi[2] = Long.MAX_VALUE; lo[2] = -1;
        for (int i = 0; i < 100000; ++i) {
            int k = random.nextInt(hi.length);
            BigInteger key = Int128MapTest.big(hi[k], lo[k]);
            Integer value = random.nextBoolean() ? random.nextInt() : null;
            switch (random.nextInt(8)) {
                case 0 -> assertEquals(expected.put(key, value), actual.put(hi[k], lo[k], value));
                case 1 -> assertEquals(expected.putAndMoveToFirst(key, value), actual.putAndMoveToFirst(hi[k], lo[k], value));
                case 2 -> assertEquals(expected.getAndMoveToFirst(key), actual.getAndMoveToFirst(hi[k], lo[k]));
                case 3 -> assertEquals(expected.remove(key), actual.remove(hi[k], lo[k]));
                case 4 -> { if (!expected.isEmpty()) assertEquals(expected.removeFirst(), actual.removeFirst()); }
                case 5 -> { if (!expected.isEmpty()) assertEquals(expected.removeLast(), actual.removeLast()); }
                case 6 -> assertEquals(expected.get(key), actual.get(hi[k], lo[k]));
                default -> assertEquals(expected.containsKey(key), actual.containsKey(hi[k], lo[k]));
            }
            assertEquals(expected.size(), actual.size());
            if (expected.isEmpty()) assertFalse(actual.cursor().advance());
            else assertOrder(expected, actual);
        }
        assertOrder(expected, actual);
        actual.clear();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        assertThrows(NoSuchElementException.class, actual::removeFirst);
        assertThrows(NoSuchElementException.class, actual::removeLast);
        actual.put(0, 0, 42);
        assertEquals(42, actual.removeLast());
    }

    private static void assertOrder(Object2ObjectLinkedOpenHashMap<BigInteger, Integer> expected,
                                    Int128LinkedMap<Integer> actual) {
        var cursor = actual.cursor();
        // This fastutil version can retain a stale iterator head after singleton removeLast.
        if (expected.isEmpty()) {
            assertFalse(cursor.advance());
            return;
        }
        for (var entry : expected.object2ObjectEntrySet()) {
            assertTrue(cursor.advance());
            assertEquals(entry.getKey(), Int128MapTest.big(cursor.hi(), cursor.lo()));
            assertEquals(entry.getValue(), cursor.value());
        }
        assertFalse(cursor.advance());
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::hi);
    }

    @Test
    void wrappedChainsGrowthEvictionAndReinsertion() {
        var actual = new Int128LinkedMap<Integer>(16);
        var expected = new Object2ObjectLinkedOpenHashMap<BigInteger, Integer>();
        long[] keys = new long[12];
        for (long k = 1, count = 0; count < keys.length; ++k) {
            if (((int)Long.rotateLeft(HashCommon.mix(k), 32) & 31) == 31) keys[(int)count++] = k;
        }
        for (long k : keys) { actual.put(0, k, (int)k); expected.put(BigInteger.valueOf(k), (int)k); }
        for (long k : keys) {
            actual.getAndMoveToFirst(0, k); expected.getAndMoveToFirst(BigInteger.valueOf(k));
            assertOrder(expected, actual);
            actual.remove(0, k); expected.remove(BigInteger.valueOf(k));
            assertOrder(expected, actual);
            actual.put(0, k, (int)k); expected.put(BigInteger.valueOf(k), (int)k);
        }
        actual.putAndMoveToFirst(0, 0, null); expected.putAndMoveToFirst(BigInteger.ZERO, null);
        for (int k = 1; k <= 1000; ++k) {
            actual.putAndMoveToFirst(1, k, k);
            expected.putAndMoveToFirst(Int128MapTest.big(1, k), k);
        }
        assertOrder(expected, actual);
        while (!expected.isEmpty()) assertEquals(expected.removeLast(), actual.removeLast());
        assertFalse(actual.cursor().advance());
        assertThrows(IllegalArgumentException.class, () -> new Int128LinkedMap<>(-1));
    }

    @Test
    void shiftedEntryRemainsPredecessorOfItsSuccessor() {
        var map = new Int128LinkedMap<Integer>(16);
        long[] keys = new long[3];
        int count = 0;
        for (long k = 1; count < keys.length; ++k) {
            if (((int)Long.rotateLeft(HashCommon.mix(k), 32) & 31) == 31) keys[count++] = k;
        }
        for (int i = 0; i < keys.length; ++i) map.put(0, keys[i], i);
        // Both survivors relocate across the wrapped probe chain. The last one's prev
        // must name the middle entry's new slot, not the deleted entry's predecessor.
        assertEquals(0, map.remove(0, keys[0]));
        assertEquals(2, map.getAndMoveToFirst(0, keys[2]));
        var cursor = map.cursor();
        assertTrue(cursor.advance());
        assertEquals(keys[2], cursor.lo());
        assertTrue(cursor.advance());
        assertEquals(keys[1], cursor.lo());
        assertFalse(cursor.advance());
        assertEquals(1, map.removeLast());
        assertEquals(2, map.removeFirst());
        assertTrue(map.isEmpty());
    }

    @Test
    void cursorContracts() {
        var map = new Int128LinkedMap<Integer>();
        map.put(0, 0, 1); map.put(1, 0, 2);
        var cursor = map.cursor();
        assertThrows(NoSuchElementException.class, cursor::value);
        map.put(0, 0, 3);
        map.getAndMoveToFirst(0, 0);
        assertTrue(cursor.advance());
        assertEquals(3, cursor.value());
        map.getAndMoveToFirst(1, 0);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertThrows(ConcurrentModificationException.class, cursor::lo);
    }
}
