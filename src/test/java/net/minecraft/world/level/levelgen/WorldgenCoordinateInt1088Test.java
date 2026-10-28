package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldgenCoordinateInt1088Test {
    @Test
    void narrowSeedsRemainTheOriginalSignedLong() {
        Random random = new Random(903);
        for (long n : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            assertEquals(n, WorldgenCoordinate.seedCoordinate(Int1088.fromLong(n)));
        }
        for (int i = 0; i < 10000; i++) {
            long n = random.nextLong();
            assertEquals(n, WorldgenCoordinate.seedCoordinate(Int1088.fromLong(n)));
        }
    }

    @Test
    void signedLimbSeamsAndRandomValuesMatchIndependentFold() {
        Random random = new Random(1088903);
        for (int bit = 63; bit < 1088; bit += 64) {
            for (int sign : new int[]{-1, 1}) {
                for (int delta = -1; delta <= 1; delta++) {
                    BigInteger n = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign))
                            .add(BigInteger.valueOf(delta));
                    if (n.compareTo(MIN) >= 0 && n.compareTo(MAX) <= 0) check(n);
                }
            }
        }
        check(MIN);
        check(MAX);
        for (int i = 0; i < 10000; i++) {
            BigInteger n = new BigInteger(1087, random);
            check(random.nextBoolean() ? n : n.negate());
        }
    }

    @Test
    void eachUpperLimbAffectsBothSignedFixtures() {
        for (int sign : new int[]{-1, 1}) {
            Set<Long> seeds = new HashSet<>();
            for (int limb = 1; limb < 17; limb++) {
                BigInteger n = BigInteger.ONE.shiftLeft(limb * 64).add(BigInteger.valueOf(37))
                        .multiply(BigInteger.valueOf(sign));
                assertTrue(seeds.add(WorldgenCoordinate.seedCoordinate(value(n))), "limb " + limb);
                assertNotEquals((long) sign * 37, WorldgenCoordinate.seedCoordinate(value(n)));
            }
        }
    }

    private static void check(BigInteger n) {
        assertEquals(fold(n), WorldgenCoordinate.seedCoordinate(value(n)), n.toString());
    }

    static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);

    static Int1088 value(BigInteger n) {
        return Int1088.parseDecimal(n.toString());
    }

    // Extract signed words from the mathematical integer, not Int1088's limb API.
    static long fold(BigInteger n) {
        long seed = n.longValue();
        for (int i = 1; i < 17; i++) {
            long previous = n.shiftRight((i - 1) * 64).longValue();
            long extra = n.shiftRight(i * 64).longValue() ^ (previous >> 63);
            if (extra != 0) {
                long mixed = extra + i * 0x9e3779b97f4a7c15L;
                mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
                mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
                seed ^= mixed ^ (mixed >>> 31);
            }
        }
        return seed;
    }
}
