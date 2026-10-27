package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorAABBTest {
    @Test
    void wideBoxesIntersectContainAndRangeAcrossEveryLimbSeam() {
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 seam = Int1088.ONE.shiftLeftSaturated(bit);
                if (sign < 0) seam = seam.negate();
                SectorVec3 position = SectorVec3.fromBlockAndFraction(seam, 0, 64, seam, 0);
                SectorAABB box = SectorAABB.around(position, 1, 2);
                assertEquals(seam.subtract(Int1088.ONE), box.minBlockXForRange());
                assertEquals(seam, box.maxBlockXForRangeInclusive());
                assertEquals(seam.add(Int1088.ONE), box.maxBlockZExclusive());
                assertEquals(1.0D, box.getXsize());
                assertEquals(1.0D, box.getZsize());
                assertEquals(position.withY(65), box.getCenter());
                org.junit.jupiter.api.Assertions.assertTrue(box.contains(position));
                org.junit.jupiter.api.Assertions.assertFalse(box.contains(position.add(0.5D, 0, 0)));
                SectorAABB shifted = box.move(0.25D, 0, -0.25D);
                org.junit.jupiter.api.Assertions.assertTrue(box.intersects(shifted));
                SectorAABB intersection = box.intersect(shifted);
                assertEquals(0.75D, intersection.getXsize());
                assertEquals(0.75D, intersection.getZsize());
                assertEquals(1.25D, box.minmax(shifted).getXsize());
                assertEquals(box, box.move(1, 0, -1).move(-1, 0, 1));
            }
        }
    }

    @Test
    void midpointUsesFullWidthAverageWithoutOverflow() {
        java.math.BigInteger min = new java.math.BigInteger(Int1088.MIN.toString());
        java.math.BigInteger max = new java.math.BigInteger(Int1088.MAX.toString());
        java.math.BigInteger wide = java.math.BigInteger.ONE.shiftLeft(1024);
        for (java.math.BigInteger[] pair : new java.math.BigInteger[][] {
                {min, max}, {min, min.add(java.math.BigInteger.ONE)},
                {max.subtract(java.math.BigInteger.ONE), max}, {wide.negate(), wide.add(java.math.BigInteger.ONE)}}) {
            Int1088 low = Int1088.parseDecimal(pair[0].toString());
            Int1088 high = Int1088.parseDecimal(pair[1].toString());
            SectorAABB box = new SectorAABB(low, 0.25D, 10, low, 0.25D,
                    high, 0.75D, 12, high, 0.75D);
            java.math.BigInteger sum = pair[0].add(pair[1]);
            // Fractional average is 0.5, plus 0.5 if the integer sum is odd.
            java.math.BigInteger block = sum.shiftRight(1);
            double fraction = sum.testBit(0) ? 0.0D : 0.5D;
            if (sum.testBit(0)) block = block.add(java.math.BigInteger.ONE);
            SectorVec3 center = box.getCenter();
            assertEquals(Int1088.parseDecimal(block.toString()), center.blockX());
            assertEquals(center.blockX(), center.blockZ());
            assertEquals(fraction, center.subX());
            assertEquals(fraction, center.subZ());
            assertEquals(11.0D, center.y());
        }
    }

    @Test
    void edgeBoxesSaturateEndpointsAndInclusiveRanges() {
        SectorAABB box = SectorAABB.around(
                SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.75D, 64, Int1088.MIN, 0.25D), 2, 1);
        assertEquals(Int1088.MAX, box.maxBlockX());
        assertEquals(Math.nextDown(1.0D), box.maxSubX());
        assertEquals(Int1088.MIN, box.minBlockZ());
        assertEquals(0.0D, box.minSubZ());
        assertEquals(Int1088.MAX, box.maxBlockXForRangeInclusive());
        assertEquals(Int1088.MAX, box.maxBlockXForCollision());
        assertEquals(Int1088.MIN, box.minBlockZForCollision());
        assertEquals(1.25D, box.getXsize(), 1.0E-15D);
        assertEquals(1.25D, box.getZsize());
    }

    @Test
    void localConversionAcceptsLimitAndRejectsUnsafeSpansAndDistantOrigins() {
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
        SectorAABB limit = new SectorAABB(0L, 0.0D, 64.0D, 0L, 0.0D,
                WorldBounds.MAX_SAFE_LOCAL_SPAN, 0.0D, 65.0D, 0L, 0.5D);
        assertEquals((double) WorldBounds.MAX_SAFE_LOCAL_SPAN, limit.toLocalAABB(origin).maxX);
        SectorVec3 fractionalOrigin = SectorVec3.fromBlockAndFraction(0L, 0.0D, 64.0D, 0L, 0.0D);
        assertEquals(limit.toLocalAABB(origin), limit.toLocalAABB(fractionalOrigin));
        for (Int1088 far : new Int1088[] {Int1088.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L),
                Int1088.fromLong(1L << 62), Int1088.of(Long.MIN_VALUE, 0L), Int1088.ONE.shiftLeftSaturated(1024)}) {
            SectorAABB wide = new SectorAABB(Int1088.ZERO, 0.0D, 64.0D, Int1088.ZERO, 0.0D,
                    far, 0.0D, 65.0D, Int1088.ZERO, 0.5D);
            assertThrows(IllegalArgumentException.class, () -> wide.toLocalAABB(origin));
            assertThrows(IllegalArgumentException.class, () -> wide.toLocalAABB(fractionalOrigin));
            SectorAABB distant = SectorAABB.around(
                    SectorVec3.fromBlockAndFraction(far, 0.5D, 64.0D, far, 0.5D), 0.5D, 1.0D);
            assertThrows(IllegalArgumentException.class, () -> distant.toLocalAABB(origin));
        }
    }

    @Test
    void highWordSeamMovementAndCacheKeysRemainExact() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int1088.of(-1L, 9L), 0.75D,
                64.0D, Int1088.of(0L, -9L), 0.25D);
        SectorAABB box = SectorAABB.around(position, 0.5D, 1.0D);
        assertEquals(0.5D, box.toLocalAABB(SectorPhysicsOrigin.from(position)).maxZ);
        SectorAABB moved = box.move(1.0D, 0.0D, -1.0D);
        assertEquals(Int1088.of(0L, 10L), moved.minBlockX());
        assertEquals(box, moved.move(-1.0D, 0.0D, 1.0D));
        SectorAABB same = SectorAABB.around(position, 0.5D, 1.0D);
        assertEquals(box.hashCode(), same.hashCode());
        java.util.Map<SectorAABB, String> cache = new java.util.HashMap<>();
        cache.put(box, "hit");
        assertEquals("hit", cache.get(same));
        SectorAABB alias = SectorAABB.around(SectorVec3.fromBlockAndFraction(
                Int1088.of(-1L, 10L), 0.75D, 64.0D, position.blockZ(), 0.25D), 0.5D, 1.0D);
        assertNotEquals(box, alias);
        assertEquals(null, cache.get(alias));
    }

    @Test
    void compatibilitySettersPreserveUntouchedHugeEndpoints() {
        long huge = 1L << 60;
        AABB box = AABB.fromSectorBounds(new SectorAABB(huge, 0.25D, 64.0D, -huge, 0.5D,
                huge + 1, 0.75D, 66.0D, -huge + 1, 0.875D));
        SectorAABB changed = box.setMinY(63.0D).setMaxY(67.0D)
                .setMinX(box.minX).setMaxX(box.maxX)
                .setMinZ(box.minZ).setMaxZ(box.maxZ).getSectorBounds();
        assertEquals(Int1088.fromLong(huge), changed.minBlockX());
        assertEquals(0.25D, changed.minSubX());
        assertEquals(Int1088.fromLong(huge + 1), changed.maxBlockX());
        assertEquals(0.75D, changed.maxSubX());
        assertEquals(Int1088.fromLong(-huge), changed.minBlockZ());
        assertEquals(0.5D, changed.minSubZ());
        assertEquals(Int1088.fromLong(-huge + 1), changed.maxBlockZ());
        assertEquals(0.875D, changed.maxSubZ());
        assertEquals(63.0D, changed.minY());
        assertEquals(67.0D, changed.maxY());
        SectorAABB replaced = box.setMinX((double) huge - 256.0D).getSectorBounds();
        assertEquals(Int1088.fromLong(huge - 256), replaced.minBlockX());
        assertEquals(0.75D, replaced.maxSubX());
        assertEquals(0.5D, replaced.minSubZ());
    }

    @Test
    void blockFactoriesAndMergesRetainHugeExactBounds() {
        long huge = 1L << 60;
        AABB first = new AABB(new net.minecraft.core.BlockPos(huge, 64, -huge));
        AABB second = new AABB(new net.minecraft.core.BlockPos(huge + 1, 64, -huge));
        assertEquals(Int1088.fromLong(huge + 1), first.getSectorBounds().maxBlockX());
        assertEquals(Int1088.fromLong(-huge + 1), first.getSectorBounds().maxBlockZ());
        SectorAABB merged = first.minmax(second).getSectorBounds();
        assertEquals(Int1088.fromLong(huge), merged.minBlockX());
        assertEquals(Int1088.fromLong(huge + 2), merged.maxBlockX());
        SectorAABB intersection = first.inflate(0.5D).intersect(second).getSectorBounds();
        assertEquals(Int1088.fromLong(huge + 1), intersection.minBlockX());
        assertEquals(Int1088.fromLong(huge + 1), intersection.maxBlockX());
        assertEquals(0.5D, intersection.maxSubX());
        SectorAABB structure = AABB.of(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                huge, 64, -huge, huge + 2, 66, -huge + 2)).getSectorBounds();
        assertEquals(Int1088.fromLong(huge + 3), structure.maxBlockX());
        assertEquals(Int1088.fromLong(-huge + 3), structure.maxBlockZ());
    }

    @Test
    void blockFactoryClampsAtSignedInt1088Edge() {
        SectorAABB box = new AABB(new net.minecraft.core.BlockPos(Int1088.MAX, 64, Int1088.MIN))
                .getSectorBounds();
        assertEquals(Int1088.MAX, box.maxBlockX());
        assertEquals(Math.nextDown(1.0D), box.maxSubX());
        assertEquals(Int1088.MIN.add(Int1088.ONE), box.maxBlockZ());
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
        assertEquals(Int1088.fromLong(11L), box.minBlockX());
        assertEquals(0.0D, box.minSubX());
        assertEquals(Int1088.fromLong(12L), box.maxBlockX());
        assertEquals(0.0D, box.maxSubX());
        assertEquals(Int1088.fromLong(9L), box.minBlockXForCollision());
        assertEquals(Int1088.fromLong(13L), box.maxBlockXForCollision());
    }

    @Test
    void movementCarriesExactEndpoints() {
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(1L << 53, 0.9D, 4.0D,
                                                   1L << 53, 0.1D),
                                           0.6D, 1.8D)
                                 .move(0.3D, 2.0D, -0.3D);
        assertEquals(Int1088.fromLong((1L << 53) + 1L), box.maxBlockX());
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
        assertEquals(Int1088.fromLong(huge - 17L), transformed.minBlockX());
        assertEquals(0.95D, transformed.minSubX(), 1.0E-12D);
        assertEquals(Int1088.fromLong(huge + 761L), transformed.maxBlockZ());
        assertEquals(0.05D, transformed.maxSubZ(), 1.0E-12D);
    }
}
