package net.minecraft.nbt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NbtInt1088Test {
    @TempDir
    Path directory;

    @Test
    void fixedArraysRoundTripThroughCompressedNbt() throws IOException {
        Int1088[] values = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.ONE,
            Int1088.fromLong(-1), Int1088.fromLong(Long.MIN_VALUE), Int1088.of(0, 1)};
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < values.length; ++i) {
            String key = "axis" + i;
            NbtUtils.writeInt1088(tag, key, values[i]);
            assertTrue(tag.contains(key, Tag.TAG_LONG_ARRAY));
            long[] words = tag.getLongArray(key);
            assertEquals(17, words.length);
            for (int j = 0; j < words.length; ++j) assertEquals(values[i].limb(j), words[j]);
        }
        assertEquals(values.length, tag.size());
        Path file = directory.resolve("coordinates.dat");
        NbtIo.writeCompressed(tag, file.toFile());
        CompoundTag restored = NbtIo.readCompressed(file.toFile());
        for (int i = 0; i < values.length; ++i) {
            assertEquals(values[i], NbtUtils.readInt1088(restored, "axis" + i));
        }
    }

    @Test
    void arbitraryLimbsAndCanonicalSignExtension() {
        Random random = new Random(1088L);
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < 1000; ++i) {
            long[] words = new long[17];
            for (int j = 0; j < words.length; ++j) words[j] = random.nextLong();
            Int1088 value = Int1088.of(words);
            NbtUtils.writeInt1088(tag, "X", value);
            assertArrayEquals(words, tag.getLongArray("X"));
            assertEquals(value, NbtUtils.readInt1088(tag, "X"));
        }
        long[] negative = new long[17];
        Arrays.fill(negative, -1L);
        tag.putLongArray("X", negative);
        assertEquals(1, NbtUtils.readInt1088(tag, "X").limbCount());
        assertEquals(Int1088.fromLong(-1), NbtUtils.readInt1088(tag, "X"));
    }

    @Test
    void arraysDoNotAliasValues() {
        long[] words = new long[17];
        words[16] = 42;
        Int1088 value = Int1088.of(words);
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeInt1088(tag, "X", value);
        Int1088 decoded = NbtUtils.readInt1088(tag, "X");
        tag.getLongArray("X")[16] = 7;
        assertEquals(42, value.limb(16));
        assertEquals(value, decoded);
        assertNotEquals(value, NbtUtils.readInt1088(tag, "X"));
    }

    @Test
    void blockPositionsRoundTripAndRejectLegacyCoordinates() {
        for (BlockPos pos : new BlockPos[] {
                new BlockPos(Int1088.MIN, Integer.MIN_VALUE, Int1088.MAX),
                new BlockPos(Int1088.of(0, 1), 64, Int1088.fromLong(-1))}) {
            CompoundTag encoded = NbtUtils.writeBlockPos(pos);
            assertEquals(pos, NbtUtils.readBlockPos(encoded));
            for (String axis : new String[] {"X", "Z"}) {
                CompoundTag missing = encoded.copy();
                missing.remove(axis);
                CompoundTag before = missing.copy();
                assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(missing));
                assertEquals(before, missing);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("X", 0);
        tag.putLong("XHi", 0);
        tag.putInt("Y", 0);
        NbtUtils.writeInt1088(tag, "Z", Int1088.ZERO);
        assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(tag));
    }

    @Test
    void rejectsMissingWrongTypeLegacyAndWrongLengths() {
        CompoundTag tag = new CompoundTag();
        assertRejected(tag);
        tag.putLong("X", 1);
        tag.putLong("XHi", 0);
        assertRejected(tag);
        Tag[] invalid = {ByteTag.valueOf((byte) 1), IntTag.valueOf(1), StringTag.valueOf("1"),
            new ByteArrayTag(new byte[17]), new IntArrayTag(new int[17]), new ListTag(), new CompoundTag()};
        for (Tag value : invalid) {
            tag.put("X", value);
            assertRejected(tag);
        }
        for (int length : new int[] {0, 1, 2, 16, 18, 34}) {
            tag.putLongArray("X", new long[length]);
            assertRejected(tag);
        }
    }

    private static void assertRejected(CompoundTag tag) {
        CompoundTag before = tag.copy();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> NbtUtils.readInt1088(tag, "X"));
        assertTrue(error.getMessage().contains("X"));
        assertTrue(error.getMessage().contains("LongArray[17]"));
        assertEquals(before, tag);
    }
}
