package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class Vec3iCross1088Test {
    @Test
    void verticalDeterminantRetainsCancellationUntilFinalClamp() {
        Int1088 edge = Int1088.ONE.shiftLeftSaturated(1024);
        assertEquals(Vec3i.ZERO, new Vec3i(edge, 0, edge).cross(new Vec3i(edge, 0, edge)));
        for (long delta : new long[] {-2147483649L, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, 2147483648L}) {
            // (edge + 1) * (edge + delta) - edge * (edge + delta + 1) = delta.
            Int1088 factor = edge.addSaturated(Int1088.fromLong(delta));
            Vec3i first = new Vec3i(edge, 0, edge.addSaturated(Int1088.ONE));
            Vec3i second = new Vec3i(factor, 0, factor.addSaturated(Int1088.ONE));
            BigInteger a = new BigInteger(first.getZ().toString()).multiply(new BigInteger(second.getX().toString()));
            BigInteger b = new BigInteger(first.getX().toString()).multiply(new BigInteger(second.getZ().toString()));
            int expected = a.subtract(b).max(BigInteger.valueOf(Integer.MIN_VALUE)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
            assertEquals(expected, first.cross(second).getY());
        }
        Vec3i fullEdge = new Vec3i(Int1088.MIN, 0, Int1088.MIN);
        assertEquals(Vec3i.ZERO, fullEdge.cross(fullEdge));
    }
}
