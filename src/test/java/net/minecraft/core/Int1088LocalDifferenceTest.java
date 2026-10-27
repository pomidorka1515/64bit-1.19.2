package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088LocalDifferenceTest {
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test
    void differencesAndDistancesMatchIndependentOracle() {
        long seed = 108806;
        System.out.println("local-difference seed=" + seed);
        Random random = new Random(seed);
        Int1088[] edges = {Int1088.ZERO, Int1088.ONE, Int1088.fromLong(-1), Int1088.MIN, Int1088.MAX,
                Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.ONE.shiftLeftSaturated(1024).negate()};
        for (Int1088 a : edges) for (Int1088 b : edges) check(a, b);
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            Int1088 boundary = Int1088.ONE.shiftLeftSaturated(limb * 64);
            check(boundary, boundary.subtractSaturated(Int1088.ONE));
            check(boundary.subtractSaturated(Int1088.ONE), boundary);
            check(boundary, Int1088.ZERO);
            check(Int1088.ZERO, boundary);
        }
        for (int i = 0; i < 10000; ++i) {
            Int1088 a = random(random), b = random(random);
            check(a, b);
            check(a, a.addSaturated(Int1088.fromLong(random.nextLong())));
        }
    }

    @Test
    void localExactnessIsNotAWorldLimit() {
        Int1088 origin = Int1088.ONE.shiftLeftSaturated(1024);
        boolean[] exact = {false};
        for (long offset : new long[] {-1048577, -1048576, -1, 0, 1, 1048576, 1048577}) {
            Int1088 value = origin.addSaturated(Int1088.fromLong(offset));
            boolean expected = Math.abs(offset) <= Int1088Math.MAX_SAFE_LOCAL_SPAN;
            double hint = Int1088Math.signedDifference(value, origin, exact);
            assertEquals(expected, exact[0]);
            assertEquals(expected, Int1088Math.signedDifferenceExact(value, origin));
            assertEquals(expected ? (double) offset : offset < 0 ? (double) Long.MIN_VALUE : (double) Long.MAX_VALUE, hint);
        }
    }

    @Test
    void narrowHashesMatchFullSignExtendedValues() {
        Random random = new Random(108807);
        for (long value : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(Int1088.fromLong(value).hashXZ(), Int1088Math.hashXZ(value));
        }
        for (int i = 0; i < 10000; i++) {
            long value = random.nextLong();
            assertEquals(Int1088.fromLong(value).hashXZ(), Int1088Math.hashXZ(value));
        }
    }

    @Test
    void spanProductsSaturateAndRejectNegativeInput() {
        assertEquals(0, Int1088Math.safeProduct(0, Long.MAX_VALUE));
        assertEquals(4096, Int1088Math.safeProduct(16, 256));
        assertEquals(Long.MAX_VALUE, Int1088Math.safeProduct(Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.safeProduct(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.safeProduct(0, -1));
    }

    private static Int1088 random(Random random) {
        long[] limbs = new long[1 + random.nextInt(Int1088.LIMBS)];
        for (int i = 0; i < limbs.length; ++i) limbs[i] = random.nextLong();
        return Int1088.of(limbs);
    }

    private static void check(Int1088 a, Int1088 b) {
        BigInteger difference = new BigInteger(a.toDecimalString()).subtract(new BigInteger(b.toDecimalString()));
        long expected = clamp(difference);
        assertEquals(expected, Int1088Math.signedDifferenceAsLong(a, b));
        long[] x = new long[Int1088.LIMBS + 2], z = new long[Int1088.LIMBS + 3];
        a.copyFixed(x, 1);
        b.copyFixed(z, 2);
        assertEquals(expected, Int1088Math.signedDifferenceAsLong(x, 1, a.limbCount(), z, 2, b.limbCount()));
        assertEquals(expected, Int1088Math.signedDifferenceAsLong(x, 1, Int1088.LIMBS, z, 2, Int1088.LIMBS));
        long distance = clamp(difference.abs());
        assertEquals(distance, Int1088Math.distance(a, b));
        assertEquals(distance == Long.MAX_VALUE ? distance : distance + 1, Int1088Math.distancePlusOne(a, b));
    }

    private static long clamp(BigInteger value) {
        return value.max(LONG_MIN).min(LONG_MAX).longValueExact();
    }
}
