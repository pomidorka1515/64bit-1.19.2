package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128SpatialSetTest {
    @Test
    void randomOperationsMatchLocalPackedBitsetModel() {
        Random random = new Random(108);
        LocalBitsetModel expected = new LocalBitsetModel();
        Int128SpatialSet actual = new Int128SpatialSet(16);
        var out = new Int128SpatialSet.Position();
        for (int i = 0; i < 100000; ++i) {
            int x = random.nextInt(64) - 32, y = random.nextInt(32) - 16, z = random.nextInt(64) - 32;
            // Test-only bounded packing: 25 X/Z bits and 14 Y bits.
            long packed = ((long)x & 0x1ffffffL) << 39 | ((long)z & 0x1ffffffL) << 14 | y & 0x3fffL;
            switch (random.nextInt(4)) {
                case 0, 1 -> assertEquals(expected.add(packed), actual.add(x >> 31, x, y, z >> 31, z));
                case 2 -> assertEquals(expected.rem(packed), actual.rem(x >> 31, x, y, z >> 31, z));
                default -> {
                    if (!expected.isEmpty()) assertNext(expected, actual, out);
                }
            }
            assertEquals(expected.isEmpty(), actual.isEmpty());
        }
        while (!expected.isEmpty()) assertNext(expected, actual, out);
        assertTrue(actual.isEmpty());
        assertThrows(NoSuchElementException.class, () -> actual.removeFirstLong(out));
    }

    private static void assertNext(LocalBitsetModel expected, Int128SpatialSet actual, Int128SpatialSet.Position out) {
        long packed = expected.removeFirstLong();
        actual.removeFirstLong(out);
        long x = packed >> 39, z = packed << 25 >> 39;
        assertEquals(x, out.xLo);
        assertEquals(x >> 63, out.xHi);
        assertEquals((int)(packed << 50 >> 50), out.y);
        assertEquals(z, out.zLo);
        assertEquals(z >> 63, out.zHi);
    }

    private static final class LocalBitsetModel {
        private static final long LOCAL_MASK = 3L << 39 | 3L << 14 | 3L;
        private final LinkedHashMap<Long, Long> groups = new LinkedHashMap<>();

        boolean add(long packed) {
            long key = packed & ~LOCAL_MASK;
            long bit = 1L << index(packed), old = groups.getOrDefault(key, 0L);
            groups.put(key, old | bit);
            return (old & bit) != 0L;
        }

        boolean rem(long packed) {
            long key = packed & ~LOCAL_MASK;
            long bit = 1L << index(packed), old = groups.getOrDefault(key, 0L);
            if ((old & bit) == 0L) return false;
            if (old == bit) groups.remove(key);
            else groups.put(key, old & ~bit);
            return true;
        }

        boolean isEmpty() { return groups.isEmpty(); }

        long removeFirstLong() {
            var entry = groups.entrySet().iterator().next();
            long key = entry.getKey(), bits = entry.getValue();
            int index = Long.numberOfTrailingZeros(bits);
            if ((bits & (bits - 1L)) == 0L) groups.remove(key);
            else entry.setValue(bits & (bits - 1L));
            return key | (long) (index >>> 4) << 39 | (long) (index >>> 2 & 3) << 14 | index & 3;
        }

        private static int index(long packed) {
            return (int) ((packed >>> 39 & 3) << 4 | (packed >>> 14 & 3) << 2 | packed & 3);
        }
    }

    private record Group(long xh, long xl, int y, long zh, long zl) {}

    @Test
    void fullWidthCoordinatesMatchOrderedBitsetModel() {
        Random random = new Random(128108);
        Group[] keys = new Group[512];
        keys[0] = new Group(0, 0, 0, 0, 0);
        keys[1] = new Group(Long.MIN_VALUE, 0, Integer.MIN_VALUE, Long.MAX_VALUE, -4);
        keys[2] = new Group(Long.MAX_VALUE, -4, Integer.MAX_VALUE & ~3, Long.MIN_VALUE, 0);
        for (int i = 3; i < keys.length; ++i) {
            keys[i] = new Group(random.nextLong(), random.nextLong() & ~3L,
                random.nextInt() & ~3, random.nextLong(), random.nextLong() & ~3L);
        }
        var model = new LinkedHashMap<Group, Long>();
        var actual = new Int128SpatialSet(0);
        var out = new Int128SpatialSet.Position();
        for (int i = 0; i < 100000; ++i) {
            Group k = keys[random.nextInt(keys.length)];
            int index = random.nextInt(64);
            long mask = 1L << index, old = model.getOrDefault(k, 0L);
            long xl = k.xl | (index >>> 4), zl = k.zl | (index >>> 2 & 3);
            int y = k.y | (index & 3);
            switch (random.nextInt(4)) {
                case 0, 1 -> {
                    assertEquals((old & mask) != 0, actual.add(k.xh, xl, y, k.zh, zl));
                    model.put(k, old | mask);
                }
                case 2 -> {
                    assertEquals((old & mask) != 0, actual.rem(k.xh, xl, y, k.zh, zl));
                    if ((old & ~mask) == 0) model.remove(k); else model.put(k, old & ~mask);
                }
                default -> { if (!model.isEmpty()) removeModel(model, actual, out); }
            }
            assertEquals(model.isEmpty(), actual.isEmpty());
        }
        while (!model.isEmpty()) removeModel(model, actual, out);
        // Reinsert after the shrink and fully drain every bit, including bit 63.
        for (int bit = 63; bit >= 0; --bit) actual.add(0, bit >>> 4, bit & 3, 0, bit >>> 2 & 3);
        for (int bit = 0; bit < 64; ++bit) {
            actual.removeFirstLong(out);
            assertEquals(bit >>> 4, out.xLo);
            assertEquals(bit & 3, out.y);
            assertEquals(bit >>> 2 & 3, out.zLo);
        }
        assertTrue(actual.isEmpty());
    }

    private static void removeModel(LinkedHashMap<Group, Long> model, Int128SpatialSet actual,
                                    Int128SpatialSet.Position out) {
        var entry = model.entrySet().iterator().next();
        Group k = entry.getKey();
        int bit = Long.numberOfTrailingZeros(entry.getValue());
        actual.removeFirstLong(out);
        assertEquals(k.xh, out.xHi); assertEquals(k.xl | bit >>> 4, out.xLo);
        assertEquals(k.zh, out.zHi); assertEquals(k.zl | (bit >>> 2 & 3), out.zLo);
        assertEquals(k.y | (bit & 3), out.y);
        long rest = entry.getValue() & ~(1L << bit);
        if (rest == 0) model.remove(k); else entry.setValue(rest);
    }
}
