package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorVec3Test {
    @Test
    void hostileCarriesMatchExactDecimalOracleWithoutHotDecimalArithmetic() {
        java.util.Random random = new java.util.Random(1008);
        for (int i = 0; i < 500; ++i) {
            Int128 block = i == 0 ? Int128.MIN : i == 1 ? Int128.MAX
                    : Int128.of(random.nextLong(), random.nextLong());
            double carry = switch (i % 6) {
                case 0 -> 0x1.0p127;
                case 1 -> -0x1.0p127;
                case 2 -> Math.nextUp(0x1.0p127);
                case 3 -> Math.nextDown(-0x1.0p127);
                case 4 -> 0x1.0p128;
                default -> -0x1.0p128;
            };
            java.math.BigInteger sum = new java.math.BigInteger(block.toString())
                    .add(new java.math.BigDecimal(carry).toBigIntegerExact());
            java.math.BigInteger min = new java.math.BigInteger(Int128.MIN.toString());
            java.math.BigInteger max = new java.math.BigInteger(Int128.MAX.toString());
            SectorVec3 result = SectorVec3.fromBlockAndFraction(block, carry, 0, block, carry);
            Int128 expected = Int128.parseDecimal(sum.max(min).min(max).toString());
            assertEquals(expected, result.blockX());
            assertEquals(expected, result.blockZ());
            assertEquals(sum.compareTo(max) > 0 ? Math.nextDown(1.0D) : 0.0D, result.subX());
            assertEquals(result.subX(), result.subZ());
        }
    }

    @Test
    void carriesAndBorrowsAcrossUnsignedLowWordSeam() {
        Int128 before = Int128.of(5L, -1L);
        Int128 after = Int128.of(6L, 0L);
        SectorVec3 start = SectorVec3.fromBlockAndFraction(before, 0.75D, 4.0D, after, 0.25D);
        SectorVec3 moved = start.add(0.5D, 0.0D, -0.5D);
        assertEquals(after, moved.blockX());
        assertEquals(before, moved.blockZ());
        assertEquals(0.25D, moved.subX());
        assertEquals(0.75D, moved.subZ());
        assertEquals(new Vec3(0.5D, 0.0D, -0.5D), moved.relativeTo(start));
        assertEquals(start, moved.add(-0.5D, 0.0D, 0.5D));
    }

    @Test
    void decimalParsingRetainsFullWidthIdentityAndFractions() {
        SectorVec3 position = SectorVec3.fromDecimal(Int128.MAX + ".25", 64.0D,
                "-18446744073709551616.25");
        assertEquals(Int128.MAX, position.blockX());
        assertEquals(0.25D, position.subX());
        assertEquals(Int128.of(-2L, -1L), position.blockZ());
        assertEquals(0.75D, position.subZ());
        assertEquals(Int128.MAX + ".25", position.formatX(2));
        assertEquals("-18446744073709551616.25", position.formatZ(2));
        assertThrows(IllegalArgumentException.class,
                () -> SectorVec3.fromDecimal("170141183460469231731687303715884105728", "0"));
    }

    @Test
    void localSpanGuardUsesMagnitudeRatherThanHighWordEquality() {
        Int128[][] pairs = {
                {Int128.ZERO, Int128.fromLong(1L << 62)},
                {Int128.ZERO, Int128.of(0L, Long.MIN_VALUE)},
                {Int128.ZERO, Int128.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L)},
                {Int128.MIN, Int128.MAX}
        };
        for (Int128[] pair : pairs) {
            SectorVec3 a = SectorVec3.fromBlockAndFraction(pair[0], 0.0D, 0.0D, Int128.ZERO, 0.0D);
            SectorVec3 b = SectorVec3.fromBlockAndFraction(pair[1], 0.0D, 0.0D, Int128.ZERO, 0.0D);
            assertFalse(a.isLocalTo(b));
            assertFalse(b.isLocalTo(a));
            assertThrows(IllegalArgumentException.class, () -> SectorPhysicsOrigin.from(a).toLocal(b));
        }
        SectorVec3 a = SectorVec3.fromBlockAndFraction(Int128.of(0L, -1L), 0.25D,
                0.0D, Int128.ZERO, 0.0D);
        SectorVec3 b = a.add(2.0D, 0.0D, 0.0D);
        org.junit.jupiter.api.Assertions.assertTrue(a.isLocalTo(b));
        assertEquals(new Vec3(2.0D, 0.0D, 0.0D), b.relativeTo(a));
        org.junit.jupiter.api.Assertions.assertTrue(a.isLocalTo(a.add(WorldBounds.MAX_SAFE_LOCAL_SPAN, 0.0D, 0.0D)));
    }

    @Test
    void preservesLargeBlockCoordinatesAndSubBlocks() {
        long huge = 1L << 53;
        SectorVec3 whole = SectorVec3.fromBlockAndFraction(huge, 0.0D, 4.5D, 0L, 0.0D);
        SectorVec3 quarter = SectorVec3.fromBlockAndFraction(huge, 0.25D, 4.5D, 0L, 0.0D);
        assertEquals(Int128.fromLong(huge), whole.blockX());
        assertEquals(0.0D, whole.subX());
        assertEquals(0.25D, quarter.subX());
        assertEquals(4.5D, quarter.y());
        assertEquals(new BlockPos(huge, 4, 0), quarter.blockPosition());
    }

    @Test
    void repeatedSmallMovementAtLargeCoordinatesRemainsLocal() {
        long huge = 1L << 53;
        SectorVec3 position = SectorVec3.fromBlockAndFraction(huge, 0.25D, 2.0D, huge, 0.25D);
        for (int i = 0; i < 1000; ++i) position = position.add(0.001D, 0.0D, 0.001D);
        assertEquals(Int128.fromLong(huge + 1L), position.blockX());
        assertEquals(Int128.fromLong(huge + 1L), position.blockZ());
        assertEquals(0.25D, position.subX(), 1.0E-12D);
        assertEquals(0.25D, position.subZ(), 1.0E-12D);

        long billion = 1_000_000_000_000_000_000L;
        position = SectorVec3.fromBlockAndFraction(billion, 0.25D, 2.0D, billion, 0.25D);
        for (int i = 0; i < 1000; ++i) position = position.add(0.001D, 0.0D, 0.001D);
        assertEquals(Int128.fromLong(billion + 1L), position.blockX());
        assertEquals(0.25D, position.subX(), 1.0E-12D);
    }

    @Test
    void usesFloorForNegativeApproximatePositions() {
        SectorVec3 position = SectorVec3.fromApproximate(-1.25D, 3.0D, -2.75D);
        assertEquals(Int128.fromLong(-2L), position.blockX());
        assertEquals(0.75D, position.subX(), 0.0D);
        assertEquals(Int128.fromLong(-3L), position.blockZ());
        assertEquals(0.25D, position.subZ(), 0.0D);
    }

    @Test
    void carriesAndBorrowsAcrossBothBoundaries() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(10L, 0.999999D, 7.0D, -10L, 0.000001D);
        position = position.add(0.000002D, 0.0D, -0.000002D);
        assertEquals(Int128.fromLong(11L), position.blockX());
        assertEquals(0.000001D, position.subX(), 1.0E-12D);
        assertEquals(Int128.fromLong(-11L), position.blockZ());
        assertEquals(0.999999D, position.subZ(), 1.0E-12D);
    }

    @Test
    void computesNearbyRelativeDifferencesBeforeDoubleConversion() {
        long huge = 1L << 53;
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(huge, 0.25D, 10.0D, -huge, 0.75D);
        SectorVec3 nearby = SectorVec3.fromBlockAndFraction(huge + 3L, 0.5D, 12.5D, -huge - 2L, 0.25D);
        Vec3 difference = nearby.relativeTo(origin);
        assertEquals(3.25D, difference.x, 0.0D);
        assertEquals(2.5D, difference.y, 0.0D);
        assertEquals(-2.5D, difference.z, 0.0D);
    }

    @Test
    void normalizesInvalidFractionsAndRejectsNonFiniteApproximateInput() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(5L, 2.25D, 1.0D, 6L, -0.25D);
        assertEquals(Int128.fromLong(7L), position.blockX());
        assertEquals(0.25D, position.subX());
        assertEquals(Int128.fromLong(5L), position.blockZ());
        assertEquals(0.75D, position.subZ());

        SectorVec3 invalidFractions = SectorVec3.fromBlockAndFraction(10L, Double.NaN, Double.POSITIVE_INFINITY,
                20L, Double.NEGATIVE_INFINITY);
        assertEquals(Int128.fromLong(10L), invalidFractions.blockX());
        assertEquals(0.0D, invalidFractions.subX());
        assertEquals(Int128.fromLong(20L), invalidFractions.blockZ());
        assertEquals(0.0D, invalidFractions.subZ());
        assertEquals(0.0D, invalidFractions.y());

        assertThrows(IllegalArgumentException.class, () -> SectorVec3.fromApproximate(Double.NaN, 0.0D, 0.0D));
        assertFalse(SectorVec3.fromBlockAndFraction(0L, 0.0D, 0.0D, 0L, 0.0D).isFinite() == false);
    }

    @Test
    void movementStopsAtBothRepresentableEdges() {
        SectorVec3 max = SectorVec3.fromBlockAndFraction(Int128.MAX, 0.5D, 0.0D, Int128.MAX, 0.5D)
                                 .add(100.0D, 0.0D, 100.0D);
        assertEquals(Int128.MAX, max.blockX());
        assertEquals(Int128.MAX, max.blockZ());
        assertEquals(Math.nextDown(1.0D), max.subX());
        assertEquals(Math.nextDown(1.0D), max.subZ());
        SectorVec3 min = SectorVec3.fromBlockAndFraction(Int128.MIN, 0.5D, 0.0D, Int128.MIN, 0.5D)
                                 .add(-100.0D, 0.0D, -100.0D);
        assertEquals(Int128.MIN, min.blockX());
        assertEquals(Int128.MIN, min.blockZ());
        assertEquals(0.0D, min.subX());
        assertEquals(0.0D, min.subZ());
        assertEquals(Double.isFinite(max.toApproximateVec3().x), true);
    }

    @Test
    void clampsLegacyDoubleIngressAtTheWorldEdges() {
        SectorVec3 positive = SectorVec3.fromApproximate(Double.MAX_VALUE, 64.0D, Double.MAX_VALUE);
        Int128 maximumDoubleBlock = Int128.MAX;
        assertEquals(maximumDoubleBlock, positive.blockX());
        assertEquals(0.0D, positive.subX());
        assertEquals(maximumDoubleBlock, positive.blockZ());
        assertEquals(0.0D, positive.subZ());

        SectorVec3 negative = SectorVec3.fromApproximate(-Double.MAX_VALUE, 64.0D, -Double.MAX_VALUE);
        assertEquals(WorldBounds.MIN_BLOCK, negative.blockX());
        assertEquals(0.0D, negative.subX());
        assertEquals(WorldBounds.MIN_BLOCK, negative.blockZ());
        assertEquals(0.0D, negative.subZ());
    }

    @Test
    void preservesYAndConvertsToLocalCoordinates() {
        long huge = 1_000_000_000_000_000_000L;
        SectorVec3 position = SectorVec3.fromBlockAndFraction(huge, 0.25D, 64.75D, -huge, 0.5D);
        assertEquals(64.75D, position.withY(64.75D).y());
        Vec3 local = position.toLocal(huge, 64, -huge);
        assertEquals(0.25D, local.x, 0.0D);
        assertEquals(0.75D, local.y, 0.0D);
        assertEquals(0.5D, local.z, 0.0D);
    }
}
