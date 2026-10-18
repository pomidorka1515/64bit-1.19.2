package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SectorAABBTest {
    @Test
    void blockFactoriesAndMergesRetainHugeExactBounds() {
        long huge = 1L << 60;
        AABB first = new AABB(new net.minecraft.core.BlockPos(huge, 64, -huge));
        AABB second = new AABB(new net.minecraft.core.BlockPos(huge + 1, 64, -huge));
        assertEquals(huge + 1, first.getSectorBounds().maxBlockX());
        assertEquals(-huge + 1, first.getSectorBounds().maxBlockZ());
        SectorAABB merged = first.minmax(second).getSectorBounds();
        assertEquals(huge, merged.minBlockX());
        assertEquals(huge + 2, merged.maxBlockX());
        SectorAABB intersection = first.inflate(0.5D).intersect(second).getSectorBounds();
        assertEquals(huge + 1, intersection.minBlockX());
        assertEquals(huge + 1, intersection.maxBlockX());
        assertEquals(0.5D, intersection.maxSubX());
        SectorAABB structure = AABB.of(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                huge, 64, -huge, huge + 2, 66, -huge + 2)).getSectorBounds();
        assertEquals(huge + 3, structure.maxBlockX());
        assertEquals(-huge + 3, structure.maxBlockZ());
    }

    @Test
    void blockFactoryClampsAtSignedLongEdge() {
        SectorAABB box = new AABB(new net.minecraft.core.BlockPos(Long.MAX_VALUE, 64, Long.MIN_VALUE))
                .getSectorBounds();
        assertEquals(Long.MAX_VALUE, box.maxBlockX());
        assertEquals(Math.nextDown(1.0D), box.maxSubX());
        assertEquals(Long.MIN_VALUE + 1, box.maxBlockZ());
    }

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
                                                   1L << 53, 0.1D),
                                           0.6D, 1.8D)
                                 .move(0.3D, 2.0D, -0.3D);
        assertEquals((1L << 53) + 1L, box.maxBlockX());
        assertEquals(0.5D, box.maxSubX(), 1.0E-12D);
        assertEquals(6.0D, box.minY(), 0.0D);
    }

    @Test
    void equalExactBoxesCanBeUsedAsCollisionCacheKeys() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(1L << 53, 0.25D, 64.0D,
                -(1L << 53), 0.75D);
        SectorAABB first = SectorAABB.around(position, 0.6D, 1.8D).move(1.0D, 0.0D, -1.0D);
        SectorAABB equivalent = new SectorAABB((1L << 53), 0.95D, 64.0D, -(1L << 53) - 1L,
                0.44999999999999996D, (1L << 53) + 1L, 0.55D, 65.8D, -(1L << 53),
                0.050000000000000044D);
        SectorAABB different = equivalent.move(0.0D, 1.0D, 0.0D);

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void rebasesBoundsRelativeToExactFractionalOrigin() {
        long huge = 53_905_378_846_979_544L;
        SectorVec3 position = SectorVec3.fromBlockAndFraction(huge, 0.25D, 64.0D, -huge, 0.75D);
        AABB local = SectorAABB.around(position, 0.6D, 1.8D).toLocalAABB(position);

        assertEquals(-0.3D, local.minX, 1.0E-12D);
        assertEquals(0.3D, local.maxX, 1.0E-12D);
        assertEquals(-0.3D, local.minZ, 1.0E-12D);
        assertEquals(0.3D, local.maxZ, 1.0E-12D);
        assertEquals(0.0D, local.minY, 0.0D);
        assertEquals(1.8D, local.maxY, 1.0E-12D);
    }

    @Test
    void compatibilityAabbRetainsExactBoundsThroughQueryTransforms() {
        long huge = 53_905_378_846_979_544L;
        SectorAABB exact = SectorAABB.around(
                SectorVec3.fromBlockAndFraction(huge, 0.25D, 55.0D, huge + 744L, 0.75D), 0.6D, 1.95D);
        AABB compatibility = AABB.fromSectorBounds(exact).inflate(16.0D, 4.0D, 16.0D).expandTowards(0.25D, -0.5D, -0.25D);
        SectorAABB transformed = compatibility.getSectorBounds();
        assertEquals(huge - 17L, transformed.minBlockX());
        assertEquals(0.95D, transformed.minSubX(), 1.0E-12D);
        assertEquals(huge + 761L, transformed.maxBlockZ());
        assertEquals(0.05D, transformed.maxSubZ(), 1.0E-12D);
    }
}
