package net.minecraft.world.level.levelgen.synth;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldgenCoordinateInt1088Test {
    @Test
    void arithmeticMatchesIntegerOracle() {
        Random random = new Random(1088);
        for (int i = 0; i < 1000; ++i) {
            BigInteger n = new BigInteger(1087, random);
            if (random.nextBoolean()) n = n.negate();
            Int1088 value = Int1088.parseDecimal(n.toString());
            int shift = random.nextInt(1200);
            int divisor = random.nextInt(100000) + 1;
            assertEquals(n.divide(BigInteger.ONE.shiftLeft(shift)).toString(), WorldgenCoordinate.truncShift(value, shift).toDecimalString());
            assertEquals(n.remainder(BigInteger.valueOf(divisor)).intValue(), WorldgenCoordinate.remainder(value, divisor));
            BigInteger[] qr = n.divideAndRemainder(BigInteger.valueOf(divisor));
            assertEquals((n.signum() < 0 && qr[1].signum() != 0 ? qr[0].subtract(BigInteger.ONE) : qr[0]).toString(), WorldgenCoordinate.floorDivide(value, divisor).toDecimalString());
        }
    }

    @Test
    void scalingPowersOfTwoKeepsFullCoordinate() {
        for (int bit : new int[]{63, 64, 127, 128, 512, 1024, 1086}) {
            for (int sign : new int[]{-1, 1}) {
                BigInteger n = BigInteger.ONE.shiftLeft(bit).add(BigInteger.valueOf(7)).multiply(BigInteger.valueOf(sign));
                Int1088 value = Int1088.parseDecimal(n.toString());
                PreciseNoiseCoordinate.Split out = new PreciseNoiseCoordinate.Split();
                WorldgenCoordinate.scale(value, 0.125, 0, out);
                BigInteger floor = n.shiftRight(3);
                assertEquals(floor.longValue(), out.lattice);
                assertEquals(n.subtract(floor.shiftLeft(3)).doubleValue() / 8, out.fraction);
                long[] limbs = new long[19];
                value.copyFixed(limbs, 1);
                PreciseNoiseCoordinate.Split view = new PreciseNoiseCoordinate.Split();
                WorldgenCoordinate.scale(limbs, 1, 17, 0.125, 0, view);
                assertEquals(out.lattice, view.lattice);
                assertEquals(out.fraction, view.fraction);
            }
        }
    }

    @Test
    void scalingMatchesExactDecimalOracle() {
        Random random = new Random(2108);
        for (int i = 0; i < 500; ++i) {
            BigInteger n = new BigInteger(1087, random);
            if (random.nextBoolean()) n = n.negate();
            double scale = Math.scalb(0.5 + random.nextDouble(), -random.nextInt(1100));
            if (random.nextBoolean()) scale = -scale;
            BigDecimal exact = new BigDecimal(n).multiply(new BigDecimal(scale));
            BigInteger floor = exact.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            double fraction = exact.subtract(new BigDecimal(floor)).doubleValue();
            if (fraction == 1.0) { floor = floor.add(BigInteger.ONE); fraction = 0; }
            PreciseNoiseCoordinate.Split out = new PreciseNoiseCoordinate.Split();
            WorldgenCoordinate.scale(Int1088.parseDecimal(n.toString()), scale, 0, out);
            assertEquals(floor.longValue(), out.lattice, "case " + i);
            assertEquals(fraction, out.fraction, "case " + i);
        }
    }

    @Test
    void reusedScalingBuffersDoNotLeakHighWordsBetweenWidthsOrSigns() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int thread = 0; thread < 4; ++thread) {
                tasks.add(() -> {
                    for (int bit : new int[]{1086, 64, 512, 127, 1024, 80, 65}) {
                        for (int sign : new int[]{-1, 1}) {
                            BigInteger n = BigInteger.ONE.shiftLeft(bit).add(BigInteger.valueOf(12345))
                                    .multiply(BigInteger.valueOf(sign));
                            Int1088 coordinate = Int1088.parseDecimal(n.toString());
                            double scale = 0.1;
                            for (int octave : new int[]{0, -7, -63}) {
                                BigDecimal exact = new BigDecimal(n).multiply(new BigDecimal(scale))
                                        .divide(new BigDecimal(BigInteger.ONE.shiftLeft(-octave)));
                                BigInteger floor = exact.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
                                double fraction = exact.subtract(new BigDecimal(floor)).doubleValue();
                                if (fraction == 1.0) { floor = floor.add(BigInteger.ONE); fraction = 0; }
                                PreciseNoiseCoordinate.Split actual = new PreciseNoiseCoordinate.Split();
                                // Poison the same thread's scratch before each smaller sample.
                                WorldgenCoordinate.scale(Int1088.MIN, -0.73, 0, actual);
                                WorldgenCoordinate.scale(coordinate, scale, octave, actual);
                                assertEquals(floor.longValue(), actual.lattice);
                                assertEquals(fraction, actual.fraction);
                                long[] padded = new long[Int1088.LIMBS + 2];
                                coordinate.copyFixed(padded, 1);
                                WorldgenCoordinate.scale(padded, 1, Int1088.LIMBS, scale, octave, actual);
                                assertEquals(floor.longValue(), actual.lattice);
                                assertEquals(fraction, actual.fraction);
                            }
                            long seed = coordinate.limb(0);
                            for (int i = 1; i < Int1088.LIMBS; ++i) {
                                long extra = coordinate.limb(i) ^ (coordinate.limb(i - 1) >> 63);
                                if (extra != 0) seed ^= net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(
                                        extra + i * 0x9e3779b97f4a7c15L);
                            }
                            assertEquals(seed, WorldgenCoordinate.seedCoordinate(coordinate));
                        }
                    }
                    return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) result.get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void narrowAndThresholds() {
        for (long n : new long[]{Long.MIN_VALUE, -65, -1, 0, 1, 65, Long.MAX_VALUE}) {
            Int1088 value = Int1088.fromLong(n);
            assertEquals(n, WorldgenCoordinate.seedCoordinate(value));
            assertEquals((double)n, WorldgenCoordinate.toDouble(value));
            PreciseNoiseCoordinate.Split a = new PreciseNoiseCoordinate.Split(), b = new PreciseNoiseCoordinate.Split();
            WorldgenCoordinate.scale(value, 0.123, 0, a);
            WorldgenCoordinate.scale(n, 0.123, 0, b);
            assertEquals(a.lattice, b.lattice);
            assertEquals(a.fraction, b.fraction);
        }
        assertFalse(WorldgenCoordinate.squareSumExceeds(Int1088.fromLong(64), Int1088.ZERO, 4096));
        assertTrue(WorldgenCoordinate.squareSumExceeds(Int1088.fromLong(64), Int1088.ONE, 4096));
        assertTrue(WorldgenCoordinate.squareSumExceeds(Int1088.MAX, Int1088.ZERO, Long.MAX_VALUE));
        assertEquals(Int1088.MAX, WorldgenCoordinate.add(Int1088.MAX, 1));
    }
}
