package net.minecraft.world.phys;

import net.minecraft.world.phys.SectorVec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the exact-dismount arithmetic used by vehicles.
 *
 * <p>The boat/horse dismount path applies a small horizontal escape offset to
 * the vehicle's exact split-coordinate position.  The previous lossy form
 * reconstructed {@code getX() + escape.x} as one global double, which at
 * coordinates near 2^63 snapped the dismounted passenger onto the 1024-block
 * double grid and flung every entity to 9223372036854774800.0.</p>
 */
class ExactDismountMathTest {
   /** The exact split used by Boat/AbstractHorse for their escape offsets. */
   private static SectorVec3 addEscape(SectorVec3 base, double escapeX, double escapeZ) {
      long floorX = (long)Math.floor(escapeX);
      long floorZ = (long)Math.floor(escapeZ);
      long blockX = WorldBounds.addBlockOffset(base.blockX(), floorX);
      long blockZ = WorldBounds.addBlockOffset(base.blockZ(), floorZ);
      double subX = base.subX() + escapeX - (double)floorX;
      double subZ = base.subZ() + escapeZ - (double)floorZ;
      return SectorVec3.fromBlockAndFraction(blockX, subX, base.y(), blockZ, subZ);
   }

   @Test
   void boatEscapeOffsetPreservesSubBlockBitsNearTwoToThe63() {
      // One block below Long.MAX_VALUE: any global-double reconstruction here
      // would quantize the position onto the 2048-block representable grid.
      long edge = Long.MAX_VALUE - 1L;
      SectorVec3 boat = SectorVec3.fromBlockAndFraction(edge, 0.75D, 64.0D, edge, 0.25D);
      SectorVec3 dismount = addEscape(boat, 1.2D, -1.1D);
      assertEquals(edge + 1L, dismount.blockX());
      assertEquals(0.75D + 1.2D - 1.0D, dismount.subX(), 1.0E-12D);
      assertEquals(edge - 1L, dismount.blockZ());
      assertEquals(0.25D - 1.1D + 1.0D, dismount.subZ(), 1.0E-12D);
   }

   @Test
   void approximateMirrorAtEdgeStaysInsideWorldBounds() {
      SectorVec3 edge = SectorVec3.fromBlockAndFraction(Long.MAX_VALUE, 0.5D, 0.0D, Long.MIN_VALUE, 0.5D);
      Vec3 approximate = edge.toApproximateVec3();
      assertTrue(approximate.x < 0x1.0p63);
      assertTrue(approximate.z >= (double)WorldBounds.MIN_BLOCK);
   }

   @Test
   void negativeEscapeAcrossBlockBoundaryIsExact() {
      SectorVec3 base = SectorVec3.fromBlockAndFraction(Long.MIN_VALUE + 2L, 0.1D, 5.0D, Long.MIN_VALUE + 2L, 0.1D);
      SectorVec3 moved = addEscape(base, -0.4D, -0.4D);
      assertEquals(Long.MIN_VALUE + 1L, moved.blockX());
      assertEquals(Long.MIN_VALUE + 1L, moved.blockZ());
      assertEquals(0.7D, moved.subX(), 1.0E-12D);
      assertEquals(0.7D, moved.subZ(), 1.0E-12D);
   }
}
