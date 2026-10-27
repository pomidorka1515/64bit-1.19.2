package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import java.util.Random;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class Int1088CodecTest {
    @Test
    void encodesFixedLittleEndianLimbsAndRoundTrips() {
        Random random = new Random(0x1088c0decL);
        for (int i = 0; i < 1000; ++i) {
            Int1088 value = i == 0 ? Int1088.ZERO : i == 1 ? Int1088.MIN : i == 2 ? Int1088.MAX : Int1088Test.random(random);
            LongArrayTag encoded = (LongArrayTag) Int1088.CODEC.encodeStart(NbtOps.INSTANCE, value).result().orElseThrow();
            long[] words = encoded.getAsLongArray();
            assertEquals(Int1088.LIMBS, words.length);
            for (int limb = 0; limb < words.length; ++limb) assertEquals(value.limb(limb), words[limb]);
            assertEquals(value, Int1088Codec.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow());
            var json = Int1088.CODEC.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
            assertEquals(value, Int1088.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
        }
    }

    @Test
    void rejectsWrongLengthsAndMalformedInput() {
        for (int length : new int[]{0, 1, 2, 16, 18, 19, 100}) {
            var result = Int1088.CODEC.parse(NbtOps.INSTANCE, new LongArrayTag(new long[length]));
            assertTrue(result.result().isEmpty());
            assertTrue(result.error().orElseThrow().message().contains("exactly 17 limbs"));
        }
        JsonArray malformed = new JsonArray();
        for (int i = 0; i < 16; ++i) malformed.add(0);
        malformed.add("not a long");
        assertTrue(Int1088.CODEC.parse(JsonOps.INSTANCE, malformed).result().isEmpty());
    }

    @Test
    void canonicalizesFixedSignExtension() {
        for (long number : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            long[] words = new long[Int1088.LIMBS];
            java.util.Arrays.fill(words, number < 0 ? -1L : 0L);
            words[0] = number;
            Int1088 decoded = Int1088.CODEC.parse(NbtOps.INSTANCE, new LongArrayTag(words)).result().orElseThrow();
            assertEquals(Int1088.fromLong(number), decoded);
            assertEquals(1, decoded.limbCount());
        }
    }
}
