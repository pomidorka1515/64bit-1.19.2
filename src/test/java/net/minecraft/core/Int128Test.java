package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128Test {
    @Test
    void constantsMatchTheSigned128BitRange() {
        assertEquals(0L, Int128.ZERO.hi());
        assertEquals(0L, Int128.ZERO.lo());
        assertEquals(0L, Int128.ONE.hi());
        assertEquals(1L, Int128.ONE.lo());
        assertEquals(Long.MIN_VALUE, Int128.MIN.hi());
        assertEquals(0L, Int128.MIN.lo());
        assertEquals(Long.MAX_VALUE, Int128.MAX.hi());
        assertEquals(-1L, Int128.MAX.lo());
        assertEquals(BigInteger.ONE.shiftLeft(127).negate(), toBigInteger(Int128.MIN));
        assertEquals(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE), toBigInteger(Int128.MAX));
    }

    @Test
    void fromLongSignExtends() {
        assertEquals(Int128.ZERO, Int128.fromLong(0L));
        assertEquals(Int128.ONE, Int128.fromLong(1L));
        assertEquals(Int128.of(0L, Long.MAX_VALUE), Int128.fromLong(Long.MAX_VALUE));
        assertEquals(Int128.of(-1L, -1L), Int128.fromLong(-1L));
        assertEquals(Int128.of(-1L, Long.MIN_VALUE), Int128.fromLong(Long.MIN_VALUE));
        assertEquals(Int128.of(-1L, Long.MIN_VALUE + 1L), Int128.fromLong(Long.MIN_VALUE + 1L));
        assertTrue(Int128.fromLong(-1L).hi() < 0L);
        assertTrue(Int128.fromLong(Long.MIN_VALUE).compareTo(Int128.ZERO) < 0);
        assertTrue(Int128.fromLong(Long.MAX_VALUE).compareTo(Int128.ZERO) > 0);
    }

    @Test
    void fromUnsignedLongDoesNotSignExtend() {
        assertEquals(Int128.of(0L, -1L), Int128.fromUnsignedLong(-1L));
        assertEquals(Int128.of(0L, Long.MIN_VALUE), Int128.fromUnsignedLong(Long.MIN_VALUE));
        assertEquals(0L, Int128.fromUnsignedLong(-1L).hi());
        assertNotEquals(Int128.fromLong(-1L), Int128.fromUnsignedLong(-1L));
        assertNotEquals(Int128.fromLong(Long.MIN_VALUE), Int128.fromUnsignedLong(Long.MIN_VALUE));
        assertTrue(Int128.fromUnsignedLong(-1L).compareTo(Int128.fromLong(Long.MAX_VALUE)) > 0);
        assertTrue(Int128.fromUnsignedLong(Long.MIN_VALUE).compareTo(Int128.ZERO) > 0);
        assertTrue(Int128.fromUnsignedLong(-1L).compareTo(Int128.of(1L, 0L)) < 0);
    }

    @Test
    void equalsAndHashCodeUseBothHalves() {
        Int128 value = Int128.of(7L, -4L);
        assertEquals(value, value);
        assertEquals(value, Int128.of(7L, -4L));
        assertEquals(value.hashCode(), Int128.of(7L, -4L).hashCode());
        assertNotEquals(value, Int128.of(8L, -4L));
        assertNotEquals(value, Int128.of(7L, -3L));
        assertNotEquals(value, null);
        assertNotEquals(value, Long.valueOf(-4L));
        assertNotEquals(Int128.of(1L, 0L).hashCode(), Int128.of(0L, 1L).hashCode());
        assertEquals(Int128.MIN, Int128.of(Long.MIN_VALUE, 0L));
        assertEquals(Int128.MAX, Int128.of(Long.MAX_VALUE, -1L));
        assertNotEquals(Int128.MIN, Int128.MAX);
    }

    @Test
    void compareToOrdersSignedHighThenUnsignedLow() {
        assertTrue(Int128.MIN.compareTo(Int128.ZERO) < 0);
        assertTrue(Int128.ZERO.compareTo(Int128.ONE) < 0);
        assertTrue(Int128.ONE.compareTo(Int128.MAX) < 0);
        assertTrue(Int128.MIN.compareTo(Int128.MAX) < 0);
        assertEquals(0, Int128.MIN.compareTo(Int128.of(Long.MIN_VALUE, 0L)));
        assertEquals(0, Int128.MAX.compareTo(Int128.of(Long.MAX_VALUE, -1L)));

        assertTrue(Int128.of(-1L, -1L).compareTo(Int128.ZERO) < 0);
        assertTrue(Int128.of(0L, -1L).compareTo(Int128.of(1L, 0L)) < 0);
        assertTrue(Int128.of(5L, 0L).compareTo(Int128.of(5L, 1L)) < 0);
        assertTrue(Int128.of(5L, 1L).compareTo(Int128.of(5L, Long.MAX_VALUE)) < 0);
        assertTrue(Int128.of(5L, Long.MAX_VALUE).compareTo(Int128.of(5L, Long.MIN_VALUE)) < 0);
        assertTrue(Int128.of(5L, Long.MIN_VALUE).compareTo(Int128.of(5L, -1L)) < 0);

        assertTrue(Int128.fromLong(Long.MIN_VALUE).compareTo(Int128.fromLong(-1L)) < 0);
        assertTrue(Int128.fromLong(-1L).compareTo(Int128.fromLong(0L)) < 0);
        assertTrue(Int128.fromLong(0L).compareTo(Int128.fromLong(1L)) < 0);
        assertTrue(Int128.fromLong(1L).compareTo(Int128.fromLong(Long.MAX_VALUE)) < 0);

        Int128 sample = Int128.of(-3L, 9L);
        assertEquals(0, sample.compareTo(sample));
        assertEquals(0, sample.compareTo(Int128.of(-3L, 9L)));
        assertThrows(NullPointerException.class, () -> sample.compareTo(null));
    }

    @Test
    void orderingAndDecimalMatchBigInteger() {
        assertMatches(Int128.MIN);
        assertMatches(Int128.MAX);
        assertMatches(Int128.ZERO);
        assertMatches(Int128.ONE);
        long[] samples = new long[] {0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - 1L, Long.MIN_VALUE + 1L};
        for (long hi : samples) {
            for (long lo : samples) {
                assertMatches(Int128.of(hi, lo));
            }
        }
        Random random = new Random(0x128L);
        Int128 previous = null;
        for (int i = 0; i < 1000; ++i) {
            Int128 value = Int128.of(random.nextLong(), random.nextLong());
            assertMatches(value);
            if (previous != null) {
                int sign = Integer.signum(previous.compareTo(value));
                assertEquals(sign, Integer.signum(toBigInteger(previous).compareTo(toBigInteger(value))));
                assertEquals(-sign, Integer.signum(value.compareTo(previous)));
                assertEquals(sign == 0, previous.equals(value));
            }
            previous = value;
        }
    }

    private static void assertMatches(Int128 value) {
        BigInteger expected = toBigInteger(value);
        assertEquals(expected.toString(), value.toString());
        assertEquals(expected.signum(), Integer.signum(value.compareTo(Int128.ZERO)));
        assertEquals(0, value.compareTo(Int128.of(value.hi(), value.lo())));
        assertEquals(value, Int128.of(value.hi(), value.lo()));
    }

    private static BigInteger toBigInteger(Int128 value) {
        byte[] bytes = new byte[16];
        long hi = value.hi();
        long lo = value.lo();
        for (int i = 7; i >= 0; --i) {
            bytes[i] = (byte) hi;
            hi >>= 8;
        }
        for (int i = 15; i >= 8; --i) {
            bytes[i] = (byte) lo;
            lo >>= 8;
        }
        return new BigInteger(bytes);
    }
}
