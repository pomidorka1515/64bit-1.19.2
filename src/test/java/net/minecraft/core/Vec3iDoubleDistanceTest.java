package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Vec3iDoubleDistanceTest {
    @Test
    void upperDoubleBoundaryDoesNotRoundMaxIntoTheTarget() {
        // The largest double binade with finite adjacent samples is inside
        // the new domain, so retain this rounding-seam test at 2^1023.
        Int1088 boundary = Int1088.ONE.shiftLeftSaturated(1023);
        Vec3i max = new Vec3i(boundary.subtractSaturated(Int1088.ONE), 0, Int1088.ZERO);
        assertEquals(1.0D, max.distToLowCornerSqr(0x1.0p1023, 0.0D, 0.0D));
        assertEquals(0.5D, max.distToCenterSqr(0x1.0p1023, 0.5D, 0.0D));
        Vec3i previous = new Vec3i(boundary.subtractSaturated(Int1088.fromLong(2)), 0, Int1088.ZERO);
        assertEquals(4.0D, previous.distToLowCornerSqr(0x1.0p1023, 0.0D, 0.0D));
    }

    @Test
    void nonFiniteAndOutsideInputsRemainNonLocal() {
        Int1088 coordinate = Int1088.ONE.shiftLeftSaturated(1023).negate();
        Vec3i min = new Vec3i(coordinate, 0, coordinate);
        assertEquals(0.0D, min.distToLowCornerSqr(-0x1.0p1023, 0, -0x1.0p1023));
        assertTrue(min.distToLowCornerSqr(Math.nextDown(-0x1.0p1023), 0, -0x1.0p1023) > 0);
        assertEquals(Double.POSITIVE_INFINITY, min.distToLowCornerSqr(Double.POSITIVE_INFINITY, 0, 0));
        assertTrue(Double.isNaN(min.distToLowCornerSqr(Double.NaN, 0, 0)));
    }
}
