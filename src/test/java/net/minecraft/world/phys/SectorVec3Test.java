package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorVec3Test {
   @Test
   void preservesLargeBlockCoordinatesAndSubBlocks() {
      long huge = 1L << 53;
      SectorVec3 whole = SectorVec3.fromBlockAndFraction(huge, 0.0D, 4.5D, 0L, 0.0D);
      SectorVec3 quarter = SectorVec3.fromBlockAndFraction(huge, 0.25D, 4.5D, 0L, 0.0D);
      assertEquals(huge, whole.blockX());
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
      assertEquals(huge + 1L, position.blockX());
      assertEquals(huge + 1L, position.blockZ());
      assertEquals(0.25D, position.subX(), 1.0E-12D);
      assertEquals(0.25D, position.subZ(), 1.0E-12D);

      long billion = 1_000_000_000_000_000_000L;
      position = SectorVec3.fromBlockAndFraction(billion, 0.25D, 2.0D, billion, 0.25D);
      for (int i = 0; i < 1000; ++i) position = position.add(0.001D, 0.0D, 0.001D);
      assertEquals(billion + 1L, position.blockX());
      assertEquals(0.25D, position.subX(), 1.0E-12D);
   }

   @Test
   void usesFloorForNegativeApproximatePositions() {
      SectorVec3 position = SectorVec3.fromApproximate(-1.25D, 3.0D, -2.75D);
      assertEquals(-2L, position.blockX());
      assertEquals(0.75D, position.subX(), 0.0D);
      assertEquals(-3L, position.blockZ());
      assertEquals(0.25D, position.subZ(), 0.0D);
   }

   @Test
   void carriesAndBorrowsAcrossBothBoundaries() {
      SectorVec3 position = SectorVec3.fromBlockAndFraction(10L, 0.999999D, 7.0D, -10L, 0.000001D);
      position = position.add(0.000002D, 0.0D, -0.000002D);
      assertEquals(11L, position.blockX());
      assertEquals(0.000001D, position.subX(), 1.0E-12D);
      assertEquals(-11L, position.blockZ());
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
      assertEquals(7L, position.blockX());
      assertEquals(0.25D, position.subX());
      assertEquals(5L, position.blockZ());
      assertEquals(0.75D, position.subZ());

      SectorVec3 invalidFractions = SectorVec3.fromBlockAndFraction(10L, Double.NaN, Double.POSITIVE_INFINITY,
            20L, Double.NEGATIVE_INFINITY);
      assertEquals(10L, invalidFractions.blockX());
      assertEquals(0.0D, invalidFractions.subX());
      assertEquals(20L, invalidFractions.blockZ());
      assertEquals(0.0D, invalidFractions.subZ());
      assertEquals(0.0D, invalidFractions.y());

      assertThrows(IllegalArgumentException.class, () -> SectorVec3.fromApproximate(Double.NaN, 0.0D, 0.0D));
      assertFalse(SectorVec3.fromBlockAndFraction(0L, 0.0D, 0.0D, 0L, 0.0D).isFinite() == false);
   }

   @Test
   void movementStopsAtBothRepresentableEdges() {
      SectorVec3 max = SectorVec3.fromBlockAndFraction(Long.MAX_VALUE, 0.5D, 0.0D, Long.MAX_VALUE, 0.5D)
            .add(100.0D, 0.0D, 100.0D);
      assertEquals(Long.MAX_VALUE, max.blockX());
      assertEquals(Long.MAX_VALUE, max.blockZ());
      assertEquals(Math.nextDown(1.0D), max.subX());
      assertEquals(Math.nextDown(1.0D), max.subZ());
      SectorVec3 min = SectorVec3.fromBlockAndFraction(Long.MIN_VALUE, 0.5D, 0.0D, Long.MIN_VALUE, 0.5D)
            .add(-100.0D, 0.0D, -100.0D);
      assertEquals(Long.MIN_VALUE, min.blockX());
      assertEquals(Long.MIN_VALUE, min.blockZ());
      assertEquals(0.0D, min.subX());
      assertEquals(0.0D, min.subZ());
      assertEquals(Double.isFinite(max.toApproximateVec3().x), true);
   }

   @Test
   void clampsLegacyDoubleIngressAtTheWorldEdges() {
      SectorVec3 positive = SectorVec3.fromApproximate(Double.MAX_VALUE, 64.0D, Double.MAX_VALUE);
      long maximumDoubleBlock = (long)Math.floor(WorldBounds.clampAbsoluteDouble(Double.MAX_VALUE));
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
