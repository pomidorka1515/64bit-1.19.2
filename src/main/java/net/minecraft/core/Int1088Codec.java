package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.stream.LongStream;

/** Fixed-width, little-endian representation of an Int1088. */
public final class Int1088Codec {
    public static final Codec<Int1088> CODEC = Codec.LONG_STREAM.comapFlatMap(stream -> {
        long[] words = stream.limit(18L).toArray();
        if (words.length != Int1088.LIMBS) {
            return DataResult.error("Int1088 requires exactly 17 limbs");
        }
        return DataResult.success(Int1088.fromFixedLimbs(words));
    }, value -> {
        long[] words = new long[Int1088.LIMBS];
        value.copyFixed(words, 0);
        return LongStream.of(words);
    });

    private Int1088Codec() {
    }
}
