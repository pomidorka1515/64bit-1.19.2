package net.minecraft.nbt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NbtInt128Test {
    @TempDir
    Path directory;

    @Test
    void requiredLongPairsRetainBothHalvesThroughCompressedNbt() throws IOException {
        Int128[] values = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.ONE,
            Int128.fromLong(-1L), Int128.fromLong(Long.MIN_VALUE), Int128.fromLong(Long.MAX_VALUE),
            Int128.of(1L, 0L), Int128.of(-2L, -1L), Int128.of(0L, Long.MIN_VALUE)};
        CompoundTag data = new CompoundTag();
        for (int i = 0; i < values.length; ++i) {
            NbtUtils.writeInt128(data, "axis" + i, values[i]);
            assertTrue(data.contains("axis" + i, Tag.TAG_LONG));
            assertTrue(data.contains("axis" + i + "Hi", Tag.TAG_LONG));
            assertEquals(values[i].lo(), data.getLong("axis" + i));
            assertEquals(values[i].hi(), data.getLong("axis" + i + "Hi"));
        }
        assertEquals(values.length * 2, data.size());
        Path file = directory.resolve("coordinates.dat");
        NbtIo.writeCompressed(data, file.toFile());
        CompoundTag restored = NbtIo.readCompressed(file.toFile());
        for (int i = 0; i < values.length; ++i) {
            assertEquals(values[i], NbtUtils.readInt128(restored, "axis" + i));
        }
    }

    @Test
    void arbitraryPairsAndSameLowWordsKeepDistinctIdentity() {
        Random random = new Random(1003L);
        CompoundTag data = new CompoundTag();
        for (int i = 0; i < 1000; ++i) {
            Int128 value = Int128.of(random.nextLong(), random.nextLong());
            NbtUtils.writeInt128(data, "xPos", value);
            assertEquals(value, NbtUtils.readInt128(data, "xPos"));
            Int128 other = Int128.of(value.hi() ^ 1L, value.lo());
            NbtUtils.writeInt128(data, "zPos", other);
            assertEquals(other, NbtUtils.readInt128(data, "zPos"));
            assertNotEquals(NbtUtils.readInt128(data, "xPos"), NbtUtils.readInt128(data, "zPos"));
        }
    }

    @Test
    void missingHalvesNeverDefaultOrSignExtend() {
        for (long low : new long[] {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            CompoundTag data = new CompoundTag();
            data.putLong("X", low);
            assertRejected(data, "X", "XHi");
            data.remove("X");
            data.putLong("XHi", -1L);
            assertRejected(data, "X", "X");
        }
        assertRejected(new CompoundTag(), "X", "X");
    }

    @Test
    void neitherHalfAcceptsNumericCoercionOrOtherTagTypes() {
        Tag[] invalid = {ByteTag.valueOf((byte) 1), ShortTag.valueOf((short) 1), IntTag.valueOf(1),
            FloatTag.valueOf(1.0F), DoubleTag.valueOf(1.0D), StringTag.valueOf("1"),
            new ByteArrayTag(new byte[] {1}), new IntArrayTag(new int[] {1}),
            new LongArrayTag(new long[] {1L}), new ListTag(), new CompoundTag()};
        for (String half : new String[] {"X", "XHi"}) {
            for (Tag invalidTag : invalid) {
                CompoundTag data = new CompoundTag();
                NbtUtils.writeInt128(data, "X", Int128.ONE);
                data.put(half, invalidTag);
                assertRejected(data, "X", half);
            }
        }
    }

    @Test
    void blockPositionsRequireHighWordsEvenForLegacyRangeValues() {
        for (BlockPos position : new BlockPos[] {
                new BlockPos(Int128.MIN, Integer.MIN_VALUE, Int128.MAX),
                new BlockPos(Int128.MAX, Integer.MAX_VALUE, Int128.MIN),
                new BlockPos(-1L, 64, 0L), new BlockPos(Int128.of(1L, 0L), -64, Int128.of(-2L, 0L))}) {
            CompoundTag data = NbtUtils.writeBlockPos(position);
            assertEquals(position, NbtUtils.readBlockPos(data));
            for (String half : new String[] {"X", "XHi", "Z", "ZHi"}) {
                CompoundTag missing = data.copy();
                missing.remove(half);
                assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(missing), half);
            }
        }
    }

    private static void assertRejected(CompoundTag data, String key, String missingHalf) {
        CompoundTag before = data.copy();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> NbtUtils.readInt128(data, key));
        assertTrue(error.getMessage().contains(missingHalf));
        assertEquals(before, data);
    }
}
