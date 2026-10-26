package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorAABBTest {
    @Test
    void localConversionAcceptsLimitAndRejectsUnsafeSpansAndDistantOrigins() {
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
        SectorAABB limit = new SectorAABB(0L, 0.0D, 64.0D, 0L, 0.0D,
                WorldBounds.MAX_SAFE_LOCAL_SPAN, 0.0D, 65.0D, 0L, 0.5D);
        assertEquals((double) WorldBounds.MAX_SAFE_LOCAL_SPAN, limit.toLocalAABB(origin).maxX);
        SectorVec3 fractionalOrigin = SectorVec3.fromBlockAndFraction(0L, 0.0D, 64.0D, 0L, 0.0D);
        assertEquals(limit.toLocalAABB(origin), limit.toLocalAABB(fractionalOrigin));
        for (Int128 far : new Int128[] {Int128.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L),
                Int128.fromLong(1L << 62), Int128.of(0L, Long.MIN_VALUE), Int128.ONE.shiftLeftSaturated(100)}) {
            SectorAABB wide = new SectorAABB(Int128.ZERO, 0.0D, 64.0D, Int128.ZERO, 0.0D,
                    far, 0.0D, 65.0D, Int128.ZERO, 0.5D);
            assertThrows(IllegalArgumentException.class, () -> wide.toLocalAABB(origin));
            assertThrows(IllegalArgumentException.class, () -> wide.toLocalAABB(fractionalOrigin));
            SectorAABB distant = SectorAABB.around(
                    SectorVec3.fromBlockAndFraction(far, 0.5D, 64.0D, far, 0.5D), 0.5D, 1.0D);
            assertThrows(IllegalArgumentException.class, () -> distant.toLocalAABB(origin));
        }
    }

    @Test
    void highWordSeamMovementAndCacheKeysRemainExact() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.of(9L, -1L), 0.75D,
                64.0D, Int128.of(-9L, 0L), 0.25D);
        SectorAABB box = SectorAABB.around(position, 0.5D, 1.0D);
        assertEquals(0.5D, box.toLocalAABB(SectorPhysicsOrigin.from(position)).maxZ);
        SectorAABB moved = box.move(1.0D, 0.0D, -1.0D);
        assertEquals(Int128.of(10L, 0L), moved.minBlockX());
        assertEquals(box, moved.move(-1.0D, 0.0D, 1.0D));
        SectorAABB same = SectorAABB.around(position, 0.5D, 1.0D);
        assertEquals(box.hashCode(), same.hashCode());
        java.util.Map<SectorAABB, String> cache = new java.util.HashMap<>();
        cache.put(box, "hit");
        assertEquals("hit", cache.get(same));
        SectorAABB alias = SectorAABB.around(SectorVec3.fromBlockAndFraction(
                Int128.of(10L, -1L), 0.75D, 64.0D, position.blockZ(), 0.25D), 0.5D, 1.0D);
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
        assertEquals(Int128.fromLong(huge), changed.minBlockX());
        assertEquals(0.25D, changed.minSubX());
        assertEquals(Int128.fromLong(huge + 1), changed.maxBlockX());
        assertEquals(0.75D, changed.maxSubX());
        assertEquals(Int128.fromLong(-huge), changed.minBlockZ());
        assertEquals(0.5D, changed.minSubZ());
        assertEquals(Int128.fromLong(-huge + 1), changed.maxBlockZ());
        assertEquals(0.875D, changed.maxSubZ());
        assertEquals(63.0D, changed.minY());
        assertEquals(67.0D, changed.maxY());
        SectorAABB replaced = box.setMinX((double) huge - 256.0D).getSectorBounds();
        assertEquals(Int128.fromLong(huge - 256), replaced.minBlockX());
        assertEquals(0.75D, replaced.maxSubX());
        assertEquals(0.5D, replaced.minSubZ());
    }

    @Test
    void blockFactoriesAndMergesRetainHugeExactBounds() {
        long huge = 1L << 60;
        AABB first = new AABB(new net.minecraft.core.BlockPos(huge, 64, -huge));
        AABB second = new AABB(new net.minecraft.core.BlockPos(huge + 1, 64, -huge));
        assertEquals(Int128.fromLong(huge + 1), first.getSectorBounds().maxBlockX());
        assertEquals(Int128.fromLong(-huge + 1), first.getSectorBounds().maxBlockZ());
        SectorAABB merged = first.minmax(second).getSectorBounds();
        assertEquals(Int128.fromLong(huge), merged.minBlockX());
        assertEquals(Int128.fromLong(huge + 2), merged.maxBlockX());
        SectorAABB intersection = first.inflate(0.5D).intersect(second).getSectorBounds();
        assertEquals(Int128.fromLong(huge + 1), intersection.minBlockX());
        assertEquals(Int128.fromLong(huge + 1), intersection.maxBlockX());
        assertEquals(0.5D, intersection.maxSubX());
        SectorAABB structure = AABB.of(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                huge, 64, -huge, huge + 2, 66, -huge + 2)).getSectorBounds();
        assertEquals(Int128.fromLong(huge + 3), structure.maxBlockX());
        assertEquals(Int128.fromLong(-huge + 3), structure.maxBlockZ());
    }

    @Test
    void blockFactoryClampsAtSignedInt128Edge() {
        SectorAABB box = new AABB(new net.minecraft.core.BlockPos(Int128.MAX, 64, Int128.MIN))
                .getSectorBounds();
        assertEquals(Int128.MAX, box.maxBlockX());
        assertEquals(Math.nextDown(1.0D), box.maxSubX());
        assertEquals(Int128.MIN.add(Int128.ONE), box.maxBlockZ());
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
        assertEquals(Int128.fromLong(11L), box.minBlockX());
        assertEquals(0.0D, box.minSubX());
        assertEquals(Int128.fromLong(12L), box.maxBlockX());
        assertEquals(0.0D, box.maxSubX());
        assertEquals(Int128.fromLong(9L), box.minBlockXForCollision());
        assertEquals(Int128.fromLong(13L), box.maxBlockXForCollision());
    }

    @Test
    void movementCarriesExactEndpoints() {
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(1L << 53, 0.9D, 4.0D,
                                                   1L << 53, 0.1D),
                                           0.6D, 1.8D)
                                 .move(0.3D, 2.0D, -0.3D);
        assertEquals(Int128.fromLong((1L << 53) + 1L), box.maxBlockX());
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
        assertEquals(Int128.fromLong(huge - 17L), transformed.minBlockX());
        assertEquals(0.95D, transformed.minSubX(), 1.0E-12D);
        assertEquals(Int128.fromLong(huge + 761L), transformed.maxBlockZ());
        assertEquals(0.05D, transformed.maxSubZ(), 1.0E-12D);
    }
}
