package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088MapTest {
    @Test
    void fullWidthIdentityGrowthDeletionAndNarrowProbes() {
        Int1088Map<Integer> map = new Int1088Map<>(1);
        for (int i = -200; i < 200; i++) map.put((long)i, i);
        for (int i = 2; i < 17; i++) {
            long[] words = new long[17];
            words[i] = 1;
            map.put(Int1088.of(words), i);
        }
        for (int i = -200; i < 200; i++) assertEquals(i, map.get((long)i));
        for (int i = -200; i < 200; i += 2) assertEquals(i, map.remove((long)i));
        for (int i = 2; i < 17; i++) {
            long[] words = new long[17];
            words[i] = 1;
            assertEquals(i, map.get(Int1088.of(words)));
        }
        assertFalse(map.containsKey(0L));
    }

    @Test
    void nullValuesAndFailFastCursor() {
        Int1088Map<String> map = new Int1088Map<>();
        map.put(0L, null);
        assertTrue(map.containsKey(0L));
        assertNull(map.get(0L));
        var cursor = map.cursor();
        assertTrue(cursor.advance());
        long[] words = new long[17];
        cursor.copyKey(words, 0);
        assertEquals(Int1088.ZERO, Int1088.of(words));
        map.put(1L, "one");
        assertThrows(ConcurrentModificationException.class, cursor::advance);
    }

    @Test
    void seededMixedOperationsAgainstHashMap() {
        long seed = 0x1088c011L;
        Random random = new Random(seed);
        Int1088[] keys = new Int1088[512];
        for (int i = 0; i < keys.length; i++) {
            if (i < 256) keys[i] = Int1088.fromLong(i - 128);
            else {
                long[] limbs = new long[17];
                limbs[0] = i % 4;
                limbs[1 + random.nextInt(16)] = random.nextLong();
                keys[i] = Int1088.of(limbs);
            }
        }
        Int1088Map<Integer> actual = new Int1088Map<>(0);
        HashMap<Int1088, Integer> expected = new HashMap<>();
        for (int i = 0; i < 100000; i++) {
            Int1088 key = keys[random.nextInt(keys.length)];
            switch (random.nextInt(3)) {
                case 0 -> {
                    Integer value = random.nextInt(5) == 0 ? null : random.nextInt();
                    assertEquals(expected.put(key, value), actual.put(key, value), "seed=" + seed);
                }
                case 1 -> assertEquals(expected.remove(key), actual.remove(key), "seed=" + seed);
                default -> {
                    assertEquals(expected.containsKey(key), actual.containsKey(key), "seed=" + seed);
                    assertEquals(expected.get(key), actual.get(key), "seed=" + seed);
                    if (key.fitsLong()) {
                        assertEquals(expected.containsKey(key), actual.containsKey(key.lowLimb()), "seed=" + seed);
                        assertEquals(expected.get(key), actual.get(key.lowLimb()), "seed=" + seed);
                    }
                }
            }
            assertEquals(expected.size(), actual.size(), "seed=" + seed);
        }
        var cursor = actual.cursor();
        int count = 0;
        while (cursor.advance()) {
            assertTrue(expected.containsKey(cursor.key()));
            assertEquals(expected.get(cursor.key()), cursor.value());
            count++;
        }
        assertEquals(expected.size(), count);
    }
}
