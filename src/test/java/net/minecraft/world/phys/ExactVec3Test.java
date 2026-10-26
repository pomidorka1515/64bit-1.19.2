package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

class ExactVec3Test {
    @Test
    void blockFactoriesAndPositionInterfacesRetainFullWidthCoordinates() {
        Int128 huge = Int128.of(1L << 36, 37L);
        BlockPos block = new BlockPos(huge, 10, huge.negate());
        Vec3 center = Vec3.atCenterOf(block);
        assertEquals(block, new BlockPos((net.minecraft.core.Position) center));
        assertEquals(net.minecraft.core.SectionPos.of(block), net.minecraft.core.SectionPos.of(center));
        assertEquals(0.0D, block.distToCenterSqr(center));
        assertEquals(0.5D, center.distanceTo(center.add(0.5D, 0.0D, 0.0D)));
        assertEquals(new Vec3(0.5D, 0.0D, 0.0D), center.vectorTo(center.add(0.5D, 0.0D, 0.0D)));
        assertEquals(center.add(0.25D, 0.0D, 0.0D).exactPosition(),
                center.lerp(center.add(0.5D, 0.0D, 0.0D), 0.5D).exactPosition());
        assertEquals(center.add(0.25D, 0.0D, 0.0D).exactPosition(),
                new Vec3(0.25D, 0.0D, 0.0D).add(center).exactPosition());
        AABB box = new AABB(block);
        assertTrue(box.contains(center));
        assertFalse(box.contains(center.add(2.0D, 0.0D, 0.0D)));
        assertTrue(box.intersects(center, center));
        assertFalse(box.intersects(center.add(2.0D, 0.0D, 0.0D), center.add(2.0D, 0.0D, 0.0D)));
        Vec3 hit = box.clip(center.add(-2.0D, 0.0D, 0.0D), center.add(2.0D, 0.0D, 0.0D)).orElseThrow();
        assertEquals(0.0D, hit.exactPosition().subX());
        assertEquals(huge, hit.exactPosition().blockX());
    }

    @Test
    void splitPositionCodecRetainsFractionsAndHighWords() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.of(-7L, 25L), 0.75D,
                10.5D, Int128.of(5L, -1L), 0.125D);
        var encoded = SectorVec3.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, position).result().orElseThrow();
        assertEquals(position, SectorVec3.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void compatibilityOriginsRetainBlocksOffsetsAndDistances() {
        Int128 huge = Int128.of(1L << 36, -1L);
        Vec3 origin = new ExactVec3(SectorVec3.fromBlockAndFraction(huge, 0.75D, 12.0D, huge.negate(), 0.25D));
        Vec3 moved = origin.add(0.5D, 2.0D, -0.5D);
        assertEquals(new BlockPos(huge.add(Int128.ONE), 14, huge.negate().subtract(Int128.ONE)), new BlockPos(moved));
        assertEquals(new Vec3(0.5D, 2.0D, -0.5D), moved.subtract(origin));
        assertEquals(4.5D, origin.distanceToSqr(moved));
        assertEquals(ExactVec3.position(moved), SectorVec3.fromApproximate(moved));
    }

    @Test
    void sectionIntersectionDistinguishesBoxesWithIdenticalDoubleMirrors() {
        Int128 huge = Int128.of(1L << 36, 37L);
        AABB query = new AABB(new BlockPos(huge, 10, huge));
        AABB overlapping = AABB.fromSectorBounds(SectorAABB.around(
                SectorVec3.fromBlockAndFraction(huge, 0.5D, 10.0D, huge, 0.5D), 0.5D, 1.0D));
        AABB separated = overlapping.move(3.0D, 0.0D, 0.0D);
        assertTrue(query.intersects(overlapping));
        assertTrue(overlapping.intersects(query));
        assertFalse(query.intersects(separated));
        assertFalse(separated.intersects(query));
        assertEquals(1.0D, query.getXsize());
        assertEquals(1.0D, query.getZsize());
        assertEquals(0.5D, overlapping.getXsize());
    }
}
