package net.minecraft.core;

import static net.minecraft.core.Int1088Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088LongOffsetTest {
    @Test
    void primitiveOffsetsMatchOracleAcrossCarriesAndSaturation() {
        for (BigInteger n : boundaries()) {
            Int1088 coordinate = value(n);
            for (long offset : new long[] {0, 1, -1, 16, -16, Long.MIN_VALUE, Long.MAX_VALUE}) {
                exact(clamp(n.add(BigInteger.valueOf(offset))), coordinate.addSaturated(offset));
            }
            assertSame(coordinate, coordinate.addSaturated(0L));
        }
        Random random = new Random(0x10ff5e7);
        for (int i = 0; i < 10000; ++i) {
            Int1088 coordinate = random(random);
            long offset = random.nextLong();
            exact(clamp(integer(coordinate).add(BigInteger.valueOf(offset))), coordinate.addSaturated(offset));
        }
    }

    @Test
    void immutableRightShiftKeepsCanonicalInlineAndWideResults() {
        for (BigInteger n : boundaries()) {
            Int1088 coordinate = value(n);
            for (int bits = 0; bits <= Int1088.BITS + 1; ++bits) {
                Int1088 shifted = coordinate.shiftRight(bits);
                exact(n.shiftRight(bits), shifted);
                assertEquals(value(n.shiftRight(bits)).limbCount(), shifted.limbCount());
            }
        }
    }

    @Test
    void cachedSignExtensionPreservesHistoricalHash() {
        Random random = new Random(0x1088aa5);
        for (int i = 0; i < 10000; ++i) {
            Int1088 coordinate = random(random);
            long hash = 0xcbf29ce484222325L;
            for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
                long mixed = it.unimi.dsi.fastutil.HashCommon.murmurHash3(
                        coordinate.limb(limb) + 0x9e3779b97f4a7c15L * (limb + 1));
                hash = (hash ^ mixed) * 0x100000001b3L;
            }
            assertEquals(it.unimi.dsi.fastutil.HashCommon.murmurHash3(hash), coordinate.hash());
        }
    }
}
