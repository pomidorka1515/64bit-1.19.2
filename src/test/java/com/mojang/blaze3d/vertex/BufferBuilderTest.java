package com.mojang.blaze3d.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

class BufferBuilderTest {
    private static final Field BUFFER = field("buffer");
    private static final Field NEXT_BYTE = field("nextElementByte");
    private static final int GROWTH = 2097152;
    private static volatile long checksum;

    @Test
    void identityQuadMatchesExplicitLayoutBytes() {
        for (VertexFormat format : new VertexFormat[] {DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY}) {
            try (Pair pair = new Pair(1024)) {
                pair.begin(VertexFormat.Mode.QUADS, format);
                int[] data = new int[32];
                ByteBuffer expected = ByteBuffer.allocate(4 * format.getVertexSize()).order(ByteOrder.nativeOrder());
                for (int i = 0; i < 4; i++) {
                    data[i * 8] = Float.floatToRawIntBits(i + 1.0F);
                    data[i * 8 + 1] = Float.floatToRawIntBits(2.0F);
                    data[i * 8 + 2] = Float.floatToRawIntBits(3.0F);
                    data[i * 8 + 4] = Float.floatToRawIntBits(0.125F);
                    data[i * 8 + 5] = Float.floatToRawIntBits(0.75F);
                    expected.putFloat(i + 1.0F).putFloat(2.0F).putFloat(3.0F);
                    expected.put((byte) 127).put((byte) 63).put((byte) 255).put((byte) 255);
                    expected.putFloat(0.125F).putFloat(0.75F);
                    if (format == DefaultVertexFormat.NEW_ENTITY) expected.putShort((short) 0x5678).putShort((short) 0x1234);
                    expected.putShort((short) 0xcdef).putShort((short) 0x89ab);
                    expected.put((byte) 0).put((byte) 127).put((byte) 0).put((byte) 0x5a);
                }
                BakedQuad quad = new BakedQuad(data, -1, Direction.UP, null, true);
                pair.actual.putBulkData(pose(0).last(), quad, 0.5F, 0.25F, 1.0F, 0x89abcdef, 0x12345678);
                BufferBuilder.RenderedBuffer rendered = pair.actual.end();
                assertArrayEquals(expected.array(), bytes(rendered.vertexBuffer()));
                rendered.release();
                pair.reference.end().release();
            }
        }
    }

    @Test
    void randomizedBulkWritesMatchFrozenReference() {
        Random random = new Random(0x2101088L);
        VertexFormat[] formats = {DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY,
            DefaultVertexFormat.POSITION_COLOR, DefaultVertexFormat.POSITION_COLOR_NORMAL,
            DefaultVertexFormat.POSITION_TEX};
        for (VertexFormat format : formats) {
            try (Pair pair = new Pair(4096)) {
                pair.begin(VertexFormat.Mode.QUADS, format);
                for (int i = 0; i < 96; i++) {
                    PoseStack pose = pose(i);
                    BakedQuad quad = quad(random, 32, Direction.values()[i % 6]);
                    float[] brightness = {random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()};
                    int[] lights = {random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()};
                    float red = random.nextFloat() * 3.0F - 1.0F;
                    float green = random.nextFloat() * 3.0F - 1.0F;
                    float blue = random.nextFloat() * 3.0F - 1.0F;
                    int overlay = random.nextInt();
                    if (i % 3 == 0) {
                        pair.actual.putBulkData(pose.last(), quad, red, green, blue, lights[0], overlay);
                        pair.reference.putBulkData(pose.last(), quad, red, green, blue, lights[0], overlay);
                    } else {
                        pair.actual.putBulkData(pose.last(), quad, brightness, red, green, blue, lights, overlay, i % 2 == 0);
                        pair.reference.putBulkData(pose.last(), quad, brightness, red, green, blue, lights, overlay, i % 2 == 0);
                    }
                }
                pair.compareAndRelease(false);
            }
        }
    }

    @Test
    void nonfiniteValuesSignedZerosAndColorRoundingMatch() {
        float[] values = {0.0F, -0.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -1.0F, 1.0F / 255.0F,
            Math.nextDown(1.0F / 255.0F), Math.nextUp(1.0F / 255.0F)};
        for (VertexFormat format : new VertexFormat[] {DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY}) {
            try (Pair pair = new Pair(1024)) {
                pair.begin(VertexFormat.Mode.QUADS, format);
                for (int i = 0; i < values.length; i++) {
                    int[] data = new int[32];
                    for (int j = 0; j < 4; j++) {
                        data[j * 8] = Float.floatToRawIntBits(values[i]);
                        data[j * 8 + 1] = Float.floatToRawIntBits(values[(i + 1) % values.length]);
                        data[j * 8 + 2] = Float.floatToRawIntBits(values[(i + 2) % values.length]);
                        data[j * 8 + 3] = 0x807f01ff;
                        data[j * 8 + 4] = Float.floatToRawIntBits(-0.0F);
                        data[j * 8 + 5] = 0x7fc00000;
                    }
                    BakedQuad quad = new BakedQuad(data, -1, Direction.UP, null, true);
                    float[] brightness = {values[i], 1.0F, -0.0F, values[(i + 3) % values.length]};
                    int[] lights = {0, -1, 0x80007fff, 0x12345678};
                    PoseStack.Pose pose = pose(i).last();
                    for (boolean tint : new boolean[] {false, true}) {
                        pair.actual.putBulkData(pose, quad, brightness, values[i], -0.0F, 1.0F, lights, -1, tint);
                        pair.reference.putBulkData(pose, quad, brightness, values[i], -0.0F, 1.0F, lights, -1, tint);
                    }
                }
                pair.compareAndRelease(false);
            }
        }
    }

    @Test
    void growthMilestonesAndSpareVertexAreUnchanged() {
        try (Pair pair = new Pair(6)) {
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            assertEquals(36, buffer(pair.actual).capacity());
            BakedQuad quad = quad(new Random(1), 32, Direction.NORTH);
            for (int i = 0; i < 16400; i++) {
                pair.actual.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0);
                pair.reference.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0);
                int expected = (i + 1) * 128 + 32 <= 36 + GROWTH ? 36 + GROWTH : 36 + 2 * GROWTH;
                assertEquals(expected, buffer(pair.actual).capacity(), "quad " + i);
                assertEquals(expected, buffer(pair.reference).capacity(), "reference quad " + i);
            }
            pair.compareAndRelease(true);
        }
    }

    @Test
    void linesAndSortedQuadsRetainIdenticalVerticesAndIndices() {
        for (VertexFormat.Mode mode : new VertexFormat.Mode[] {VertexFormat.Mode.LINES,
            VertexFormat.Mode.LINE_STRIP, VertexFormat.Mode.QUADS}) {
            try (Pair pair = new Pair(1024)) {
                pair.begin(mode, DefaultVertexFormat.NEW_ENTITY);
                for (int i = 0; i < 8; i++) {
                    BakedQuad quad = quad(new Random(i), 32, Direction.DOWN);
                    pair.actual.putBulkData(pose(i).last(), quad, 0.3F, 0.7F, 0.9F, i, -i);
                    pair.reference.putBulkData(pose(i).last(), quad, 0.3F, 0.7F, 0.9F, i, -i);
                }
                if (mode == VertexFormat.Mode.QUADS) {
                    pair.actual.setQuadSortOrigin(-7, 3, 11);
                    pair.reference.setQuadSortOrigin(-7, 3, 11);
                }
                pair.compareAndRelease(false);
            }
        }
    }

    @Test
    void retainedBatchesFormatSwitchesDiscardAndReuseMatch() {
        try (Pair pair = new Pair(1024)) {
            BufferBuilder.RenderedBuffer[] actual = new BufferBuilder.RenderedBuffer[4];
            BufferBuilder.RenderedBuffer[] reference = new BufferBuilder.RenderedBuffer[4];
            for (int i = 0; i < 4; i++) {
                pair.begin(VertexFormat.Mode.QUADS, i % 2 == 0 ? DefaultVertexFormat.BLOCK : DefaultVertexFormat.NEW_ENTITY);
                BakedQuad quad = quad(new Random(i), 32, Direction.EAST);
                pair.actual.putBulkData(pose(i).last(), quad, 1, 0.5F, 0, i, -1);
                pair.reference.putBulkData(pose(i).last(), quad, 1, 0.5F, 0, i, -1);
                actual[i] = pair.actual.end();
                reference[i] = pair.reference.end();
            }
            for (int i : new int[] {2, 0, 3, 1}) {
                compare(actual[i], reference[i], false);
                actual[i].release();
                reference[i].release();
            }
            assertThrows(IllegalStateException.class, actual[0]::release);
            assertThrows(IllegalStateException.class, pair.actual::end);
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            assertThrows(IllegalStateException.class, () -> pair.actual.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK));
            assertNull(pair.actual.endOrDiscardIfEmpty());
            assertNull(pair.reference.endOrDiscardIfEmpty());
            pair.actual.discard();
            pair.reference.discard();
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            pair.compareAndRelease(false);
        }
    }

    @Test
    void malformedInputsAndDefaultColorKeepExceptionAndPartialWriteBehavior() {
        for (int length : new int[] {0, 7, 8, 24, 31, 32, 33, 40}) {
            for (int arrayLength : new int[] {0, 3, 4}) {
                try (Pair pair = new Pair(1024)) {
                    pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                    BakedQuad quad = quad(new Random(1), length, Direction.SOUTH);
                    float[] brightness = new float[arrayLength];
                    int[] lights = new int[arrayLength];
                    assertSameOutcome(pair,
                        () -> pair.actual.putBulkData(pose(0).last(), quad, brightness, 1, 1, 1, lights, 0, true),
                        () -> pair.reference.putBulkData(pose(0).last(), quad, brightness, 1, 1, 1, lights, 0, true));
                    pair.compareAndRelease(false);
                }
            }
        }
        for (boolean defaultColor : new boolean[] {false, true}) {
            for (BakedQuad quad : new BakedQuad[] {null, new BakedQuad(null, -1, Direction.UP, null, true),
                quad(new Random(1), 32, Direction.UP), new BakedQuad(new int[32], -1, null, null, true)}) {
                try (Pair pair = new Pair(1024)) {
                    pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                    if (defaultColor) {
                        pair.actual.defaultColor(10, 20, 30, 40);
                        pair.reference.defaultColor(10, 20, 30, 40);
                    }
                    assertSameOutcome(pair,
                        () -> pair.actual.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0),
                        () -> pair.reference.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0));
                    pair.compareAndRelease(false);
                }
            }
        }
        try (Pair pair = new Pair(1024)) {
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            BakedQuad quad = quad(new Random(1), 32, Direction.UP);
            assertSameOutcome(pair,
                () -> pair.actual.putBulkData(null, quad, new float[4], 1, 1, 1, null, 0, false),
                () -> pair.reference.putBulkData(null, quad, new float[4], 1, 1, 1, null, 0, false));
            pair.compareAndRelease(false);
        }
        for (VertexFormat format : new VertexFormat[] {DefaultVertexFormat.PARTICLE, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL}) {
            try (Pair pair = new Pair(1024)) {
                pair.begin(VertexFormat.Mode.QUADS, format);
                BakedQuad quad = quad(new Random(3), 32, Direction.UP);
                assertSameOutcome(pair,
                    () -> pair.actual.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0),
                    () -> pair.reference.putBulkData(pose(0).last(), quad, 1, 1, 1, 0, 0));
                assertArrayEquals(Arrays.copyOf(bytes(buffer(pair.reference)), offset(pair.reference)),
                    Arrays.copyOf(bytes(buffer(pair.actual)), offset(pair.actual)));
            }
        }
    }

    @Test
    void sortedIndexOnlyRestoreRetainsBatchOffsets() {
        try (Pair pair = new Pair(1024)) {
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int i = 0; i < 4; i++) {
                BakedQuad quad = quad(new Random(i), 32, Direction.NORTH);
                pair.actual.putBulkData(pose(i).last(), quad, 1, 1, 1, 0, 0);
                pair.reference.putBulkData(pose(i).last(), quad, 1, 1, 1, 0, 0);
            }
            pair.actual.setQuadSortOrigin(0, 0, 0);
            pair.reference.setQuadSortOrigin(0, 0, 0);
            BufferBuilder.SortState actualState = pair.actual.getSortState();
            BufferBuilder.SortState referenceState = pair.reference.getSortState();
            BufferBuilder.RenderedBuffer actualVertices = pair.actual.end();
            BufferBuilder.RenderedBuffer referenceVertices = pair.reference.end();
            compare(actualVertices, referenceVertices, false);
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            pair.actual.restoreSortState(actualState);
            pair.reference.restoreSortState(referenceState);
            pair.actual.setQuadSortOrigin(15, -3, 2);
            pair.reference.setQuadSortOrigin(15, -3, 2);
            BufferBuilder.RenderedBuffer actualIndices = pair.actual.end();
            BufferBuilder.RenderedBuffer referenceIndices = pair.reference.end();
            assertTrue(actualIndices.drawState().indexOnly());
            assertEquals(referenceIndices.drawState(), actualIndices.drawState());
            assertArrayEquals(bytes(referenceIndices.indexBuffer()), bytes(actualIndices.indexBuffer()));
            actualVertices.release();
            referenceVertices.release();
            actualIndices.release();
            referenceIndices.release();
            assertEquals(0, offset(pair.actual));
        }
    }

    @Test
    void subclassesRetainVirtualDispatchAndArraySnapshots() {
        float[] brightness = {1, 0.75F, 0.5F, 0.25F};
        int[] lights = {1, 2, 3, 4};
        class MutatingBuilder extends BufferBuilder {
            int calls;
            MutatingBuilder() { super(1024); }
            @Override
            public void vertex(float x, float y, float z, float r, float g, float b, float a,
                               float u, float v, int overlay, int light, float nx, float ny, float nz) {
                assertEquals(++calls, light);
                assertEquals((5 - calls) * 0.25F, r);
                Arrays.fill(brightness, -1);
                Arrays.fill(lights, -1);
                super.vertex(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
            }
        }
        MutatingBuilder builder = new MutatingBuilder();
        try {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            builder.putBulkData(pose(0).last(), quad(new Random(1), 32, Direction.UP), brightness, 1, 1, 1, lights, 0, false);
            assertEquals(4, builder.calls);
            builder.end().release();
        } finally {
            MemoryUtil.memFree(buffer(builder));
        }
        class CountingQuad extends BakedQuad {
            int calls;
            CountingQuad() { super(new int[32], -1, Direction.UP, null, true); }
            @Override public int[] getVertices() { calls++; return super.getVertices(); }
        }
        try (Pair pair = new Pair(1024)) {
            pair.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            CountingQuad actual = new CountingQuad();
            CountingQuad reference = new CountingQuad();
            pair.actual.putBulkData(pose(0).last(), actual, 1, 1, 1, 0, 0);
            pair.reference.putBulkData(pose(0).last(), reference, 1, 1, 1, 0, 0);
            assertEquals(reference.calls, actual.calls);
            pair.compareAndRelease(false);
        }
    }

    private static void assertSameOutcome(Pair pair, Runnable actual, Runnable reference) {
        Throwable expected = outcome(reference);
        Throwable found = outcome(actual);
        assertEquals(expected == null ? null : expected.getClass(), found == null ? null : found.getClass());
        assertEquals(offset(pair.reference), offset(pair.actual));
        assertEquals(pair.reference.currentElement(), pair.actual.currentElement());
    }

    private static Throwable outcome(Runnable action) {
        try { action.run(); return null; } catch (RuntimeException exception) { return exception; }
    }

    private static PoseStack pose(int index) {
        PoseStack pose = new PoseStack();
        if (index != 0) {
            pose.translate(index * 0.125, -3.5, 7.25);
            pose.mulPose(Vector3f.YP.rotationDegrees(index * 13.75F));
            pose.scale(-0.75F, 1.5F, 0.25F);
        }
        return pose;
    }

    private static BakedQuad quad(Random random, int length, Direction direction) {
        int[] data = new int[length];
        for (int i = 0; i < length; i++) {
            data[i] = i % 8 == 3 || i % 8 >= 6 ? random.nextInt()
                : Float.floatToRawIntBits(random.nextFloat() * 32.0F - 16.0F);
        }
        return new BakedQuad(data, -1, direction, null, true);
    }

    private static Field field(String name) {
        try {
            Field field = BufferBuilder.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static ByteBuffer buffer(BufferBuilder builder) {
        try { return (ByteBuffer) BUFFER.get(builder); }
        catch (IllegalAccessException exception) { throw new AssertionError(exception); }
    }

    private static int offset(BufferBuilder builder) {
        try { return NEXT_BYTE.getInt(builder); }
        catch (IllegalAccessException exception) { throw new AssertionError(exception); }
    }

    private static void initialize(BufferBuilder builder) {
        ByteBuffer buffer = buffer(builder);
        MemoryUtil.memSet(MemoryUtil.memAddress(buffer), 0x5a, buffer.capacity());
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    private static void compare(BufferBuilder.RenderedBuffer actual, BufferBuilder.RenderedBuffer reference, boolean grew) {
        assertEquals(reference.drawState(), actual.drawState());
        byte[] expected = bytes(reference.vertexBuffer());
        byte[] found = bytes(actual.vertexBuffer());
        VertexFormat format = actual.drawState().format();
        if (format == DefaultVertexFormat.BLOCK || format == DefaultVertexFormat.NEW_ENTITY
            || format == DefaultVertexFormat.POSITION_COLOR_NORMAL) {
            int stride = format.getVertexSize();
            for (int i = stride - 1; i < found.length; i += stride) {
                if (!grew) {
                    assertEquals(0x5a, expected[i] & 255, "reference padding");
                    assertEquals(0x5a, found[i] & 255, "padding remains untouched");
                } else {
                    expected[i] = found[i] = 0; // realloc's newly added padding is unspecified
                }
            }
        }
        assertArrayEquals(expected, found);
        assertArrayEquals(bytes(reference.indexBuffer()), bytes(actual.indexBuffer()));
    }

    private static final class Pair implements AutoCloseable {
        final BufferBuilder actual;
        final BufferBuilder reference;
        Pair(int capacity) {
            actual = new BufferBuilder(capacity);
            reference = new ReferenceBuilder(capacity);
            initialize(actual);
            initialize(reference);
        }
        void begin(VertexFormat.Mode mode, VertexFormat format) {
            actual.begin(mode, format);
            reference.begin(mode, format);
        }
        void compareAndRelease(boolean grew) {
            BufferBuilder.RenderedBuffer actualBuffer = actual.end();
            BufferBuilder.RenderedBuffer referenceBuffer = reference.end();
            compare(actualBuffer, referenceBuffer, grew);
            actualBuffer.release();
            referenceBuffer.release();
            assertEquals(0, offset(actual));
            assertEquals(0, offset(reference));
        }
        @Override public void close() {
            MemoryUtil.memFree(buffer(actual));
            MemoryUtil.memFree(buffer(reference));
        }
    }

    // Frozen from VertexConsumer.putBulkData at 49f21918, before the P2.1 edit.
    // Deliberately retain array snapshots, stack copies and per-vertex vectors.
    private static final class ReferenceBuilder extends BufferBuilder {
        ReferenceBuilder(int capacity) { super(capacity); }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, int light, int overlay) {
            this.putBulkData(pose, quad, new float[] {1, 1, 1, 1}, r, g, b,
                new int[] {light, light, light, light}, overlay, false);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                                float r, float g, float b, int[] lights, int overlay, boolean tint) {
            float[] factors = {brightness[0], brightness[1], brightness[2], brightness[3]};
            int[] lightCopy = {lights[0], lights[1], lights[2], lights[3]};
            int[] data = quad.getVertices();
            Vec3i face = quad.getDirection().getNormal();
            Vector3f normal = new Vector3f((float) face.getX().toDoubleRounded(), (float) face.getY(),
                (float) face.getZ().toDoubleRounded());
            Matrix4f matrix = pose.pose();
            normal.transform(pose.normal());
            int count = data.length / 8;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer bytes = stack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
                IntBuffer ints = bytes.asIntBuffer();
                for (int i = 0; i < count; i++) {
                    ints.clear();
                    ints.put(data, i * 8, 8);
                    float x = bytes.getFloat(0);
                    float y = bytes.getFloat(4);
                    float z = bytes.getFloat(8);
                    float red, green, blue;
                    if (tint) {
                        float sourceRed = (float) (bytes.get(12) & 255) / 255.0F;
                        float sourceGreen = (float) (bytes.get(13) & 255) / 255.0F;
                        float sourceBlue = (float) (bytes.get(14) & 255) / 255.0F;
                        red = sourceRed * factors[i] * r;
                        green = sourceGreen * factors[i] * g;
                        blue = sourceBlue * factors[i] * b;
                    } else {
                        red = factors[i] * r;
                        green = factors[i] * g;
                        blue = factors[i] * b;
                    }
                    int light = lightCopy[i];
                    float u = bytes.getFloat(16);
                    float v = bytes.getFloat(20);
                    Vector4f position = new Vector4f(x, y, z, 1.0F);
                    position.transform(matrix);
                    this.vertex(position.x(), position.y(), position.z(), red, green, blue, 1.0F,
                        u, v, overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
        }
    }

    // Local headless diagnostic, not a timing assertion in the JUnit suite.
    public static void main(String[] args) {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counters unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        try (Pair pair = new Pair(65536)) {
            BakedQuad quad = quad(new Random(2101), 32, Direction.WEST);
            PoseStack.Pose pose = pose(7).last();
            float[] brightness = {1, 0.75F, 0.5F, 0.25F};
            int[] lights = {0, 0x12345678, -1, 0x80000000};
            long[] actualTimes = new long[7];
            long[] referenceTimes = new long[7];
            for (VertexFormat format : new VertexFormat[] {DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY}) {
                for (boolean simple : new boolean[] {false, true}) {
                    probe(pair.actual, format, pose, quad, brightness, lights, simple, 200000);
                    probe(pair.reference, format, pose, quad, brightness, lights, simple, 200000);
                    long actualBytes = 0, referenceBytes = 0;
                    for (int trial = 0; trial < 7; trial++) {
                        long previousResult = 0;
                        for (int turn = 0; turn < 2; turn++) {
                            boolean optimized = (trial + turn) % 2 == 0;
                            BufferBuilder builder = optimized ? pair.actual : pair.reference;
                            long beforeBytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                            long start = System.nanoTime();
                            long result = probe(builder, format, pose, quad, brightness, lights, simple, 100000);
                            long elapsed = System.nanoTime() - start;
                            long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                            checksum = result;
                            if (turn == 0) previousResult = result;
                            else if (result != previousResult) throw new AssertionError("Probe checksums differ");
                            if (optimized) { actualTimes[trial] = elapsed; actualBytes += allocated; }
                            else { referenceTimes[trial] = elapsed; referenceBytes += allocated; }
                            System.out.printf("format=%s simple=%s trial=%d path=%s ns=%d bytes=%d checksum=%d%n",
                                format == DefaultVertexFormat.BLOCK ? "block" : "entity", simple, trial,
                                optimized ? "optimized" : "reference", elapsed, allocated, result);
                        }
                    }
                    long[] sortedActual = actualTimes.clone();
                    long[] sortedReference = referenceTimes.clone();
                    Arrays.sort(sortedActual);
                    Arrays.sort(sortedReference);
                    System.out.printf("SUMMARY format=%s simple=%s optimized_ns=%d reference_ns=%d time_delta=%.2f%% optimized_bytes_per_quad=%.3f reference_bytes_per_quad=%.3f%n",
                        format == DefaultVertexFormat.BLOCK ? "block" : "entity", simple,
                        sortedActual[3], sortedReference[3], (double) sortedActual[3] / sortedReference[3] * 100 - 100,
                        actualBytes / 700000.0, referenceBytes / 700000.0);
                }
            }
        }
    }

    private static long probe(BufferBuilder builder, VertexFormat format, PoseStack.Pose pose, BakedQuad quad,
                              float[] brightness, int[] lights, boolean simple, int count) {
        long result = 0;
        for (int batch = 0; batch < count / 100; batch++) {
            builder.begin(VertexFormat.Mode.QUADS, format);
            for (int i = 0; i < 100; i++) {
                if (simple) builder.putBulkData(pose, quad, 0.3F, 0.7F, 0.9F, lights[0], -1);
                else builder.putBulkData(pose, quad, brightness, 0.3F, 0.7F, 0.9F, lights, -1, true);
            }
            BufferBuilder.RenderedBuffer rendered = builder.end();
            ByteBuffer data = rendered.vertexBuffer();
            result += data.getInt(0) + data.getInt(data.remaining() - 8);
            rendered.release();
        }
        return result;
    }
}
