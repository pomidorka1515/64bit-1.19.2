package net.minecraft.world.level.block.entity;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheEndGatewayDirectionTest {
    private static Vec3 direction(Int1088 x, Int1088 z) throws Exception {
        Method method = TheEndGatewayBlockEntity.class.getDeclaredMethod("exitSearchDirection", BlockPos.class);
        method.setAccessible(true);
        return (Vec3) method.invoke(null, new BlockPos(x, 0, z));
    }

    @Test
    void wideDirectionsRetainRatioAndUnitLength() throws Exception {
        for (int bit : new int[] {63, 127, 511, 512, 1024, 1086}) {
            BigInteger value = BigInteger.ONE.shiftLeft(bit);
            for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
                check(value.multiply(BigInteger.valueOf(sx)), value.shiftRight(2).multiply(BigInteger.valueOf(sz)));
            }
        }
        BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger max = min.negate().subtract(BigInteger.ONE);
        check(min, max);
        check(max, min);
        check(min, BigInteger.ZERO);
        check(BigInteger.ZERO, max);
        check(max, BigInteger.ONE);
    }

    private static void check(BigInteger x, BigInteger z) throws Exception {
        BigDecimal scale = new BigDecimal(x.abs().max(z.abs()));
        double dx = new BigDecimal(x).divide(scale, MathContext.DECIMAL128).doubleValue();
        double dz = new BigDecimal(z).divide(scale, MathContext.DECIMAL128).doubleValue();
        double length = Math.hypot(dx, dz);
        Vec3 actual = direction(Int1088.parseDecimal(x.toString()), Int1088.parseDecimal(z.toString()));
        assertEquals(dx / length, actual.x, 1e-15, "X at " + x + "," + z);
        assertEquals(dz / length, actual.z, 1e-15, "Z at " + x + "," + z);
        assertEquals(1.0, actual.length(), 1e-15);
        assertEquals(0.0, actual.y);
    }

    @Test
    void ordinaryDirectionsAreUnchanged() throws Exception {
        for (long x : new long[] {0, 1, -1, 1024, Long.MAX_VALUE}) {
            Vec3 expected = new Vec3((double) x, 0, -7).normalize();
            assertEquals(expected, direction(Int1088.fromLong(x), Int1088.fromLong(-7)));
        }
        assertEquals(Vec3.ZERO, direction(Int1088.ZERO, Int1088.ZERO));
    }
}
