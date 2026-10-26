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
    void fiveLongEncodingRetainsBothHalvesAndIntegerHeight() {
        for (Int128 x : new Int128[] {Int128.MIN, Int128.MAX, Int128.ZERO,
                Int128.of(1, Long.MIN_VALUE), Int128.of(-123, 456)}) {
            for (int y : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                Int128 z = x.not();
                assertRoundTrip(Vec3i.CODEC, new Vec3i(x, y, z));
                assertRoundTrip(BlockPos.CODEC, new BlockPos(x, y, z));
            }
        }
    }

    private static <T extends Vec3i> void assertRoundTrip(Codec<T> codec, T position) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, position).result().orElseThrow();
        assertArrayEquals(new long[] {position.getXHi(), position.getXLo(), position.getY(),
                position.getZHi(), position.getZLo()}, ((LongArrayTag) encoded).getAsLongArray());
        assertEquals(position, codec.parse(NbtOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void legacyAndWrongLengthInputsAreRejectedWithoutPartialValues() {
        for (Codec<? extends Vec3i> codec : List.of(Vec3i.CODEC, BlockPos.CODEC)) {
            for (int length : new int[] {0, 1, 2, 3, 4, 6, 7}) {
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
                var result = codec.parse(NbtOps.INSTANCE, new LongArrayTag(new long[] {0, 0, y, 0, 0}));
                assertTrue(result.error().isPresent());
                assertTrue(result.result().isEmpty());
            }
        }
    }
}
