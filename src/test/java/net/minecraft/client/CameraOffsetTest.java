package net.minecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class CameraOffsetTest {
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
}
