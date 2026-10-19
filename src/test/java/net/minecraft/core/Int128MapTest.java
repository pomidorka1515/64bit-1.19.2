package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.HashCommon;
import java.math.BigInteger;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128MapTest {
    static BigInteger big(long hi, long lo) {
        return BigInteger.valueOf(hi).shiftLeft(64).add(new BigInteger(Long.toUnsignedString(lo)));
    }

    @Test
    void randomOperationsMatchHashMap() {
        Random random = new Random(105);
        Int128Map<Integer> actual = new Int128Map<>(1);
        Map<BigInteger, Integer> expected = new HashMap<>();
        long[] hi = new long[2048];
        long[] lo = new long[2048];
        for (int i = 1; i < hi.length; ++i) {
            hi[i] = random.nextLong();
            lo[i] = random.nextLong();
        }
        for (int i = 0; i < 100000; ++i) {
            int k = random.nextInt(hi.length);
            BigInteger key = big(hi[k], lo[k]);
            switch (random.nextInt(4)) {
                case 0 -> {
                    Integer value = random.nextBoolean() ? random.nextInt() : null;
                    assertEquals(expected.put(key, value), actual.put(hi[k], lo[k], value));
                }
                case 1 -> assertEquals(expected.remove(key), actual.remove(hi[k], lo[k]));
                case 2 -> assertEquals(expected.get(key), actual.get(hi[k], lo[k]));
                default -> assertEquals(expected.containsKey(key), actual.containsKey(hi[k], lo[k]));
            }
            assertEquals(expected.size(), actual.size());
            if (i % 1000 == 0) assertContents(expected, actual);
        }
        assertContents(expected, actual);
        actual.clear();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        actual.put(0, 0, 1);
        assertEquals(1, actual.get(0, 0));
    }

    private static void assertContents(Map<BigInteger, Integer> expected, Int128Map<Integer> actual) {
        Map<BigInteger, Integer> seen = new HashMap<>();
        var cursor = actual.cursor();
        while (cursor.advance()) {
            BigInteger key = big(cursor.hi(), cursor.lo());
            assertFalse(seen.containsKey(key));
            seen.put(key, cursor.value());
        }
        assertEquals(expected, seen);
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::hi);
    }

    @Test
    void wrappedCollisionChainsAndBoundaryKeys() {
        Int128Map<Integer> map = new Int128Map<>(16);
        long[] keys = new long[12];
        int count = 0;
        for (long k = 1; count < keys.length; ++k) {
            if (((int)Long.rotateLeft(HashCommon.mix(k), 32) & 31) == 31) keys[count++] = k;
        }
        for (int i = 0; i < keys.length; ++i) map.put(0, keys[i], i);
        for (int i = 0; i < keys.length; ++i) {
            assertEquals(i, map.remove(0, keys[i]));
            for (int j = i + 1; j < keys.length; ++j) assertEquals(j, map.get(0, keys[j]));
        }
        long[][] boundaries = {{0, 0}, {Long.MIN_VALUE, 0}, {Long.MAX_VALUE, -1}, {1, 0}, {0, 1}, {-1, -1}};
        for (int i = 0; i < boundaries.length; ++i) map.put(boundaries[i][0], boundaries[i][1], i);
        for (int i = 0; i < boundaries.length; ++i) assertEquals(i, map.get(boundaries[i][0], boundaries[i][1]));
        var cursor = map.cursor();
        assertThrows(NoSuchElementException.class, cursor::value);
        map.put(42, 42, 7);
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertThrows(IllegalArgumentException.class, () -> new Int128Map<>(-1));
    }
}
