package com.mojang.blaze3d.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Floats;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.apache.commons.lang3.mutable.MutableInt;
import org.lwjgl.system.MemoryUtil;

class BufferBuilderSortTest {
    private static final Field BUFFER = field("buffer");
    private static final Method SORT = method();
    private static volatile long checksum;

    @Test
    void seededQuadIndicesMatchFrozenReference() {
        try (Builder owner = new Builder()) {
            for (int seed = 0; seed < 100; seed++) {
                for (int count : new int[] {0, 1, 2, 3, 16, 255, 1024}) {
                    float[][] vertices = vertices(count, seed);
                    float[] origin = {seed - 50, seed % 7, -seed};
                    check(owner.builder, vertices, origin);
                }
            }
        }
    }

    @Test
    void tiesNonfiniteAndExtremeRelativeOriginsMatch() {
        float[] values = {0, -0.0F, 1, -1, Float.NaN, Float.intBitsToFloat(0x7fc12345),
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE, Float.MIN_VALUE};
        try (Builder owner = new Builder()) {
            for (float origin : values) {
                float[][] vertices = vertices(64, 2);
                for (int i = 0; i < vertices.length; i++) {
                    vertices[i] = new float[] {values[i % values.length], values[(i / 4) % values.length], 0};
                }
                check(owner.builder, vertices, new float[] {origin, -origin, -0.0F});
            }
            float[][] ties = new float[256][3];
            for (int i = 0; i < ties.length; i++) ties[i][0] = i % 2 == 0 ? 1 : -1;
            check(owner.builder, ties, new float[] {0, 0, 0});
        }
    }

    @Test
    void widthBoundariesAndGrowShrinkReuseMatch() {
        try (Builder owner = new Builder()) {
            for (int count : new int[] {42, 43, 10922, 10923, 16384, 16385, 3, 1024, 1, 0, 255}) {
                check(owner.builder, vertices(count, count), new float[] {3, -2, 1});
            }
        }
    }

    @Test
    void retainedBatchesAndCrossBuilderRestoresKeepSnapshotOwnership() {
        try (Builder first = new Builder(); Builder second = new Builder()) {
            float[][] vertices = vertices(255, 12);
            emit(first.builder, vertices);
            first.builder.setQuadSortOrigin(1, 2, 3);
            BufferBuilder.SortState state = first.builder.getSortState();
            BufferBuilder.RenderedBuffer retained = first.builder.end();
            byte[] saved = bytes(retained.indexBuffer());
            check(first.builder, vertices(1024, 23), new float[] {4, 5, 6});
            assertArrayEquals(saved, bytes(retained.indexBuffer()));
            for (int i = 0; i < 8; i++) {
                BufferBuilder target = i % 2 == 0 ? first.builder : second.builder;
                target.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                target.restoreSortState(state);
                float[] origin = {i, -i, i * 0.5F};
                target.setQuadSortOrigin(origin[0], origin[1], origin[2]);
                BufferBuilder.RenderedBuffer sorted = target.end();
                assertTrue(sorted.drawState().indexOnly());
                assertArrayEquals(referenceIndices(centers(vertices), origin, sorted.drawState().indexType()), bytes(sorted.indexBuffer()));
                sorted.release();
                assertArrayEquals(saved, bytes(retained.indexBuffer()));
            }
            retained.release();
            check(first.builder, vertices(16, 4), new float[] {0, 0, 0});
        }
    }

    @Test
    void unsortedNonQuadAndDiscardPathsRemainSequential() {
        try (Builder owner = new Builder()) {
            check(owner.builder, vertices(255, 1), new float[] {1, 1, 1});
            for (VertexFormat.Mode mode : new VertexFormat.Mode[] {VertexFormat.Mode.QUADS, VertexFormat.Mode.TRIANGLES}) {
                owner.builder.begin(mode, DefaultVertexFormat.POSITION);
                owner.builder.vertex(1, 2, 3).endVertex();
                if (mode != VertexFormat.Mode.QUADS) owner.builder.setQuadSortOrigin(0, 0, 0);
                BufferBuilder.RenderedBuffer batch = owner.builder.end();
                assertTrue(batch.drawState().sequentialIndex());
                batch.release();
            }
            owner.builder.discard();
            owner.builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            assertNull(owner.builder.endOrDiscardIfEmpty());
            check(owner.builder, vertices(3, 2), new float[] {0, 0, 0});
        }
    }

    @Test
    void scratchIsBoundedAndNeverOwnedBySortState() throws Exception {
        try (Builder owner = new Builder(); Builder other = new Builder()) {
            check(owner.builder, vertices(16384, 1), new float[] {0, 0, 0});
            Object distances = field("sortingDistances").get(owner.builder);
            Object indices = field("sortingIndices").get(owner.builder);
            Object support = field("sortingSupport").get(owner.builder);
            assertEquals(16384, ((float[]) distances).length);
            assertEquals(16384, ((int[]) indices).length);
            assertEquals(16384, ((int[]) support).length);
            assertNotSame(indices, support);
            check(owner.builder, vertices(16385, 2), new float[] {1, 2, 3});
            check(owner.builder, vertices(16, 3), new float[] {3, 2, 1});
            owner.builder.discard();
            assertSame(distances, field("sortingDistances").get(owner.builder));
            assertSame(indices, field("sortingIndices").get(owner.builder));
            assertSame(support, field("sortingSupport").get(owner.builder));
            check(other.builder, vertices(16, 4), new float[] {1, 2, 3});
            assertNotSame(indices, field("sortingIndices").get(other.builder));
            emit(owner.builder, vertices(16, 5));
            owner.builder.setQuadSortOrigin(0, 0, 0);
            BufferBuilder.SortState state = owner.builder.getSortState();
            owner.builder.end().release();
            Vector3f first = state.sortingPoints[0].copy();
            check(owner.builder, vertices(255, 6), new float[] {0, 0, 0});
            assertEquals(first, state.sortingPoints[0]);
        }
    }

    private static void check(BufferBuilder builder, float[][] vertices, float[] origin) {
        emit(builder, vertices);
        builder.setQuadSortOrigin(origin[0], origin[1], origin[2]);
        BufferBuilder.SortState state = builder.getSortState();
        Vector3f[] expected = centers(vertices);
        assertEquals(expected.length, state.sortingPoints.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToIntBits(expected[i].x()), Float.floatToIntBits(state.sortingPoints[i].x()));
            assertEquals(Float.floatToIntBits(expected[i].y()), Float.floatToIntBits(state.sortingPoints[i].y()));
            assertEquals(Float.floatToIntBits(expected[i].z()), Float.floatToIntBits(state.sortingPoints[i].z()));
        }
        BufferBuilder.RenderedBuffer batch = builder.end();
        assertFalse(batch.drawState().sequentialIndex());
        assertArrayEquals(referenceIndices(expected, origin, batch.drawState().indexType()), bytes(batch.indexBuffer()));
        batch.release();
    }

    private static void emit(BufferBuilder builder, float[][] vertices) {
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (float[] vertex : vertices) builder.vertex(vertex[0], vertex[1], vertex[2]).endVertex();
    }

    private static float[][] vertices(int count, int seed) {
        Random random = new Random(seed);
        float[][] vertices = new float[count * 4][3];
        for (float[] vertex : vertices) {
            for (int axis = 0; axis < 3; axis++) vertex[axis] = random.nextInt(65) - 32;
        }
        return vertices;
    }

    private static Vector3f[] centers(float[][] vertices) {
        Vector3f[] centers = new Vector3f[vertices.length / 4];
        for (int i = 0; i < centers.length; i++) {
            float[] a = vertices[i * 4];
            float[] b = vertices[i * 4 + 2];
            centers[i] = new Vector3f((a[0] + b[0]) / 2.0F, (a[1] + b[1]) / 2.0F, (a[2] + b[2]) / 2.0F);
        }
        return centers;
    }

    // Frozen pre-P2.2 distance arithmetic, stable ordering and six-index emission.
    private static int[] referenceOrder(Vector3f[] points, float[] origin) {
        float[] distances = new float[points.length];
        int[] indices = new int[points.length];
        for (int i = 0; i < points.length; indices[i] = i++) {
            float x = points[i].x() - origin[0];
            float y = points[i].y() - origin[1];
            float z = points[i].z() - origin[2];
            distances[i] = x * x + y * y + z * z;
        }
        IntArrays.mergeSort(indices, (a, b) -> Floats.compare(distances[b], distances[a]));
        return indices;
    }

    private static byte[] referenceIndices(Vector3f[] points, float[] origin, VertexFormat.IndexType type) {
        ByteBuffer output = ByteBuffer.allocate(points.length * 6 * type.bytes).order(ByteOrder.nativeOrder());
        referenceWrite(output, referenceOrder(points, origin), type);
        return output.array();
    }

    private static void referenceWrite(ByteBuffer output, int[] indices, VertexFormat.IndexType type) {
        MutableInt pointer = new MutableInt(0);
        IntConsumer consumer = switch (type) {
            case BYTE -> index -> output.put(pointer.getAndIncrement(), (byte) index);
            case SHORT -> index -> output.putShort(pointer.getAndAdd(2), (short) index);
            case INT -> index -> output.putInt(pointer.getAndAdd(4), index);
        };
        for (int quad : indices) {
            consumer.accept(quad * 4 + 0);
            consumer.accept(quad * 4 + 1);
            consumer.accept(quad * 4 + 2);
            consumer.accept(quad * 4 + 2);
            consumer.accept(quad * 4 + 3);
            consumer.accept(quad * 4 + 0);
        }
    }

    // Local-only init script runs this diagnostic; it is not a desktop acceptance test.
    public static void main(String[] args) throws Exception {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int count : new int[] {16, 255, 1024, 10923, 16385}) {
            try (Builder owner = new Builder()) {
                Vector3f[] points = centers(vertices(count, 42));
                float[] origin = {3, -2, 1};
                emit(owner.builder, vertices(count, 42));
                owner.builder.setQuadSortOrigin(origin[0], origin[1], origin[2]);
                owner.builder.end().release();
                // Isolate repeated index sorting/writes, excluding unchanged center creation.
                field("sortingPoints").set(owner.builder, points);
                field("sortX").setFloat(owner.builder, origin[0]);
                field("sortY").setFloat(owner.builder, origin[1]);
                field("sortZ").setFloat(owner.builder, origin[2]);
                field("nextElementByte").setInt(owner.builder, 0);
                field("mode").set(owner.builder, VertexFormat.Mode.QUADS);
                VertexFormat.IndexType type = VertexFormat.IndexType.least(count * 6);
                ByteBuffer reference = ByteBuffer.allocateDirect(count * 6 * type.bytes).order(ByteOrder.nativeOrder());
                ByteBuffer actual = ((ByteBuffer) BUFFER.get(owner.builder)).duplicate().order(ByteOrder.nativeOrder());
                int warmup = Math.max(2000, 2000000 / count);
                int iterations = Math.max(100, 1000000 / count);
                System.out.printf("workload quads=%d warmup=%d iterations=%d%n", count, warmup, iterations);
                for (int i = 0; i < warmup; i++) {
                    referenceWrite(reference, referenceOrder(points, origin), type);
                    SORT.invoke(owner.builder, type);
                }
                for (int trial = 0; trial < 7; trial++) {
                    long[] nanos = new long[2];
                    long[] allocated = new long[2];
                    long[] sums = new long[2];
                    for (int order = 0; order < 2; order++) {
                        int path = (trial + order) % 2;
                        long beforeBytes = bean.getThreadAllocatedBytes(thread);
                        long start = System.nanoTime();
                        long sum = 0;
                        for (int i = 0; i < iterations; i++) {
                            if (path == 0) referenceWrite(reference, referenceOrder(points, origin), type);
                            else SORT.invoke(owner.builder, type);
                            ByteBuffer result = path == 0 ? reference : actual;
                            for (int b = 0; b < count * 6 * type.bytes; b++) sum = sum * 31 + result.get(b);
                        }
                        nanos[path] = System.nanoTime() - start;
                        allocated[path] = bean.getThreadAllocatedBytes(thread) - beforeBytes;
                        sums[path] = sum;
                        checksum = sum;
                    }
                    if (sums[0] != sums[1]) throw new AssertionError("probe checksum mismatch");
                    System.out.printf("trial=%d reference_ns=%d optimized_ns=%d reference_bytes=%d optimized_bytes=%d checksum=%d%n",
                        trial, nanos[0], nanos[1], allocated[0], allocated[1], checksum);
                }
            }
        }
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static Field field(String name) {
        try {
            Field field = BufferBuilder.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Method method() {
        try {
            Method method = BufferBuilder.class.getDeclaredMethod("putSortedQuadIndices", VertexFormat.IndexType.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static class Builder implements AutoCloseable {
        final BufferBuilder builder = new BufferBuilder(1024);

        @Override
        public void close() {
            try { MemoryUtil.memFree((ByteBuffer) BUFFER.get(builder)); }
            catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
    }
}
