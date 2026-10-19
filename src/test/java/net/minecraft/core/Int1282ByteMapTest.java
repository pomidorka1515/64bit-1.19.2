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

class Int1282ByteMapTest {
    @Test
    void randomOperationsMatchHashMap() {
        Random random = new Random(107);
        Int1282ByteMap actual = new Int1282ByteMap(1);
        Map<BigInteger, Byte> expected = new HashMap<>();
        long[] hi = new long[2048];
        long[] lo = new long[2048];
        for (int i = 1; i < hi.length; ++i) {
            hi[i] = random.nextLong();
            lo[i] = random.nextLong();
        }
        hi[1] = Long.MIN_VALUE;
        lo[1] = 0;
        hi[2] = Long.MAX_VALUE;
        lo[2] = -1;
        for (int i = 0; i < 100000; ++i) {
            int k = random.nextInt(hi.length);
            BigInteger key = Int128MapTest.big(hi[k], lo[k]);
            byte fallback = actual.defaultReturnValue();
            switch (random.nextInt(5)) {
                case 0 -> {
                    byte value = (byte)random.nextLong();
                    Byte previous = expected.put(key, value);
                    assertEquals(previous == null ? fallback : previous.byteValue(), actual.put(hi[k], lo[k], value));
                }
                case 1 -> {
                    Byte previous = expected.remove(key);
                    assertEquals(previous == null ? fallback : previous.byteValue(), actual.remove(hi[k], lo[k]));
                }
                case 2 -> assertEquals(expected.getOrDefault(key, fallback).byteValue(), actual.get(hi[k], lo[k]));
                case 3 -> assertEquals(expected.containsKey(key), actual.containsKey(hi[k], lo[k]));
                default -> actual.defaultReturnValue((byte)random.nextLong());
            }
            assertEquals(expected.size(), actual.size());
            if (i % 1000 == 0) assertContents(expected, actual);
        }
        assertContents(expected, actual);
        byte fallback = actual.defaultReturnValue();
        actual.clear();
        assertTrue(actual.isEmpty());
        assertFalse(actual.cursor().advance());
        assertEquals(fallback, actual.get(0, 0));
        assertEquals(fallback, actual.put(0, 0, fallback));
        assertTrue(actual.containsKey(0, 0));
    }

    private static void assertContents(Map<BigInteger, Byte> expected, Int1282ByteMap actual) {
        Map<BigInteger, Byte> seen = new HashMap<>();
        var cursor = actual.cursor();
        while (cursor.advance()) {
            BigInteger key = Int128MapTest.big(cursor.hi(), cursor.lo());
            assertNull(seen.put(key, cursor.value()));
        }
        assertEquals(expected, seen);
        assertFalse(cursor.advance());
        assertThrows(NoSuchElementException.class, cursor::value);
    }

    @Test
    void defaultsBoundariesAndCursorContracts() {
        Int1282ByteMap map = new Int1282ByteMap(1);
        assertEquals(0, map.defaultReturnValue());
        map.defaultReturnValue((byte)-17);
        long[][] keys = {{0, 0}, {Long.MIN_VALUE, 0}, {Long.MAX_VALUE, -1}, {1, 0}, {0, 1}, {-1, -1}};
        for (long[] key : keys) {
            assertEquals(-17, map.get(key[0], key[1]));
            assertEquals(-17, map.remove(key[0], key[1]));
            assertEquals(-17, map.put(key[0], key[1], (byte)-17));
        }
        map.defaultReturnValue(Byte.MAX_VALUE);
        for (long[] key : keys) {
            assertTrue(map.containsKey(key[0], key[1]));
            assertEquals(-17, map.get(key[0], key[1]));
            assertEquals(-17, map.put(key[0], key[1], Byte.MIN_VALUE));
        }
        var cursor = map.cursor();
        assertThrows(NoSuchElementException.class, cursor::hi);
        assertTrue(cursor.advance());
        map.put(cursor.hi(), cursor.lo(), Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, cursor.value());
        map.remove(cursor.hi(), cursor.lo());
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertThrows(ConcurrentModificationException.class, cursor::value);
        cursor = map.cursor();
        map.clear();
        assertThrows(ConcurrentModificationException.class, cursor::advance);
        assertEquals(0, map.size());
        assertEquals(Byte.MAX_VALUE, map.get(0, 0));
        map.put(0, 0, Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, map.remove(0, 0));
        assertFalse(map.containsKey(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Int1282ByteMap(-1));
    }

    @Test
    void wrappedCollisionDeletionKeepsProbeChainsIntact() {
        Int1282ByteMap map = new Int1282ByteMap(16);
        long[] keys = new long[12];
        int count = 0;
        for (long k = 1; count < keys.length; ++k) {
            if (((int)Long.rotateLeft(HashCommon.mix(k), 32) & 31) == 31) keys[count++] = k;
        }
        for (int i = 0; i < keys.length; ++i) map.put(0, keys[i], (byte)i);
        for (int i = 0; i < keys.length; ++i) {
            assertEquals(i, map.remove(0, keys[i]));
            assertFalse(map.containsKey(0, keys[i]));
            for (int j = i + 1; j < keys.length; ++j) assertEquals(j, map.get(0, keys[j]));
        }
        assertTrue(map.isEmpty());
    }
}
