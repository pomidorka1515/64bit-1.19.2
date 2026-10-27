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
    void narrowAndThresholds() {
        for (long n : new long[]{Long.MIN_VALUE, -65, -1, 0, 1, 65, Long.MAX_VALUE}) {
            Int1088 value = Int1088.fromLong(n);
            assertEquals(n, WorldgenCoordinate.seedCoordinate(value));
            assertEquals((double)n, WorldgenCoordinate.toDouble(value));
            PreciseNoiseCoordinate.Split a = new PreciseNoiseCoordinate.Split(), b = new PreciseNoiseCoordinate.Split();
            WorldgenCoordinate.scale(value, 0.123, 0, a);
            WorldgenCoordinate.scale(n >> 63, n, 0.123, 0, b);
            assertEquals(a.lattice, b.lattice);
            assertEquals(a.fraction, b.fraction);
        }
        assertFalse(WorldgenCoordinate.squareSumExceeds(Int1088.fromLong(64), Int1088.ZERO, 4096));
        assertTrue(WorldgenCoordinate.squareSumExceeds(Int1088.fromLong(64), Int1088.ONE, 4096));
        assertTrue(WorldgenCoordinate.squareSumExceeds(Int1088.MAX, Int1088.ZERO, Long.MAX_VALUE));
        assertEquals(Int1088.MAX, WorldgenCoordinate.add(Int1088.MAX, 1));
    }
}
