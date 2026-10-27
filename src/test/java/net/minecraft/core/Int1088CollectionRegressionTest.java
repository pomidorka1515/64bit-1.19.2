package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Behavioral replacements for the removed pair-key collection tests. */
class Int1088CollectionRegressionTest {
    private static Int1088[] keys(Random random) {
        Int1088[] keys = new Int1088[512];
        for (int i = 0; i < keys.length; ++i) {
            long[] limbs = new long[Int1088.LIMBS];
            for (int j = 0; j < limbs.length; ++j) limbs[j] = random.nextLong();
            keys[i] = i < 128 ? Int1088.fromLong(i - 64) : Int1088.of(limbs);
        }
        keys[128] = Int1088.MIN;
        keys[129] = Int1088.MAX;
        return keys;
    }

    private static BigInteger big(Int1088 key) {
        return new BigInteger(key.toDecimalString());
    }

    @Test
    void primitiveMapsAndSetMatchIndependentModelsThroughGrowthAndDeletion() {
        Random random = new Random(0x1088c011ec7L);
        Int1088[] keys = keys(random);
        Int10882LongMap longs = new Int10882LongMap(0);
        Int10882ByteMap bytes = new Int10882ByteMap(0);
        Int1088Set set = new Int1088Set(0);
        longs.defaultReturnValue(-7L);
        bytes.defaultReturnValue((byte) -7);
        Map<BigInteger, Long> longModel = new HashMap<>();
        Map<BigInteger, Byte> byteModel = new HashMap<>();
        HashSet<BigInteger> setModel = new HashSet<>();
        BigInteger[] oracleKeys = new BigInteger[keys.length];
        for (int i = 0; i < keys.length; ++i) oracleKeys[i] = big(keys[i]);
        for (int i = 0; i < 100000; ++i) {
            int index = i < keys.length ? i : random.nextInt(keys.length);
            Int1088 key = keys[index];
            BigInteger oracle = oracleKeys[index];
            int operation = i < keys.length ? 0 : random.nextInt(3);
            if (operation == 0) {
                long value = random.nextLong();
                byte byteValue = (byte) value;
                Long previousLong = longModel.put(oracle, value);
                Byte previousByte = byteModel.put(oracle, byteValue);
                assertEquals(previousLong == null ? -7L : previousLong.longValue(), longs.put(key, value));
                assertEquals(previousByte == null ? (byte) -7 : previousByte.byteValue(), bytes.put(key, byteValue));
                assertEquals(setModel.add(oracle), set.add(key));
            } else if (operation == 1) {
                Long previousLong = longModel.remove(oracle);
                Byte previousByte = byteModel.remove(oracle);
                assertEquals(previousLong == null ? -7L : previousLong.longValue(), longs.remove(key));
                assertEquals(previousByte == null ? (byte) -7 : previousByte.byteValue(), bytes.remove(key));
                assertEquals(setModel.remove(oracle), set.remove(key));
            } else {
                assertEquals(longModel.getOrDefault(oracle, -7L).longValue(), longs.get(key));
                assertEquals(byteModel.getOrDefault(oracle, (byte) -7).byteValue(), bytes.get(key));
                assertEquals(setModel.contains(oracle), set.contains(key));
                assertEquals(longModel.containsKey(oracle), longs.containsKey(key));
                assertEquals(byteModel.containsKey(oracle), bytes.containsKey(key));
                if (key.fitsLong()) {
                    assertEquals(longs.get(key), longs.get(key.lowLimb()));
                    assertEquals(bytes.get(key), bytes.get(key.lowLimb()));
                    assertEquals(set.contains(key), set.contains(key.lowLimb()));
                }
            }
            assertEquals(longModel.size(), longs.size());
            assertEquals(byteModel.size(), bytes.size());
            assertEquals(setModel.size(), set.size());
        }
        var lc = longs.cursor();
        int count = 0;
        while (lc.advance()) { assertEquals(longModel.get(big(lc.key())).longValue(), lc.value()); ++count; }
        assertEquals(longModel.size(), count);
        var bc = bytes.cursor();
        count = 0;
        while (bc.advance()) { assertEquals(byteModel.get(big(bc.key())).byteValue(), bc.value()); ++count; }
        assertEquals(byteModel.size(), count);
        longs.clear(); bytes.clear(); set.clear();
        assertTrue(longs.isEmpty()); assertTrue(bytes.isEmpty()); assertTrue(set.isEmpty());
    }

    @Test
    void linkedMapRetainsNullValuesAndEndpointOrderUnderRandomOperations() {
        Random random = new Random(0x108810cL);
        Int1088[] keys = keys(random);
        Int1088LinkedMap<Integer> actual = new Int1088LinkedMap<>(0);
        LinkedHashMap<Int1088, Integer> expected = new LinkedHashMap<>();
        ArrayList<Int1088> order = new ArrayList<>();
        for (int i = 0; i < 100000; ++i) {
            Int1088 key = keys[random.nextInt(keys.length)];
            Integer value = i % 7 == 0 ? null : random.nextInt();
            switch (random.nextInt(7)) {
                case 0 -> {
                    if (!expected.containsKey(key)) order.add(key);
                    assertEquals(expected.put(key, value), actual.put(key, value));
                }
                case 1 -> {
                    order.remove(key); order.add(0, key);
                    assertEquals(expected.put(key, value), actual.putAndMoveToFirst(key, value));
                }
                case 2 -> {
                    order.remove(key);
                    assertEquals(expected.remove(key), actual.remove(key));
                }
                case 3 -> {
                    if (expected.containsKey(key)) { order.remove(key); order.add(0, key); }
                    assertEquals(expected.get(key), actual.getAndMoveToFirst(key));
                }
                case 4 -> {
                    if (!order.isEmpty()) assertEquals(expected.remove(order.remove(0)), actual.removeFirst());
                }
                case 5 -> {
                    if (!order.isEmpty()) assertEquals(expected.remove(order.remove(order.size() - 1)), actual.removeLast());
                }
                default -> {
                    assertEquals(expected.containsKey(key), actual.containsKey(key));
                    assertEquals(expected.get(key), actual.get(key));
                }
            }
            assertEquals(expected.size(), actual.size());
            if (i % 100 == 0) {
                var cursor = actual.cursor();
                for (Int1088 ordered : order) {
                    assertTrue(cursor.advance());
                    assertEquals(ordered, cursor.key());
                    assertEquals(expected.get(ordered), cursor.value());
                }
                assertFalse(cursor.advance());
            }
        }
    }

    private record Group(BigInteger x, int y, BigInteger z) {}

    @Test
    void spatialGroupOrderingAndBitRemovalMatchIndependentModel() {
        Random random = new Random(0x10885a7L);
        Int1088[] keys = keys(random);
        LinkedHashMap<Group, Long> model = new LinkedHashMap<>();
        Int1088SpatialSet actual = new Int1088SpatialSet(0);
        Int1088SpatialSet.Position out = new Int1088SpatialSet.Position();
        for (int i = 0; i < 100000; ++i) {
            Int1088 x = keys[i < 512 ? i : random.nextInt(keys.length)];
            Int1088 z = keys[random.nextInt(keys.length)];
            int y = random.nextInt(32) - 16;
            BigInteger xx = big(x), zz = big(z);
            Group group = new Group(xx.and(BigInteger.valueOf(-4)), y & ~3, zz.and(BigInteger.valueOf(-4)));
            long bit = 1L << ((xx.intValue() & 3) << 4 | (zz.intValue() & 3) << 2 | y & 3);
            int operation = i < 512 ? 0 : random.nextInt(3);
            long previous = model.getOrDefault(group, 0L);
            if (operation == 0) {
                assertEquals((previous & bit) != 0, actual.add(x, y, z));
                model.put(group, previous | bit);
            } else if (operation == 1) {
                assertEquals((previous & bit) != 0, actual.rem(x, y, z));
                if ((previous & ~bit) == 0) model.remove(group); else model.put(group, previous & ~bit);
            } else if (!model.isEmpty()) {
                removeFirst(model, actual, out);
            }
            assertEquals(model.isEmpty(), actual.isEmpty());
        }
        while (!model.isEmpty()) removeFirst(model, actual, out);
        assertTrue(actual.isEmpty());
        assertThrows(java.util.NoSuchElementException.class, () -> actual.removeFirstLong(out));
    }

    private static void removeFirst(LinkedHashMap<Group, Long> model, Int1088SpatialSet actual, Int1088SpatialSet.Position out) {
        var entry = model.entrySet().iterator().next();
        int index = Long.numberOfTrailingZeros(entry.getValue());
        Group group = entry.getKey();
        actual.removeFirstLong(out);
        assertEquals(group.x.or(BigInteger.valueOf(index >>> 4 & 3)), big(Int1088.fromLimbs(out.x, 0, out.xLength)));
        assertEquals(group.z.or(BigInteger.valueOf(index >>> 2 & 3)), big(Int1088.fromLimbs(out.z, 0, out.zLength)));
        assertEquals(group.y | index & 3, out.y);
        long remaining = entry.getValue() & ~(1L << index);
        if (remaining == 0) model.remove(group); else model.put(group, remaining);
    }
}
