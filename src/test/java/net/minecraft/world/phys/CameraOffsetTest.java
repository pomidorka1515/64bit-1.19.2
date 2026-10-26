package net.minecraft.world.phys;

import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraOffsetTest {
    @Test
    void cameraRaysCanLeaveAnEmptyWorldAtEitherEdge() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (Int128 edge : new Int128[] { Int128.MIN, Int128.MAX }) {
            double direction = edge.equals(Int128.MIN) ? -4.0D : 4.0D;
            SectorVec3 eye = SectorVec3.fromBlockAndFraction(edge, 0.5D, 80.0D, edge, 0.5D);
            assertEquals(Double.POSITIVE_INFINITY,
                    net.minecraft.world.level.SectorClipper.clipCameraDistance(
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, eye, Vec3.ZERO,
                            new Vec3(direction, 0.0D, direction), new TestEntity()));
        }
    }

    private static final class TestEntity extends net.minecraft.world.entity.Entity {
        TestEntity() { super(net.minecraft.world.entity.EntityType.PIG, null); }
        protected void defineSynchedData() {}
        protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
        protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
        public net.minecraft.network.protocol.Packet<?> getAddEntityPacket() { return null; }
    }

    @Test
    void cameraCanCrossBothEdgesWithoutMovingItsWorldAnchor() {
        SectorVec3 eye = SectorVec3.fromBlockAndFraction(Int128.MAX, 0.5D, 80.0D,
                Int128.MIN, 0.5D);
        SectorVec3 camera = eye.offsetCamera(4.0D, 1.0D, -4.0D);
        assertFalse(camera.isInsideWorld());
        assertEquals(new Vec3(4.0D, 1.0D, -4.0D), camera.relativeTo(eye));
        CameraRelativePosition render = CameraRelativePosition.of(camera);
        assertEquals(-4.5D, render.relativeX(Int128.MAX));
        assertEquals(3.5D, render.relativeZ(Int128.MIN));
        assertEquals(eye, camera.offsetCamera(-4.0D, -1.0D, 4.0D));
        assertTrue(eye.isInsideWorld());
        assertTrue(eye.add(4.0D, 0.0D, -4.0D).isInsideWorld());
    }

    @Test
    void ordinaryCameraMotionRemainsNormalized() {
        SectorVec3 eye = SectorVec3.fromBlockAndFraction(10L, 0.25D, 80.0D, -10L, 0.75D);
        assertEquals(eye.add(-4.0D, 2.0D, 4.0D), eye.offsetCamera(-4.0D, 2.0D, 4.0D));
    }
}
