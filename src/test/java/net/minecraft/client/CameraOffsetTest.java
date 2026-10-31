package net.minecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityLoadingTestLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CameraOffsetTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void crouchAndSwimCameraTransitionsInterpolateAtLowAndWideCoordinates() {
        PoseEntity entity = new PoseEntity();
        for (BigInteger origin : origins()) {
            SectorVec3 feet = SectorVec3.fromBlockAndFraction(value(origin), 0.25D, 64,
                    value(origin.negate()), 0.75D);
            entity.moveTo(feet, 0, 0);
            Camera camera = new Camera();
            float height = 0;
            camera.setup(null, entity, false, false, 0);
            for (Pose pose : new Pose[] {Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.STANDING}) {
                entity.setPose(pose);
                for (int tick = 0; tick < 24; ++tick) {
                    float oldHeight = height;
                    height += (entity.getEyeHeight() - height) * 0.5F;
                    camera.tick();
                    for (float partialTick : new float[] {0, 0.25F, 0.75F, 1}) {
                        camera.setup(null, entity, false, false, partialTick);
                        double expectedY = feet.y() + (double) (oldHeight + partialTick * (height - oldHeight));
                        assertEquals(feet.withY(expectedY), camera.getExactPosition(), pose.toString());
                    }
                }
            }
        }
    }

    @Test
    void localOffsetsMatchIndependentSubtractionAtEveryLimbSeam() {
        BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger max = min.negate().subtract(BigInteger.ONE);
        List<BigInteger> origins = origins();
        origins.add(min);
        origins.add(max);
        for (BigInteger origin : origins) {
            Int1088 x = value(origin);
            Int1088 z = value(origin.negate().min(max));
            CameraRelativePosition camera = CameraRelativePosition.of(
                    SectorVec3.fromBlockAndFraction(x, 0.25D, -63.5D, z, 0.75D));
            for (int offset : new int[] {-32, -1, 0, 1, 32}) {
                BigInteger targetX = origin.add(BigInteger.valueOf(offset)).max(min).min(max);
                BigInteger originZ = origin.negate().min(max);
                BigInteger targetZ = originZ.add(BigInteger.valueOf(offset)).max(min).min(max);
                double expectedX = targetX.subtract(origin).doubleValue() - 0.25D;
                double expectedZ = targetZ.subtract(originZ).doubleValue() - 0.75D;
                assertEquals(expectedX, camera.relativeX(value(targetX)));
                assertEquals(expectedZ, camera.relativeZ(value(targetZ)));
                assertEquals(-expectedX, camera.cameraRelativeToX(value(targetX)));
                assertEquals(-expectedZ, camera.cameraRelativeToZ(value(targetZ)));
            }
            assertEquals(-0.5D, camera.relativeY(-64));
        }
    }

    @Test
    void cameraMovementKeepsExactBlockAndFractionWithoutAbsoluteProjection() {
        for (BigInteger origin : origins()) {
            TestCamera camera = new TestCamera();
            camera.setPosition(SectorVec3.fromBlockAndFraction(value(origin), 0.75D, 64.5D,
                    value(origin.negate()), 0.25D));
            // Default basis: forwards +Z, up +Y, left +X.
            camera.move(-0.5D, 2.0D, 0.5D);
            SectorVec3 exact = camera.getExactPosition();
            assertEquals(value(origin.add(BigInteger.ONE)), exact.blockX());
            assertEquals(0.25D, exact.subX());
            assertEquals(value(origin.negate().subtract(BigInteger.ONE)), exact.blockZ());
            assertEquals(0.75D, exact.subZ());
            assertEquals(66.5D, exact.y());
            assertEquals(exact.blockPosition(), camera.getBlockPosition());
            assertEquals(0.25D, camera.getPosition().x);
            assertEquals(0.75D, camera.getPosition().z);
            assertTrue(Double.isFinite(camera.getPosition().x));
        }
    }

    private static List<BigInteger> origins() {
        List<BigInteger> result = new ArrayList<>();
        result.add(BigInteger.ZERO);
        for (int bit : new int[] {53, 63, 64, 127, 128, 192, 256, 320, 384, 448,
                512, 576, 640, 704, 768, 832, 896, 960, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger seam = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign));
                for (int delta : new int[] {-1, 0, 1})
                    result.add(seam.add(BigInteger.valueOf(delta)));
            }
        }
        return result;
    }

    private static Int1088 value(BigInteger value) {
        return Int1088.parseDecimal(value.toString());
    }

    private static final class TestCamera extends Camera {}

    private static final class PoseEntity extends Entity {
        PoseEntity() { super(EntityType.PLAYER, EntityLoadingTestLevel.create()); }
        @Override public EntityDimensions getDimensions(Pose pose) {
            return EntityDimensions.scalable(0.6F, pose == Pose.SWIMMING ? 0.6F : pose == Pose.CROUCHING ? 1.5F : 1.8F);
        }
        @Override protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
            return pose == Pose.SWIMMING ? 0.4F : pose == Pose.CROUCHING ? 1.27F : 1.62F;
        }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag tag) {}
        @Override protected void addAdditionalSaveData(CompoundTag tag) {}
        @Override public Packet<?> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    }
}
