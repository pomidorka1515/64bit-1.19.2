package net.minecraft.commands;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateScaleTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);

    @Test
    void fullWidthScalingMatchesBinaryOracle() {
        Random random = new Random(0x1088);
        BigInteger[] blocks = { BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.negate(), MIN, MAX,
                BigInteger.ONE.shiftLeft(1024), BigInteger.ONE.shiftLeft(1024).negate(),
                BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE) };
        double[] fractions = { 0, 0.125, 0.5, Math.nextDown(1.0), Double.MIN_VALUE };
        double[] factors = { 0.125, 1, 8, 1.3, 0.1, Double.MIN_VALUE, Double.MAX_VALUE };
        for (BigInteger block : blocks) for (double fraction : fractions) for (double factor : factors)
            check(block, fraction, factor);
        for (int i = 0; i < 200; i++) {
            BigInteger block = new BigInteger(1087, random);
            if (random.nextBoolean()) block = block.negate();
            check(block, random.nextDouble(), Math.scalb(1 + random.nextDouble(), random.nextInt(1800) - 900));
        }
    }

    private static void check(BigInteger block, double fraction, double factor) {
        SectorVec3 input = SectorVec3.fromBlockAndFraction(Int1088.parseDecimal(block.toString()), fraction, 42,
                Int1088.parseDecimal(block.toString()), fraction);
        SectorVec3 result = CoordinateScale.scale(input, factor);
        BigDecimal exact = new BigDecimal(block).add(new BigDecimal(fraction)).multiply(new BigDecimal(factor));
        BigInteger floor = exact.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        double sub;
        if (floor.compareTo(MIN) < 0) {
            floor = MIN;
            sub = 0;
        } else if (floor.compareTo(MAX) > 0) {
            floor = MAX;
            sub = Math.nextDown(1.0);
        } else {
            sub = exact.subtract(new BigDecimal(floor)).doubleValue();
            if (sub == 1.0) {
                if (exact.signum() >= 0 && floor.compareTo(MAX) < 0) {
                    floor = floor.add(BigInteger.ONE);
                    sub = 0.0;
                } else sub = Math.nextDown(1.0);
            }
        }
        assertEquals(floor.toString(), result.blockX().toString(), () -> block + " + " + fraction + " * " + factor);
        assertEquals(sub, result.subX(), () -> block + " + " + fraction + " * " + factor);
        assertEquals(result.blockX(), result.blockZ());
        assertEquals(result.subX(), result.subZ());
        assertEquals(42, result.y());
    }
}
