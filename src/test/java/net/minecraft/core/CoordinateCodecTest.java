package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class CoordinateCodecTest {
    @Test
    void fixedLimbEncodingRetainsAllLimbsAndIntegerHeight() {
        for (Int1088 x : new Int1088[] {Int1088.MIN, Int1088.MAX, Int1088.ZERO,
                Int1088.of(Long.MIN_VALUE, 1), Int1088.of(456, -123)}) {
            for (int y : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                Int1088 z = x.not();
                assertRoundTrip(Vec3i.CODEC, new Vec3i(x, y, z));
                assertRoundTrip(BlockPos.CODEC, new BlockPos(x, y, z));
            }
        }
    }

    private static <T extends Vec3i> void assertRoundTrip(Codec<T> codec, T position) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, position).result().orElseThrow();
        long[] expected = new long[35];
        position.getX().copyFixed(expected, 0);
        expected[17] = position.getY();
        position.getZ().copyFixed(expected, 18);
        assertArrayEquals(expected, ((LongArrayTag) encoded).getAsLongArray());
        assertEquals(position, codec.parse(NbtOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void legacyAndWrongLengthInputsAreRejectedWithoutPartialValues() {
        for (Codec<? extends Vec3i> codec : List.of(Vec3i.CODEC, BlockPos.CODEC)) {
            for (int length : new int[] {0, 1, 2, 3, 4, 5, 6, 34, 36}) {
                var result = codec.parse(NbtOps.INSTANCE, new LongArrayTag(new long[length]));
                assertTrue(result.error().isPresent(), "length " + length);
                assertTrue(result.result().isEmpty());
            }
        }
    }

    @Test
    void outOfRangeHeightIsRejectedRatherThanTruncated() {
        for (Codec<? extends Vec3i> codec : List.of(Vec3i.CODEC, BlockPos.CODEC)) {
            for (long y : new long[] {Long.MIN_VALUE, (long) Integer.MIN_VALUE - 1,
                    (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
                long[] words = new long[35];
                words[17] = y;
                var result = codec.parse(NbtOps.INSTANCE, new LongArrayTag(words));
                assertTrue(result.error().isPresent());
                assertTrue(result.result().isEmpty());
            }
        }
    }
}
