package com.mojang.math;

import java.nio.FloatBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatrixQuaternionTest {
    private static final float[] SPECIAL = {0.0F, -0.0F, 1.0F, -1.0F,
            Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, -Float.MIN_NORMAL,
            Float.MAX_VALUE, -Float.MAX_VALUE, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NaN, Float.intBitsToFloat(0xffc12345)};

    // Frozen constructor coefficients, followed by an independent row-major product.
    private static float[] rotation(Quaternion q, int order) {
        float x = q.i(), y = q.j(), z = q.k(), w = q.r();
        float xx = 2.0F * x * x, yy = 2.0F * y * y, zz = 2.0F * z * z;
        float xy = x * y, yz = y * z, zx = z * x;
        float xw = x * w, yw = y * w, zw = z * w;
        float[] result = new float[order * order];
        result[0] = 1.0F - yy - zz;
        result[order + 1] = 1.0F - zz - xx;
        result[2 * order + 2] = 1.0F - xx - yy;
        result[order] = 2.0F * (xy + zw);
        result[1] = 2.0F * (xy - zw);
        result[2 * order] = 2.0F * (zx - yw);
        result[2] = 2.0F * (zx + yw);
        result[2 * order + 1] = 2.0F * (yz + xw);
        result[order + 2] = 2.0F * (yz - xw);
        if (order == 4) result[15] = 1.0F;
        return result;
    }

    private static float[] product(float[] a, float[] b, int order) {
        float[] result = new float[a.length];
        for (int row = 0; row < order; ++row) {
            for (int column = 0; column < order; ++column) {
                float sum = a[row * order] * b[column];
                for (int k = 1; k < order; ++k) {
                    sum += a[row * order + k] * b[k * order + column];
                }
                result[row * order + column] = sum;
            }
        }
        return result;
    }

    private static float[] values(Matrix3f matrix) {
        FloatBuffer buffer = FloatBuffer.allocate(9);
        matrix.storeTransposed(buffer);
        return buffer.array();
    }

    private static float[] values(Matrix4f matrix) {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        matrix.storeTransposed(buffer);
        return buffer.array();
    }

    private static void assertBits(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) {
            // NaN payload propagation is not specified by Java; no finite tolerance.
            assertEquals(Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual[i]), "element " + i);
        }
    }

    private static float[] quaternionValues(Quaternion q) {
        return new float[] {q.i(), q.j(), q.k(), q.r()};
    }

    private static void check(float[] input, Quaternion q, int order) {
        float[] before = quaternionValues(q);
        float[] expected = product(input, rotation(q, order), order);
        if (order == 3) {
            Matrix3f matrix = new Matrix3f();
            matrix.loadTransposed(FloatBuffer.wrap(input));
            matrix.mul(q);
            assertBits(expected, values(matrix));
        } else {
            Matrix4f matrix = new Matrix4f();
            matrix.loadTransposed(FloatBuffer.wrap(input));
            matrix.multiply(q);
            assertBits(expected, values(matrix));
        }
        assertBits(before, quaternionValues(q));
    }

    @Test
    void randomizedArbitraryMatricesAndQuaternionsMatchFrozenFormula() {
        Random random = new Random(0x26a771L);
        for (int order : new int[] {3, 4}) {
            for (int i = 0; i < 12000; ++i) {
                float[] input = new float[order * order];
                boolean raw = i % 2 == 0;
                for (int j = 0; j < input.length; ++j) input[j] = sample(random, raw);
                check(input, new Quaternion(sample(random, raw), sample(random, raw),
                        sample(random, raw), sample(random, raw)), order);
            }
        }
    }

    private static float sample(Random random, boolean raw) {
        return raw ? Float.intBitsToFloat(random.nextInt()) : (random.nextFloat() - 0.5F) * 8.0F;
    }

    @Test
    void axisZeroNonunitAndExceptionalValuesKeepEveryMultiplicationTerm() {
        for (int order : new int[] {3, 4}) {
            for (int offset = 0; offset < SPECIAL.length; ++offset) {
                float[] input = new float[order * order];
                for (int j = 0; j < input.length; ++j) input[j] = SPECIAL[(j + offset) % SPECIAL.length];
                for (float v : SPECIAL) {
                    check(input, new Quaternion(v, 0, 0, 1), order);
                    check(input, new Quaternion(0, v, 0, 1), order);
                    check(input, new Quaternion(0, 0, v, 1), order);
                    check(input, new Quaternion(v, v, v, v), order);
                    check(input, new Quaternion(-0.0F, 0, -0.0F, v), order);
                }
            }
        }
    }

    @Test
    void unitAndZeroQuaternionsDoNotAssumeAffineMatrices() {
        float halfRoot = (float) Math.sqrt(0.5D);
        Quaternion[] rotations = {Quaternion.ONE, new Quaternion(0, 0, 0, 0),
                new Quaternion(1, 0, 0, 0), new Quaternion(0, -1, 0, 0),
                new Quaternion(0, 0, 1, 0), new Quaternion(halfRoot, 0, 0, halfRoot),
                new Quaternion(0, halfRoot, 0, -halfRoot), new Quaternion(0.5F, -0.5F, 0.5F, -0.5F)};
        for (int order : new int[] {3, 4}) {
            float[] input = new float[order * order];
            for (int i = 0; i < input.length; ++i) input[i] = (i - 7) * 0.375F;
            for (Quaternion q : rotations) check(input, q, order);
        }
    }

    @Test
    void finiteChainsAndIndependentMatricesRemainBitExact() {
        Random random = new Random(0x21776L);
        Matrix3f a = new Matrix3f();
        Matrix4f b = new Matrix4f();
        a.setIdentity();
        b.setIdentity();
        float[] expected3 = values(a), expected4 = values(b);
        for (int i = 0; i < 2000; ++i) {
            Quaternion q = new Quaternion((random.nextFloat() - 0.5F) * 0.02F,
                    (random.nextFloat() - 0.5F) * 0.02F, (random.nextFloat() - 0.5F) * 0.02F, 0.9999F);
            expected3 = product(expected3, rotation(q, 3), 3);
            expected4 = product(expected4, rotation(q, 4), 4);
            a.mul(q);
            b.multiply(q);
            assertBits(expected3, values(a));
            assertBits(expected4, values(b));
        }
    }

    @Test
    void nullQuaternionLeavesDestinationUnchangedAndLaterCallWorks() {
        Matrix3f a = Matrix3f.createScaleMatrix(-0.0F, 2, Float.POSITIVE_INFINITY);
        Matrix4f b = Matrix4f.createTranslateMatrix(2, -3, 4);
        float[] before3 = values(a), before4 = values(b);
        assertThrows(NullPointerException.class, () -> a.mul((Quaternion) null));
        assertThrows(NullPointerException.class, () -> b.multiply((Quaternion) null));
        assertBits(before3, values(a));
        assertBits(before4, values(b));
        Quaternion q = new Quaternion(0.25F, -0.5F, 0.125F, 0.75F);
        a.mul(q);
        b.multiply(q);
        assertBits(product(before3, rotation(q, 3), 3), values(a));
        assertBits(product(before4, rotation(q, 4), 4), values(b));
    }

    @Test
    void generalMatrixSelfMultiplicationRetainsAliasingContract() {
        Random random = new Random(6812);
        for (int i = 0; i < 100; ++i) {
            float[] a = new float[9], b = new float[16];
            for (int j = 0; j < a.length; ++j) a[j] = sample(random, false);
            for (int j = 0; j < b.length; ++j) b[j] = sample(random, false);
            Matrix3f m3 = new Matrix3f();
            Matrix4f m4 = new Matrix4f();
            m3.loadTransposed(FloatBuffer.wrap(a));
            m4.loadTransposed(FloatBuffer.wrap(b));
            m3.mul(m3);
            m4.multiply(m4);
            assertBits(product(a, a, 3), values(m3));
            assertBits(product(b, b, 4), values(m4));
        }
    }
}
