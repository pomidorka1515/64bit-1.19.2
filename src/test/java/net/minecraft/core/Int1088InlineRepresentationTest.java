package net.minecraft.core;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Int1088InlineRepresentationTest {
    @Test
    void oneAndTwoLimbValuesHaveNoRetainedArrayAndCopyDefensively() throws Exception {
        var rest = Int1088.class.getDeclaredField("rest");
        rest.setAccessible(true);
        for (Int1088 value : new Int1088[] {Int1088.ZERO, Int1088.ONE, Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.of(-1, 0), Int1088.of(0, -1)}) {
            assertNull(rest.get(value));
            assertNull(rest.get(value.copy()));
            long[] words = new long[Int1088.LIMBS];
            value.copyFixed(words, 0);
            assertEquals(value, Int1088.fromFixedLimbs(words));
            assertEquals(value.hash(), Int1088Math.hash(words, 0, words.length));
            assertEquals(value.hashXZ(), Int1088Math.hashXZ(words, 0, words.length));
            words[0] ^= 1;
            assertNotEquals(value, Int1088.fromFixedLimbs(words));
        }
        assertNotNull(rest.get(Int1088.ONE.shiftLeftSaturated(128)));
    }

    @Test
    void inlineAndWideComparisonsAndHashesAgreeWithPrimitiveViews() {
        Random random = new Random(0x5441088L);
        long[] a = new long[Int1088.LIMBS], b = new long[Int1088.LIMBS];
        for (int i = 0; i < 10_000; ++i) {
            Int1088 left = Int1088.of(random.nextLong(), random.nextLong());
            Int1088 right = i % 2 == 0 ? Int1088.fromLong(random.nextLong())
                    : left.shiftLeftSaturated(i % 1024);
            left.copyFixed(a, 0);
            right.copyFixed(b, 0);
            assertEquals(Integer.signum(Int1088Math.compare(a, 0, a.length, b, 0, b.length)),
                    Integer.signum(left.compareTo(right)));
            assertEquals(Int1088Math.hash(a, 0, a.length), left.hash());
            assertEquals(Int1088Math.hash(b, 0, b.length), right.hash());
            assertEquals(left, left.copy());
        }
    }
}
