package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Vec3iDoubleDistanceTest {
    @Test
    void upperDoubleBoundaryDoesNotRoundMaxIntoTheTarget() {
        Vec3i max = new Vec3i(Int128.MAX, 0, Int128.ZERO);
        assertEquals(1.0D, max.distToLowCornerSqr(0x1.0p127, 0.0D, 0.0D));
        assertEquals(0.5D, max.distToCenterSqr(0x1.0p127, 0.5D, 0.0D));
        Vec3i previous = new Vec3i(Int128.MAX.subtract(Int128.ONE), 0, Int128.ZERO);
        assertEquals(4.0D, previous.distToLowCornerSqr(0x1.0p127, 0.0D, 0.0D));
    }

    @Test
    void nonFiniteAndOutsideInputsRemainNonLocal() {
        Vec3i min = new Vec3i(Int128.MIN, 0, Int128.MIN);
        assertEquals(0.0D, min.distToLowCornerSqr(-0x1.0p127, 0, -0x1.0p127));
        assertTrue(min.distToLowCornerSqr(Math.nextDown(-0x1.0p127), 0, -0x1.0p127) > 0);
        assertEquals(Double.POSITIVE_INFINITY, min.distToLowCornerSqr(Double.POSITIVE_INFINITY, 0, 0));
        assertTrue(Double.isNaN(min.distToLowCornerSqr(Double.NaN, 0, 0)));
    }
}
