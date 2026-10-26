package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int128MathTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(127).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static BigInteger big(Int128 value) {
        return new BigInteger(value.toHex(), 16).add(value.hi() < 0L ? MIN.shiftLeft(1) : BigInteger.ZERO);
    }

    @Test
    void arithmeticAndSpansMatchTenThousandRandomPairs() {
        Random random = new Random(104L);
        for (int i = 0; i < 10000; ++i) {
            Int128 a = Int128.of(random.nextLong(), random.nextLong());
            Int128 b = Int128.of(random.nextLong(), random.nextLong());
            checkPair(a, b);
            long delta = random.nextInt(4000001) - 2000000L;
            Int128 nearby = a.addSaturated(Int128.fromLong(delta));
            checkPair(a, nearby);
            assertEquals(big(a).subtract(big(nearby)).doubleValue(),
                Int128Math.lowSignedDifference(a.lo(), nearby.lo()));
        }
    }

    private static void checkPair(Int128 a, Int128 b) {
        BigInteger difference = big(a).subtract(big(b));
        BigInteger sum = big(a).add(big(b));
        assertEquals(Integer.signum(a.compareTo(b)),
            Integer.signum(Int128Math.compare(a.hi(), a.lo(), b.hi(), b.lo())));
        long low = a.lo() + b.lo();
        long carry = Int128Math.addCarry(a.lo(), b.lo(), low);
        long high = a.hi() + b.hi() + carry;
        assertEquals(a.add(b), Int128.of(high, low));
        assertEquals(sum.compareTo(MIN) < 0 || sum.compareTo(MAX) > 0,
            Int128Math.addOverflowed(a.hi(), b.hi(), carry, high));
        low = a.lo() - b.lo();
        long borrow = Int128Math.subtractBorrow(a.lo(), b.lo());
        high = a.hi() - b.hi() - borrow;
        assertEquals(a.subtract(b), Int128.of(high, low));
        assertEquals(difference.compareTo(MIN) < 0 || difference.compareTo(MAX) > 0,
            Int128Math.subtractOverflowed(a.hi(), b.hi(), borrow, high));
        assertEquals(difference.max(LONG_MIN).min(LONG_MAX).longValue(),
            Int128Math.signedDifferenceAsLong(a.hi(), a.lo(), b.hi(), b.lo()));
        boolean expectedExact = difference.abs().compareTo(BigInteger.valueOf(Int128Math.MAX_SAFE_LOCAL_SPAN)) <= 0;
        boolean[] exact = { !expectedExact };
        double result = Int128Math.signedDifference(a, b, exact);
        assertEquals(expectedExact, exact[0]);
        assertEquals(expectedExact, Int128Math.signedDifferenceExact(a, b));
        assertEquals(expectedExact ? difference.doubleValue()
            : difference.signum() < 0 ? (double) Long.MIN_VALUE : (double) Long.MAX_VALUE, result);
        BigInteger distance = difference.abs().min(LONG_MAX);
        assertEquals(distance.longValue(), Int128Math.distance(a.hi(), a.lo(), b.hi(), b.lo()));
        assertEquals(distance.add(BigInteger.ONE).min(LONG_MAX).longValue(),
            Int128Math.distancePlusOne(a.hi(), a.lo(), b.hi(), b.lo()));
    }

    @Test
    void edgesPreserveSignAndUseSpanRatherThanHighEquality() {
        Int128[] edges = { Int128.MIN, Int128.MAX, Int128.ZERO, Int128.ONE,
            Int128.fromLong(-1L), Int128.of(0L, -1L), Int128.of(1L, 0L),
            Int128.of(0L, Long.MIN_VALUE), Int128.of(0L, 1L << 62),
            Int128.fromLong(Int128Math.MAX_SAFE_LOCAL_SPAN),
            Int128.fromLong(Int128Math.MAX_SAFE_LOCAL_SPAN + 1L) };
        for (Int128 a : edges) for (Int128 b : edges) checkPair(a, b);
        assertFalse(Int128Math.signedDifferenceExact(Int128.ZERO, Int128.of(0L, 1L << 62)));
        assertTrue(Int128Math.signedDifferenceExact(Int128.of(0L, -1L), Int128.of(1L, 0L)));
        assertEquals(1L << 20, Int128Math.MAX_SAFE_LOCAL_SPAN);
    }

    @Test
    void shiftsCoverEveryCaseAndRoundTripSaturation() {
        Random random = new Random(10401L);
        Int128[] edges = { Int128.MIN, Int128.MAX, Int128.ZERO, Int128.ONE, Int128.fromLong(-1L) };
        for (int i = 0; i < 100; ++i) {
            Int128 value = i < edges.length ? edges[i] : Int128.of(random.nextLong(), random.nextLong());
            for (int bits = 0; bits <= 130; ++bits) {
                long hi = Int128Math.rightShiftHi(value.hi(), bits);
                long lo = Int128Math.rightShiftLo(value.hi(), value.lo(), bits);
                assertEquals(big(value).shiftRight(bits), big(Int128.of(hi, lo)));
                assertEquals(value.shiftRight(bits), Int128.of(hi, lo));
                lo = Int128Math.leftShiftLo(value.lo(), bits);
                hi = Int128Math.leftShiftHi(value.hi(), value.lo(), bits);
                assertEquals(big(value).shiftLeft(bits).and(MIN.shiftLeft(1).negate().subtract(BigInteger.ONE)),
                    new BigInteger(Int128.of(hi, lo).toHex(), 16));
                boolean exact = Int128Math.rightShiftHi(hi, bits) == value.hi()
                    && Int128Math.rightShiftLo(hi, lo, bits) == value.lo();
                Int128 saturated = exact ? Int128.of(hi, lo) : value.hi() < 0L ? Int128.MIN : Int128.MAX;
                assertEquals(value.shiftLeftSaturated(bits), saturated);
            }
            assertEquals(value.shiftRight(4).hi(), Int128Math.rightShiftHi(value.hi()));
            assertEquals(value.shiftRight(4).lo(), Int128Math.rightShiftLo(value.hi(), value.lo()));
        }
        assertEquals(-1L, Int128Math.rightShiftLo(-1L, 0L, Integer.MAX_VALUE));
        assertEquals(0L, Int128Math.leftShiftHi(-1L, -1L, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Int128Math.rightShiftHi(0L, -1));
        assertThrows(IllegalArgumentException.class, () -> Int128Math.rightShiftLo(0L, 0L, -1));
        assertThrows(IllegalArgumentException.class, () -> Int128Math.leftShiftHi(0L, 0L, -1));
        assertThrows(IllegalArgumentException.class, () -> Int128Math.leftShiftLo(0L, -1));
    }

    @Test
    void offsetsFloorAndClampOnlyTheFinalSum() {
        Random random = new Random(10402L);
        double[] offsets = { -0.1, 0.1, -1.1, 0.0, -0x1.0p127, 0x1.0p127,
            -0x1.8p127, 0x1.8p127, -0x1.0p128, 0x1.0p128, Double.MAX_VALUE };
        Int128[] bases = { Int128.MIN, Int128.MAX, Int128.ZERO, Int128.of(0L, -1L) };
        for (Int128 base : bases) for (double offset : offsets) checkOffset(base, offset);
        for (int i = 0; i < 10000; ++i) {
            Int128 base = Int128.of(random.nextLong(), random.nextLong());
            checkOffset(base, Math.scalb(random.nextDouble() - 0.5, random.nextInt(132)));
        }
        assertEquals(Int128.ONE, Int128Math.addDoubleBlockOffset(0L, 1L, Double.NaN));
        assertEquals(Int128.MAX, Int128Math.addDoubleBlockOffset(0L, 0L, Double.POSITIVE_INFINITY));
        assertEquals(Int128.MIN, Int128Math.addDoubleBlockOffset(0L, 0L, Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, Int128Math.safeProduct(Long.MAX_VALUE, 2L));
        assertEquals(0L, Int128Math.safeProduct(Long.MAX_VALUE, 0L));
        assertEquals(42L, Int128Math.safeProduct(6L, 7L));
        assertThrows(IllegalArgumentException.class, () -> Int128Math.safeProduct(-1L, 1L));
    }

    private static void checkOffset(Int128 base, double offset) {
        BigInteger expected = big(base).add(new BigDecimal(offset).setScale(0, RoundingMode.FLOOR).toBigIntegerExact());
        assertEquals(expected.max(MIN).min(MAX), big(Int128Math.addDoubleBlockOffset(base.hi(), base.lo(), offset)));
    }
}
