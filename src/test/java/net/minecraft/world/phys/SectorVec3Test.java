package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorVec3Test {
    @Test
    void movementCarriesUseAllLimbsThroughTheLargestFiniteDouble() {
        java.math.BigInteger min = new java.math.BigInteger(Int1088.MIN.toString());
        java.math.BigInteger max = new java.math.BigInteger(Int1088.MAX.toString());
        for (Int1088 block : new Int1088[] {Int1088.ZERO, Int1088.MIN, Int1088.MAX,
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.ONE.shiftLeftSaturated(1024).negate()}) {
            for (double magnitude : new double[] {1.0D, 0x1.0p53, 0x1.0p63, 0x1.0p127,
                    0x1.0p1023, Double.MAX_VALUE}) {
                for (int sign : new int[] {-1, 1}) {
                    double delta = magnitude * sign;
                    java.math.BigInteger sum = new java.math.BigInteger(block.toString())
                            .add(new java.math.BigDecimal(delta).toBigIntegerExact());
                    SectorVec3 result = SectorVec3.fromBlockAndFraction(block, 0, 4.5D, block, 0)
                            .add(delta, 0.25D, delta);
                    assertEquals(Int1088.parseDecimal(sum.max(min).min(max).toString()), result.blockX());
                    assertEquals(result.blockX(), result.blockZ());
                    assertEquals(sum.compareTo(max) > 0 ? Math.nextDown(1.0D) : 0.0D, result.subX());
                    assertEquals(result.subX(), result.subZ());
                    assertEquals(4.75D, result.y());
                }
            }
        }
    }

    @Test
    void everyLimbSeamRetainsFractionsAndExactCarrierIdentity() {
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                java.math.BigInteger seam = java.math.BigInteger.ONE.shiftLeft(bit)
                        .multiply(java.math.BigInteger.valueOf(sign));
                Int1088 before = Int1088.parseDecimal(seam.subtract(java.math.BigInteger.ONE).toString());
                Int1088 after = Int1088.parseDecimal(seam.toString());
                SectorVec3 start = SectorVec3.fromBlockAndFraction(before, 0.75D, 10, after, 0.25D);
                SectorVec3 moved = start.add(0.5D, 0, -0.5D);
                assertEquals(after, moved.blockX());
                assertEquals(before, moved.blockZ());
                assertEquals(0.25D, moved.subX());
                assertEquals(0.75D, moved.subZ());
                assertEquals(start, moved.add(-0.5D, 0, 0.5D));
                Vec3 carrier = start.toExactVec3();
                assertEquals(start, SectorVec3.fromApproximate(carrier));
                assertEquals(moved, carrier.add(0.5D, 0, -0.5D).exactPosition());
                assertEquals(start.hashCode(), SectorVec3.fromApproximate(carrier).hashCode());
                assertFalse(start.equals(start.withX(Int1088.fromLong(before.limb(0)), start.subX())));
            }
        }
    }

    @Test
    void decimalReplacementRoundsFractionsWithoutRoundingTheIntegerPart() {
        String huge = java.math.BigInteger.ONE.shiftLeft(1024).toString();
        SectorVec3 start = SectorVec3.fromDecimal(huge + ".125", 12.5D, "-" + huge + ".75");
        SectorVec3 replaced = start.withXDecimal(huge + ".999999999999999999999999999999")
                .withZDecimal("-" + huge + ".999999999999999999999999999999");
        assertEquals(start.blockX().add(Int1088.ONE), replaced.blockX());
        assertEquals(start.blockZ(), replaced.blockZ());
        assertEquals(0.0D, replaced.subX());
        assertEquals(0.0D, replaced.subZ());
        assertEquals(12.5D, replaced.y());
        assertEquals(start.blockZ(), start.withXDecimal(".5").blockZ());
        assertEquals(0.5D, start.withXDecimal(".5").subX());
        assertEquals(Int1088.fromLong(-1), start.withZDecimal("-0.5").blockZ());
        assertEquals(0.5D, start.withZDecimal("-0.5").subZ());
        assertEquals(Int1088.MIN, start.withXDecimal(Int1088.MIN.toString()).blockX());
        assertEquals(Int1088.MAX, start.withZDecimal(Int1088.MAX + ".").blockZ());
        for (String invalid : new String[] {"", ".", "+", "-", "+-1", "+-1.5", "--1", "1-2",
                "1.2.3", "NaN", "Infinity", "1e3", " 1", "1 ", Int1088.MIN + ".1",
                new java.math.BigInteger(Int1088.MAX.toString()).add(java.math.BigInteger.ONE).toString()}) {
            assertThrows(IllegalArgumentException.class, () -> start.withXDecimal(invalid), invalid);
            assertThrows(IllegalArgumentException.class, () -> start.withZDecimal(invalid), invalid);
        }
    }

    @Test
    void nonFiniteHorizontalMovementRecoversOrSaturatesWithoutWrapping() {
        SectorVec3 start = SectorVec3.fromBlockAndFraction(7, 0.25D, 12, -9, 0.75D);
        assertEquals(start, start.add(Double.NaN, 0, Double.NaN));
        SectorVec3 moved = start.add(Double.POSITIVE_INFINITY, 0, Double.NEGATIVE_INFINITY);
        assertEquals(Int1088.MAX, moved.blockX());
        assertEquals(Math.nextDown(1.0D), moved.subX());
        assertEquals(Int1088.MIN, moved.blockZ());
        assertEquals(0.0D, moved.subZ());
        assertThrows(IllegalArgumentException.class, () -> start.add(0, Double.NaN, 0));
    }

    @Test
    void decimalFormattingMatchesIndependentOracleAtEveryDisplayPrecision() {
        Int1088[] blocks = {Int1088.MIN, Int1088.MAX, Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.ONE.shiftLeftSaturated(1024).negate(), Int1088.fromLong(-1), Int1088.ZERO};
        double[] fractions = {0.0D, Double.MIN_VALUE, 0.0005D, 0.0015D, 0.125D, 0.9995D, Math.nextDown(1.0D)};
        for (Int1088 block : blocks) {
            for (double fraction : fractions) {
                for (int digits = 0; digits <= 8; ++digits) {
                    String expected = new java.math.BigDecimal(block.toDecimalString())
                            .add(java.math.BigDecimal.valueOf(fraction))
                            .setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString();
                    assertEquals(expected, SectorVec3.formatCoordinate(block, fraction, digits));
                }
            }
        }
    }

    @Test
    void hostileCarriesMatchExactDecimalOracleWithoutHotDecimalArithmetic() {
        java.util.Random random = new java.util.Random(1008);
        for (int i = 0; i < 500; ++i) {
            long[] limbs = new long[Int1088.LIMBS];
            for (int limb = 0; limb < limbs.length; ++limb) limbs[limb] = random.nextLong();
            Int1088 block = i == 0 ? Int1088.MIN : i == 1 ? Int1088.MAX : Int1088.of(limbs);
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
            java.math.BigInteger min = new java.math.BigInteger(Int1088.MIN.toString());
            java.math.BigInteger max = new java.math.BigInteger(Int1088.MAX.toString());
            SectorVec3 result = SectorVec3.fromBlockAndFraction(block, carry, 0, block, carry);
            Int1088 expected = Int1088.parseDecimal(sum.max(min).min(max).toString());
            assertEquals(expected, result.blockX());
            assertEquals(expected, result.blockZ());
            assertEquals(sum.compareTo(max) > 0 ? Math.nextDown(1.0D) : 0.0D, result.subX());
            assertEquals(result.subX(), result.subZ());
        }
    }

    @Test
    void carriesAndBorrowsAcrossUnsignedLowWordSeam() {
        Int1088 before = Int1088.of(-1L, 5L);
        Int1088 after = Int1088.of(0L, 6L);
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
        SectorVec3 position = SectorVec3.fromDecimal(Int1088.MAX + ".25", 64.0D,
                "-18446744073709551616.25");
        assertEquals(Int1088.MAX, position.blockX());
        assertEquals(0.25D, position.subX());
        assertEquals(Int1088.of(-1L, -2L), position.blockZ());
        assertEquals(0.75D, position.subZ());
        assertEquals(Int1088.MAX + ".25", position.formatX(2));
        assertEquals("-18446744073709551616.25", position.formatZ(2));
        assertThrows(IllegalArgumentException.class,
                () -> SectorVec3.fromDecimal(new java.math.BigInteger(Int1088.MAX.toString()).add(java.math.BigInteger.ONE).toString(), "0"));
    }

    @Test
    void localSpanGuardUsesMagnitudeRatherThanHighWordEquality() {
        Int1088[][] pairs = {
                {Int1088.ZERO, Int1088.fromLong(1L << 62)},
                {Int1088.ZERO, Int1088.of(Long.MIN_VALUE, 0L)},
                {Int1088.ZERO, Int1088.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L)},
                {Int1088.MIN, Int1088.MAX}
        };
        for (Int1088[] pair : pairs) {
            SectorVec3 a = SectorVec3.fromBlockAndFraction(pair[0], 0.0D, 0.0D, Int1088.ZERO, 0.0D);
            SectorVec3 b = SectorVec3.fromBlockAndFraction(pair[1], 0.0D, 0.0D, Int1088.ZERO, 0.0D);
            assertFalse(a.isLocalTo(b));
            assertFalse(b.isLocalTo(a));
            assertThrows(IllegalArgumentException.class, () -> SectorPhysicsOrigin.from(a).toLocal(b));
        }
        SectorVec3 a = SectorVec3.fromBlockAndFraction(Int1088.of(-1L, 0L), 0.25D,
                0.0D, Int1088.ZERO, 0.0D);
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
        assertEquals(Int1088.fromLong(huge), whole.blockX());
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
        assertEquals(Int1088.fromLong(huge + 1L), position.blockX());
        assertEquals(Int1088.fromLong(huge + 1L), position.blockZ());
        assertEquals(0.25D, position.subX(), 1.0E-12D);
        assertEquals(0.25D, position.subZ(), 1.0E-12D);

        long billion = 1_000_000_000_000_000_000L;
        position = SectorVec3.fromBlockAndFraction(billion, 0.25D, 2.0D, billion, 0.25D);
        for (int i = 0; i < 1000; ++i) position = position.add(0.001D, 0.0D, 0.001D);
        assertEquals(Int1088.fromLong(billion + 1L), position.blockX());
        assertEquals(0.25D, position.subX(), 1.0E-12D);
    }

    @Test
    void usesFloorForNegativeApproximatePositions() {
        SectorVec3 position = SectorVec3.fromApproximate(-1.25D, 3.0D, -2.75D);
        assertEquals(Int1088.fromLong(-2L), position.blockX());
        assertEquals(0.75D, position.subX(), 0.0D);
        assertEquals(Int1088.fromLong(-3L), position.blockZ());
        assertEquals(0.25D, position.subZ(), 0.0D);
    }

    @Test
    void carriesAndBorrowsAcrossBothBoundaries() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(10L, 0.999999D, 7.0D, -10L, 0.000001D);
        position = position.add(0.000002D, 0.0D, -0.000002D);
        assertEquals(Int1088.fromLong(11L), position.blockX());
        assertEquals(0.000001D, position.subX(), 1.0E-12D);
        assertEquals(Int1088.fromLong(-11L), position.blockZ());
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
        assertEquals(Int1088.fromLong(7L), position.blockX());
        assertEquals(0.25D, position.subX());
        assertEquals(Int1088.fromLong(5L), position.blockZ());
        assertEquals(0.75D, position.subZ());

        SectorVec3 invalidFractions = SectorVec3.fromBlockAndFraction(10L, Double.NaN, Double.POSITIVE_INFINITY,
                20L, Double.NEGATIVE_INFINITY);
        assertEquals(Int1088.fromLong(10L), invalidFractions.blockX());
        assertEquals(0.0D, invalidFractions.subX());
        assertEquals(Int1088.fromLong(20L), invalidFractions.blockZ());
        assertEquals(0.0D, invalidFractions.subZ());
        assertEquals(0.0D, invalidFractions.y());

        assertThrows(IllegalArgumentException.class, () -> SectorVec3.fromApproximate(Double.NaN, 0.0D, 0.0D));
        assertFalse(SectorVec3.fromBlockAndFraction(0L, 0.0D, 0.0D, 0L, 0.0D).isFinite() == false);
    }

    @Test
    void movementStopsAtBothRepresentableEdges() {
        SectorVec3 max = SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.5D, 0.0D, Int1088.MAX, 0.5D)
                                 .add(100.0D, 0.0D, 100.0D);
        assertEquals(Int1088.MAX, max.blockX());
        assertEquals(Int1088.MAX, max.blockZ());
        assertEquals(Math.nextDown(1.0D), max.subX());
        assertEquals(Math.nextDown(1.0D), max.subZ());
        SectorVec3 min = SectorVec3.fromBlockAndFraction(Int1088.MIN, 0.5D, 0.0D, Int1088.MIN, 0.5D)
                                 .add(-100.0D, 0.0D, -100.0D);
        assertEquals(Int1088.MIN, min.blockX());
        assertEquals(Int1088.MIN, min.blockZ());
        assertEquals(0.0D, min.subX());
        assertEquals(0.0D, min.subZ());
        assertEquals(Double.isFinite(max.toExactVec3().x), true);
    }

    @Test
    void finiteDoubleIngressIsInsideTheWorldEdges() {
        SectorVec3 positive = SectorVec3.fromApproximate(Double.MAX_VALUE, 64.0D, Double.MAX_VALUE);
        Int1088 maximumDoubleBlock = Int1088.parseDecimal(new java.math.BigDecimal(Double.MAX_VALUE).toBigIntegerExact().toString());
        assertEquals(maximumDoubleBlock, positive.blockX());
        assertEquals(0.0D, positive.subX());
        assertEquals(maximumDoubleBlock, positive.blockZ());
        assertEquals(0.0D, positive.subZ());

        SectorVec3 negative = SectorVec3.fromApproximate(-Double.MAX_VALUE, 64.0D, -Double.MAX_VALUE);
        assertEquals(maximumDoubleBlock.negate(), negative.blockX());
        assertEquals(0.0D, negative.subX());
        assertEquals(maximumDoubleBlock.negate(), negative.blockZ());
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
