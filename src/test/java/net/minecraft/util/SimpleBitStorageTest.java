package net.minecraft.util;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleBitStorageTest {
    @Test
    void writesMatchIntegerAndRawOraclesForEveryWidth() {
        Random random = new Random(0x51B17L);
        for (int bits = 1; bits <= 32; ++bits) {
            int size = 2 * (64 / bits) + 3;
            int[] values = new int[size];
            SimpleBitStorage storage = new SimpleBitStorage(bits, size);
            for (int step = 0; step < size + 1000; ++step) {
                int index = step < size ? step : random.nextInt(size);
                int value = step % 3 == 0 ? 0 : step % 3 == 1
                        ? writableMask(bits) : random.nextInt() & writableMask(bits);
                if ((step & 1) == 0) {
                    assertEquals(values[index], storage.getAndSet(index, value));
                } else {
                    storage.set(index, value);
                }
                values[index] = value;
                assertEquals(value, storage.get(index));
                assertArrayEquals(pack(bits, values), storage.getRaw(), "width " + bits);
            }
            assertContents(storage, values);
        }
    }

    @Test
    void constructorsAndBulkReadsMatchIndependentLayout() {
        Random random = new Random(0xB017L);
        for (int bits = 1; bits <= 32; ++bits) {
            int perWord = 64 / bits;
            for (int size : new int[] {0, 1, perWord - 1, perWord, perWord + 1, 4096}) {
                int[] values = new int[size];
                long mask = (1L << bits) - 1;
                for (int i = 0; i < size; ++i) {
                    // Include signed int bit patterns at width 32, independent of write validation.
                    values[i] = (int) (random.nextLong() & mask);
                }
                long[] raw = pack(bits, values);
                SimpleBitStorage fromValues = new SimpleBitStorage(bits, size, values);
                SimpleBitStorage fromRaw = new SimpleBitStorage(bits, size, raw.clone());
                assertArrayEquals(raw, fromValues.getRaw());
                assertContents(fromValues, values);
                assertContents(fromRaw, values);
                assertEquals(bits, fromRaw.getBits());
                assertEquals(size, fromRaw.getSize());
            }
        }
    }

    @Test
    void arrayConstructorMasksValuesWithoutChangingInput() {
        int[] values = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 42};
        int[] original = values.clone();
        for (int bits = 1; bits <= 32; ++bits) {
            SimpleBitStorage storage = new SimpleBitStorage(bits, values.length, values);
            assertArrayEquals(pack(bits, values), storage.getRaw());
            long mask = (1L << bits) - 1;
            int[] expected = Arrays.stream(values).map(value -> (int) (value & mask)).toArray();
            assertContents(storage, expected);
        }
        assertArrayEquals(original, values);
    }

    @Test
    void writesPreserveNeighborsAndUnusedRawBits() {
        for (int bits = 1; bits <= 32; ++bits) {
            int perWord = 64 / bits;
            int size = perWord + 1;
            long[] raw = {-1L, -1L};
            SimpleBitStorage storage = new SimpleBitStorage(bits, size, raw);
            for (int index : new int[] {0, perWord - 1, perWord}) {
                int word = index / perWord;
                int shift = (index % perWord) * bits;
                long[] expected = raw.clone();
                expected[word] &= ~(((1L << bits) - 1) << shift);
                storage.set(index, 0);
                assertArrayEquals(expected, raw);
            }
        }
    }

    @Test
    void validatesWidthsRawLengthIndicesAndValuesWithoutMutation() {
        for (int bits : new int[] {-1, 0, 33, 64}) {
            assertThrows(IllegalArgumentException.class, () -> new SimpleBitStorage(bits, 1));
        }
        for (int bits = 1; bits <= 32; ++bits) {
            int width = bits;
            int size = 64 / bits + 1;
            assertThrows(SimpleBitStorage.InitializationException.class,
                    () -> new SimpleBitStorage(width, size, new long[1]));
            assertThrows(SimpleBitStorage.InitializationException.class,
                    () -> new SimpleBitStorage(width, size, new long[3]));
            SimpleBitStorage storage = new SimpleBitStorage(bits, size);
            storage.set(0, writableMask(bits));
            long[] before = storage.getRaw().clone();
            for (int index : new int[] {-1, size, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> storage.get(index));
                assertThrows(IllegalArgumentException.class, () -> storage.set(index, 0));
                assertThrows(IllegalArgumentException.class, () -> storage.getAndSet(index, 0));
            }
            int tooLarge = bits < 31 ? 1 << bits : Integer.MIN_VALUE;
            for (int value : new int[] {-1, tooLarge}) {
                assertThrows(IllegalArgumentException.class, () -> storage.set(0, value));
                assertThrows(IllegalArgumentException.class, () -> storage.getAndSet(0, value));
            }
            assertArrayEquals(before, storage.getRaw());
            SimpleBitStorage empty = new SimpleBitStorage(bits, 0);
            assertThrows(IllegalArgumentException.class, () -> empty.get(0));
            assertThrows(IllegalArgumentException.class, () -> empty.set(0, 0));
            assertThrows(IllegalArgumentException.class, () -> empty.getAndSet(0, 0));
        }
    }

    @Test
    void copyIsIndependentWhileRawConstructorRetainsItsArray() {
        for (int bits = 1; bits <= 32; ++bits) {
            long[] raw = pack(bits, new int[] {1, 0, 1});
            SimpleBitStorage storage = new SimpleBitStorage(bits, 3, raw);
            BitStorage copy = storage.copy();
            assertSame(raw, storage.getRaw());
            assertNotSame(raw, copy.getRaw());
            assertContents(copy, new int[] {1, 0, 1});
            storage.set(0, 0);
            assertEquals(1, copy.get(0));
            copy.set(1, 1);
            assertEquals(0, storage.get(1));
            Arrays.fill(raw, 0);
            assertEquals(0, storage.get(2));
            assertEquals(1, copy.get(2));
        }
    }

    private static int writableMask(int bits) {
        return bits >= 31 ? Integer.MAX_VALUE : (1 << bits) - 1;
    }

    private static long[] pack(int bits, int[] values) {
        int perWord = 64 / bits;
        long mask = (1L << bits) - 1;
        long[] raw = new long[(values.length + perWord - 1) / perWord];
        for (int i = 0; i < values.length; ++i) {
            raw[i / perWord] |= ((long) values[i] & mask) << ((i % perWord) * bits);
        }
        return raw;
    }

    private static void assertContents(BitStorage storage, int[] expected) {
        for (int i = 0; i < expected.length; ++i) {
            assertEquals(expected[i], storage.get(i), "index " + i + ", width " + storage.getBits());
        }
        int[] unpacked = new int[expected.length + 2];
        Arrays.fill(unpacked, 0x12345678);
        storage.unpack(unpacked);
        assertArrayEquals(expected, Arrays.copyOf(unpacked, expected.length));
        assertEquals(0x12345678, unpacked[expected.length]);
        assertEquals(0x12345678, unpacked[expected.length + 1]);
        int[] visited = new int[expected.length];
        int[] count = {0};
        storage.getAll(value -> visited[count[0]++] = value);
        assertEquals(expected.length, count[0]);
        assertArrayEquals(expected, visited);
    }
}
