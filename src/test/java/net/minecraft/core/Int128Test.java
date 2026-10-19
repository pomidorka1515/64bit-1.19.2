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

    @Test
    void arithmeticAndBitsMatchBigInteger() {
        Int128[] edges = {Int128.ZERO, Int128.ONE, Int128.fromLong(-1L), Int128.MIN, Int128.MAX,
            Int128.of(0L, -1L), Int128.of(-1L, 0L), Int128.fromLong(Long.MIN_VALUE),
            Int128.fromLong(Long.MAX_VALUE), Int128.MIN.add(Int128.ONE), Int128.MAX.subtract(Int128.ONE)};
        long[] factors = {0L, 1L, -1L, 2L, -2L, Long.MIN_VALUE, Long.MAX_VALUE};
        int[] shifts = {0, 1, 3, 4, 63, 64, 65, 126, 127, 128, 129, Integer.MAX_VALUE};
        for (Int128 a : edges) {
            for (Int128 b : edges) {
                assertOperations(a, b);
            }
            for (long factor : factors) {
                assertEquals(clamp(toBigInteger(a).multiply(BigInteger.valueOf(factor))), toBigInteger(a.multiplySaturated(factor)));
            }
            for (int shift : shifts) {
                assertShifts(a, shift);
            }
            for (int bit = 0; bit < 128; ++bit) {
                assertEquals(toBigInteger(a).testBit(bit), a.testBit(bit));
            }
        }
        Random random = new Random(0x12802L);
        for (int i = 0; i < 10000; ++i) {
            Int128 a = Int128.of(random.nextLong(), random.nextLong());
            Int128 b = Int128.of(random.nextLong(), random.nextLong());
            assertOperations(a, b);
            long factor = i % 2 == 0 ? random.nextLong() : random.nextInt(33) - 16L;
            assertEquals(clamp(toBigInteger(a).multiply(BigInteger.valueOf(factor))), toBigInteger(a.multiplySaturated(factor)));
            Int128 small = Int128.fromLong(random.nextLong());
            assertEquals(clamp(toBigInteger(small).multiply(BigInteger.valueOf(factor))), toBigInteger(small.multiplySaturated(factor)));
            Int128 reduced = a.shiftRight(random.nextInt(128));
            assertEquals(clamp(toBigInteger(reduced).multiply(BigInteger.valueOf(factor))), toBigInteger(reduced.multiplySaturated(factor)));
            assertShifts(a, random.nextInt(140));
            int bit = random.nextInt(128);
            assertEquals(toBigInteger(a).testBit(bit), a.testBit(bit));
        }
    }

    @Test
    void invalidShiftAndBitIndicesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Int128.ONE.shiftRight(-1));
        assertThrows(IllegalArgumentException.class, () -> Int128.ONE.shiftLeftSaturated(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Int128.ONE.testBit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Int128.ONE.testBit(128));
    }

    private static void assertOperations(Int128 a, Int128 b) {
        BigInteger x = toBigInteger(a);
        BigInteger y = toBigInteger(b);
        assertEquals(wrap(x.add(y)), toBigInteger(a.add(b)));
        assertEquals(wrap(x.subtract(y)), toBigInteger(a.subtract(b)));
        assertEquals(clamp(x.add(y)), toBigInteger(a.addSaturated(b)));
        assertEquals(clamp(x.subtract(y)), toBigInteger(a.subtractSaturated(b)));
        assertEquals(clamp(x.negate()), toBigInteger(a.negate()));
        assertEquals(clamp(x.abs()), toBigInteger(a.absSaturated()));
        assertEquals(x.and(y), toBigInteger(a.and(b)));
        assertEquals(x.or(y), toBigInteger(a.or(b)));
        assertEquals(x.xor(y), toBigInteger(a.xor(b)));
        assertEquals(x.not(), toBigInteger(a.not()));
    }

    private static void assertShifts(Int128 value, int bits) {
        BigInteger expected = toBigInteger(value);
        assertEquals(expected.shiftRight(Math.min(bits, 128)), toBigInteger(value.shiftRight(bits)));
        assertEquals(clamp(expected.shiftLeft(Math.min(bits, 128))), toBigInteger(value.shiftLeftSaturated(bits)));
    }

    private static BigInteger clamp(BigInteger value) {
        return value.max(toBigInteger(Int128.MIN)).min(toBigInteger(Int128.MAX));
    }

    private static BigInteger wrap(BigInteger value) {
        BigInteger modulus = BigInteger.ONE.shiftLeft(128);
        BigInteger unsigned = value.mod(modulus);
        return unsigned.testBit(127) ? unsigned.subtract(modulus) : unsigned;
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
