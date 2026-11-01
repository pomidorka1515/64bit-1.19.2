package com.mojang.blaze3d.vertex;

import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import java.nio.FloatBuffer;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoseStackTest {
    private static void assertBits(Matrix4f expected, Matrix4f actual) {
        FloatBuffer a = FloatBuffer.allocate(16), b = FloatBuffer.allocate(16);
        expected.store(a);
        actual.store(b);
        for (int i = 0; i < 16; ++i) assertEquals(Float.floatToIntBits(a.get(i)), Float.floatToIntBits(b.get(i)));
    }

    private static void assertBits(Matrix3f expected, Matrix3f actual) {
        FloatBuffer a = FloatBuffer.allocate(9), b = FloatBuffer.allocate(9);
        expected.store(a);
        actual.store(b);
        for (int i = 0; i < 9; ++i) assertEquals(Float.floatToIntBits(a.get(i)), Float.floatToIntBits(b.get(i)));
    }

    @Test
    void rotationsMatchFrozenConstructorProductsForBothPoseMatrices() {
        PoseStack stack = new PoseStack();
        Random random = new Random(0x26b123L);
        for (int i = 0; i < 2000; ++i) {
            stack.setIdentity();
            stack.translate(random.nextDouble(), -2, 3);
            stack.scale(0.5F, -2, 3);
            Matrix4f expected4 = stack.last().pose().copy();
            Matrix3f expected3 = stack.last().normal().copy();
            stack.pushPose();
            Quaternion q = new Quaternion(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat());
            expected4.multiply(new Matrix4f(q));
            expected3.mul(new Matrix3f(q));
            stack.mulPose(q);
            assertBits(expected4, stack.last().pose());
            assertBits(expected3, stack.last().normal());
            stack.popPose();
            assertTrue(stack.clear());
        }
    }

    @Test
    void retainedPoppedPosesAndMatricesNeverBecomeScratch() {
        PoseStack stack = new PoseStack(), other = new PoseStack();
        PoseStack.Pose root = stack.last();
        for (int depth = 0; depth < 40; ++depth) stack.pushPose();
        PoseStack.Pose retained = stack.last();
        stack.translate(3, 4, 5);
        stack.scale(1, -2, 3);
        Matrix4f position = retained.pose(), positionCopy = position.copy();
        Matrix3f normal = retained.normal(), normalCopy = normal.copy();
        for (int depth = 0; depth < 40; ++depth) stack.popPose();
        assertSame(root, stack.last());
        for (int i = 0; i < 100; ++i) {
            stack.pushPose();
            assertNotSame(retained, stack.last());
            assertNotSame(position, stack.last().pose());
            assertNotSame(normal, stack.last().normal());
            stack.mulPose(new Quaternion(0.1F, -0.3F, 0.5F, 0.7F));
            other.mulPose(new Quaternion(0.4F, 0.2F, -0.1F, 0.9F));
            stack.setIdentity();
            stack.popPose();
        }
        assertBits(positionCopy, position);
        assertBits(normalCopy, normal);
        Matrix4f identity4 = new Matrix4f();
        Matrix3f identity3 = new Matrix3f();
        identity4.setIdentity();
        identity3.setIdentity();
        assertBits(identity4, root.pose());
        assertBits(identity3, root.normal());
    }

    @Test
    void nullRotationAndUnderflowKeepExistingRecoveryBehavior() {
        PoseStack stack = new PoseStack();
        PoseStack.Pose root = stack.last();
        Matrix4f position = root.pose().copy();
        Matrix3f normal = root.normal().copy();
        assertThrows(NullPointerException.class, () -> stack.mulPose(null));
        assertBits(position, root.pose());
        assertBits(normal, root.normal());
        stack.pushPose();
        stack.popPose();
        assertSame(root, stack.last());
        stack.popPose();
        assertFalse(stack.clear());
        assertThrows(NoSuchElementException.class, stack::last);
        assertThrows(NoSuchElementException.class, stack::pushPose);
        assertThrows(NoSuchElementException.class, stack::popPose);
        assertThrows(NoSuchElementException.class, stack::setIdentity);
    }
}
