package net.minecraft.client.renderer.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Int1088;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EntityRenderCoordinatesTest {
    @Test
    void shadowVerticesPreserveFractionsAcrossHighHalvesAndLowWordSeams() {
        for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 123),
                Int1088.of(Long.MAX_VALUE, -123), Int1088.MIN.add(Int1088.fromLong(8)),
                Int1088.MAX.subtract(Int1088.fromLong(8))}) {
            for (int offset = -4; offset <= 4; ++offset) {
                assertEquals(offset - 0.75D, EntityRenderDispatcher.shadowOffset(
                        origin.add(Int1088.fromLong(offset)), origin, 0.75D));
            }
        }
    }

    @Test
    void interpolatedBeamAndHitboxGeometryIsTranslationInvariant() {
        for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 123),
                Int1088.of(Long.MAX_VALUE, -123)}) {
            SectorVec3 old = SectorVec3.fromBlockAndFraction(origin, 0.75D, 64.0D, origin, 0.25D);
            SectorVec3 current = old.add(1.0D, 2.0D, -1.0D);
            SectorVec3 interpolated = old.lerpTo(current, 0.5D);
            Vec3 beam = current.add(4.0D, 0.5D, 3.0D).relativeTo(interpolated);
            assertEquals(4.5D, beam.x);
            assertEquals(1.5D, beam.y);
            assertEquals(2.5D, beam.z);
            Vec3 delta = interpolated.relativeTo(current);
            AABB box = SectorAABB.around(current, 1.0D, 2.0D)
                    .move(delta.x, delta.y, delta.z).toLocalAABB(interpolated);
            assertEquals(-0.5D, box.minX);
            assertEquals(0.5D, box.maxX);
            assertEquals(0.0D, box.minY);
            assertEquals(2.0D, box.maxY);
            assertEquals(-0.5D, box.minZ);
            assertEquals(0.5D, box.maxZ);
        }
    }
}
