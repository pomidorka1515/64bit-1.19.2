package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class FrustumInt128Test {
    @Test
    void translatedExactBoxesAndTheirMirrorsCullIdentically() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        for (Int128 base : new Int128[] {Int128.ZERO, Int128.of(17, -1), Int128.of(-17, 0)}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5D, 0.5D, base, 0.5D);
            Camera camera = new Camera() {
                @Override
                public SectorVec3 getExactPosition() { return position; }
            };
            Frustum frustum = new Frustum(identity, identity);
            frustum.prepare(camera);
            SectorAABB near = new SectorAABB(base, 0.25D, 0.25D, base, 0.25D,
                    base, 0.75D, 0.75D, base, 0.75D);
            assertTrue(frustum.isVisible(near));
            assertEquals(frustum.isVisible(near), frustum.isVisible(AABB.fromSectorBounds(near)));
            SectorAABB far = near.move(32.0D, 0.0D, 32.0D);
            assertEquals(false, frustum.isVisible(far));
            assertEquals(frustum.isVisible(far), frustum.isVisible(AABB.fromSectorBounds(far)));
        }
    }
}
