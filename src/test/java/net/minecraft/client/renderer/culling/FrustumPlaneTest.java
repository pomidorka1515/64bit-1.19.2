package net.minecraft.client.renderer.culling;

import com.mojang.math.Vector4f;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FrustumPlaneTest {
    @Test
    void supportVertexMatchesEightCorners() {
        Random random = new Random(0xf12a57);
        for (int i = 0; i < 20000; ++i) {
            Vector4f plane = new Vector4f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1,
                    random.nextFloat() * 2 - 1, random.nextFloat() * 64 - 32);
            float x = random.nextFloat() * 1024 - 512;
            float y = random.nextFloat() * 1024 - 512;
            float z = random.nextFloat() * 1024 - 512;
            check(plane, x, y, z, x + random.nextFloat() * 64,
                    y + random.nextFloat() * 64, z + random.nextFloat() * 64);
        }
    }

    @Test
    void exceptionalAndBoundaryInputsKeepOriginalStrictComparison() {
        float[] values = {0, -0.0F, 1, -1, Float.MIN_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
        for (float value : values) {
            for (float coefficient : values) {
                Vector4f plane = new Vector4f(coefficient, -1, 0, value);
                check(plane, value, -1, -1, 1, value, 1);
                check(plane, -1, -1, -1, value, 1, value);
                check(plane, 0, 0, 0, 0, 0, 0);
            }
        }
    }

    private static void check(Vector4f plane, float x, float y, float z, float xx, float yy, float zz) {
        boolean original = false;
        for (int corner = 0; corner < 8; ++corner) {
            float a = (corner & 1) == 0 ? x : xx;
            float b = (corner & 2) == 0 ? y : yy;
            float c = (corner & 4) == 0 ? z : zz;
            original |= plane.x() * a + plane.y() * b + plane.z() * c + plane.w() > 0.0F;
        }
        assertEquals(original, Frustum.planeIntersectsBox(plane, x, y, z, xx, yy, zz));
    }
}
