package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SectorAABBTest {
   @Test
   void convertsHugeBoxToLocalCoordinates() {
      long huge = 1_000_000_000_000_000_000L;
      SectorAABB box = new SectorAABB(huge, 0.25D, 64.0D, -huge, 0.5D,
            huge, 0.85D, 65.8D, -huge, 0.9D);
      AABB local = box.toLocalAABB(new SectorPhysicsOrigin(huge, 64, -huge));
      assertEquals(0.25D, local.minX, 0.0D);
      assertEquals(0.85D, local.maxX, 0.0D);
      assertEquals(0.0D, local.minY, 0.0D);
      assertEquals(1.8D, local.maxY, 1.0E-14D);
      assertEquals(0.5D, local.minZ, 0.0D);
      assertEquals(0.9D, local.maxZ, 0.0D);
   }

   @Test
   void canonicalizesAndRangesBoundaryEndpoints() {
      SectorAABB box = new SectorAABB(10L, 1.0D, 0.0D, 20L, 0.0D,
            10L, 2.0D, 1.0D, 20L, 0.99999995D);
      assertEquals(11L, box.minBlockX());
      assertEquals(0.0D, box.minSubX());
      assertEquals(12L, box.maxBlockX());
      assertEquals(0.0D, box.maxSubX());
      assertEquals(9L, box.minBlockXForCollision());
      assertEquals(13L, box.maxBlockXForCollision());
   }

   @Test
   void movementCarriesExactEndpoints() {
      SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(1L << 53, 0.9D, 4.0D,
            1L << 53, 0.1D), 0.6D, 1.8D).move(0.3D, 2.0D, -0.3D);
      assertEquals((1L << 53) + 1L, box.maxBlockX());
      assertEquals(0.5D, box.maxSubX(), 1.0E-12D);
      assertEquals(6.0D, box.minY(), 0.0D);
   }
}
