package net.minecraft.client.renderer.culling;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FrustumTest {
    private static Matrix4f identity() {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        return matrix;
    }

    private static Matrix4f[] projections() {
        return new Matrix4f[] {identity(), Matrix4f.createScaleMatrix(0.002F, 0.004F, 0.003F),
                Matrix4f.perspective(73.0D, 1.7F, 0.05F, 512.0F)};
    }

    private static Int1088[] bases() {
        return new Int1088[] {Int1088.ZERO, Int1088.fromLong(-4096),
                Int1088.ONE.shiftLeftSaturated(160), Int1088.ONE.shiftLeftSaturated(160).negate(),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.ONE.shiftLeftSaturated(1024).negate(),
                Int1088.MIN.addSaturated(Int1088.fromLong(4096)),
                Int1088.MAX.subtractSaturated(Int1088.fromLong(4095))};
    }

    private static void prepare(Frustum frustum, Int1088 base) {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5D, 8.5D, base, 0.5D);
        frustum.prepare(new Camera() {
            @Override
            public SectorVec3 getExactPosition() { return position; }
        });
    }

    // Independently construct the frozen six planes, then test all eight corners.
    // Never call the optimized plane helper or coordinate conversion in the oracle.
    private static Vector4f[] planes(Matrix4f projection, Matrix4f view) {
        Matrix4f matrix = view.copy();
        matrix.multiply(projection);
        matrix.transpose();
        Vector4f[] planes = {new Vector4f(-1, 0, 0, 1), new Vector4f(1, 0, 0, 1),
                new Vector4f(0, -1, 0, 1), new Vector4f(0, 1, 0, 1),
                new Vector4f(0, 0, -1, 1), new Vector4f(0, 0, 1, 1)};
        for (Vector4f plane : planes) {
            plane.transform(matrix);
            plane.normalize();
        }
        return planes;
    }

    private static boolean reference(Vector4f[] planes, double x, double y, double z,
            double xx, double yy, double zz) {
        for (Vector4f plane : planes) {
            boolean inside = false;
            for (int corner = 0; corner < 8; ++corner) {
                float a = (float) ((corner & 1) == 0 ? x : xx);
                float b = (float) ((corner & 2) == 0 ? y : yy);
                float c = (float) ((corner & 4) == 0 ? z : zz);
                inside |= plane.x() * a + plane.y() * b + plane.z() * c + plane.w() > 0.0F;
            }
            if (!inside) return false;
        }
        return true;
    }

    @Test
    void randomizedPublicPathsMatchEightCornersAcrossExactWorldTranslations() {
        Random random = new Random(0x25f12a57L);
        for (Matrix4f projection : projections()) {
            Matrix4f view = Matrix4f.createTranslateMatrix(0.125F, -0.25F, 0.5F);
            Vector4f[] planes = planes(projection, view);
            for (Int1088 base : bases()) {
                Frustum frustum = new Frustum(projection, view);
                prepare(frustum, base);
                for (int i = 0; i < 1000; ++i) {
                    int x = random.nextInt(2049) - 1024;
                    int y = random.nextInt(513) - 256;
                    int z = random.nextInt(2049) - 1024;
                    int width = random.nextInt(65);
                    int height = random.nextInt(65);
                    int depth = random.nextInt(65);
                    Int1088 bx = base.addSaturated(Int1088.fromLong(x));
                    Int1088 bz = base.addSaturated(Int1088.fromLong(z));
                    SectorAABB box = new SectorAABB(bx, 0.25D, y + 0.25D, bz, 0.25D,
                            bx.addSaturated(Int1088.fromLong(width)), 0.75D, y + height + 0.75D,
                            bz.addSaturated(Int1088.fromLong(depth)), 0.75D);
                    boolean expected = reference(planes, x - 0.25D, y - 8.25D, z - 0.25D,
                            x + width + 0.25D, y + height - 7.75D, z + depth + 0.25D);
                    assertEquals(expected, frustum.isVisible(box));
                    assertEquals(expected, frustum.isVisible(AABB.fromSectorBounds(box)));
                    assertEquals(expected, frustum.isVisible(new BlockPos(bx, y, bz),
                            new AABB(0.25D, 0.25D, 0.25D, width + 0.75D, height + 0.75D, depth + 0.75D)));
                    // Aligned exact section origins, never world-space floating point.
                    int sx = x & -16;
                    int sz = z & -16;
                    BlockPos origin = new BlockPos(base.addSaturated(Int1088.fromLong(sx)), y,
                            base.addSaturated(Int1088.fromLong(sz)));
                    boolean section = reference(planes, sx - 0.5D, y - 8.5D, sz - 0.5D,
                            sx + 15.5D, y + 7.5D, sz + 15.5D);
                    assertEquals(section, frustum.isRenderSectionVisible(origin));
                    assertEquals(section, frustum.isChunkVisible(origin.getX().shiftRight(4), y,
                            origin.getZ().shiftRight(4)));
                }
            }
        }
    }

    @Test
    void legacyBoxesPreserveStrictBoundariesAndExceptionalInputs() {
        double[] values = {0.0D, -0.0D, -1.0D, 1.0D, Math.nextDown(1.0D), Math.nextUp(1.0D),
                -512.25D, 512.25D, Float.MAX_VALUE, -Float.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (Matrix4f projection : projections()) {
            Frustum frustum = new Frustum(projection, identity());
            frustum.prepare(0, 0, 0);
            Vector4f[] planes = planes(projection, identity());
            for (double a : values) for (double b : values) {
                AABB box = new AABB(a, -0.5D, b, b, 0.5D, a);
                // Frozen legacy endpoint conversion, including its non-finite behavior.
                double minX = legacyHorizontal(box.minX);
                double maxX = legacyHorizontal(box.maxX);
                double minZ = legacyHorizontal(box.minZ);
                double maxZ = legacyHorizontal(box.maxZ);
                assertEquals(reference(planes, minX, box.minY, minZ, maxX, box.maxY, maxZ),
                        frustum.isVisible(box));
            }
        }
    }

    private static double legacyHorizontal(double coordinate) {
        long block = net.minecraft.util.Mth.lfloor(coordinate);
        return (double) block + coordinate - (double) block;
    }

    @Test
    void copyPrepareAndNoOpOffsetKeepIndependentCameraSnapshots() {
        for (Int1088 base : bases()) {
            Frustum original = new Frustum(identity(), identity());
            prepare(original, base);
            Frustum copy = new Frustum(original);
            BlockPos origin = new BlockPos(base, 8, base);
            AABB local = new AABB(0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D);
            assertEquals(true, copy.isVisible(origin, local));
            for (int step : new int[] {0, -1, 1, 8, 16, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertSame(copy, copy.offsetToFullyIncludeCameraCube(step));
                assertEquals(true, copy.isVisible(origin, local));
            }
            original.prepare(2048, 2048, 2048);
            assertEquals(false, original.isVisible(origin, local));
            assertEquals(true, copy.isVisible(origin, local));
            copy.prepare(-2048, -2048, -2048);
            prepare(original, base);
            assertEquals(true, original.isVisible(origin, local));
            assertEquals(false, copy.isVisible(origin, local));
        }
    }
}
