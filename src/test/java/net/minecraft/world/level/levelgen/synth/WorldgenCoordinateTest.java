package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WorldgenCoordinateTest {
    private static BigInteger integer(long hi, long lo) {
        return BigInteger.valueOf(hi).shiftLeft(64).add(new BigInteger(Long.toUnsignedString(lo)));
    }

    @Test
    void scaleMatchesIndependentExactProduct() {
        Random random = new Random(981274L);
        PreciseNoiseCoordinate.Split out = new PreciseNoiseCoordinate.Split();
        double[] scales = {0.0D, 0.25D, -0.25D, 684.412D, 1.0181268882175227D, Double.MIN_VALUE, 0x1.0p-100, 0x1.0p100};
        for (int i = 0; i < 3000; ++i) {
            long hi = i == 0 ? Long.MIN_VALUE : i == 1 ? Long.MAX_VALUE : random.nextLong();
            long lo = i == 0 ? 0L : i == 1 ? -1L : random.nextLong();
            double scale = scales[i % scales.length];
            int octave = -(i % 20);
            WorldgenCoordinate.scale(hi, lo, scale, octave, out);
            BigDecimal exact = new BigDecimal(integer(hi, lo)).multiply(new BigDecimal(scale))
                    .divide(new BigDecimal(BigInteger.ONE.shiftLeft(-octave)));
            BigInteger lattice = exact.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            double fraction = exact.subtract(new BigDecimal(lattice)).doubleValue();
            if (fraction == 1.0D) { lattice = lattice.add(BigInteger.ONE); fraction = 0.0D; }
            assertEquals(lattice.longValue(), out.lattice, "lattice at " + i);
            assertEquals(fraction, out.fraction, Math.ulp(fraction) * 2.0D, "fraction at " + i);
            assertTrue(out.fraction >= 0.0D && out.fraction < 1.0D);
        }
    }

    @Test
    void floatingPointUsesFullSignedMagnitudeAndSeedKeepsLegacyDomain() {
        Random random = new Random(912L);
        for (int i = 0; i < 10000; ++i) {
            long hi = random.nextLong(), lo = random.nextLong();
            assertEquals(integer(hi, lo).doubleValue(), WorldgenCoordinate.toDouble(hi, lo));
            assertEquals(lo, WorldgenCoordinate.seedCoordinate(lo >> 63, lo));
        }
        assertNotEquals(WorldgenCoordinate.seedCoordinate(0L, 17L), WorldgenCoordinate.seedCoordinate(1L, 17L));
    }

    @Test
    void placementDivisionAndLocalCarriesMatchBigInteger() {
        Random random = new Random(8871L);
        for (int i = 0; i < 3000; ++i) {
            long hi = random.nextLong(), lo = random.nextLong();
            int divisor = 1 + random.nextInt(4096);
            BigInteger value = integer(hi, lo);
            BigInteger[] division = value.divideAndRemainder(BigInteger.valueOf(divisor));
            BigInteger quotient = division[0];
            if (division[1].signum() < 0) quotient = quotient.subtract(BigInteger.ONE);
            var actual = WorldgenCoordinate.floorDivide(hi, lo, divisor);
            assertEquals(quotient, integer(actual.hi(), actual.lo()));
            assertEquals(division[1].intValue(), WorldgenCoordinate.remainder(hi, lo, divisor));
            int offset = random.nextInt(1025) - 512;
            BigInteger sum = value.add(BigInteger.valueOf(offset));
            assertEquals(sum, integer(WorldgenCoordinate.addHigh(hi, lo, offset), WorldgenCoordinate.addLow(hi, lo, offset)));
        }
        assertEquals(Long.MAX_VALUE, WorldgenCoordinate.addHigh(Long.MAX_VALUE, -1L, 1L));
        assertEquals(-1L, WorldgenCoordinate.addLow(Long.MAX_VALUE, -1L, 1L));
        assertEquals(Long.MIN_VALUE, WorldgenCoordinate.addHigh(Long.MIN_VALUE, 0L, -1L));
        assertEquals(0L, WorldgenCoordinate.addLow(Long.MIN_VALUE, 0L, -1L));
    }

    @Test
    void fullWidthScalingDoesNotAllocateAfterWarmup() {
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var out = new PreciseNoiseCoordinate.Split();
        for (int i = 0; i < 100000; ++i) WorldgenCoordinate.scale(17L, i, 684.412D, -7, out);
        long id = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 10000; ++i) WorldgenCoordinate.scale(17L, i, 684.412D, -7, out);
        assertTrue(bean.getThreadAllocatedBytes(id) - before < 1024L);
    }
}
