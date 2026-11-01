package net.minecraft.util;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.ExactVec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CubicSamplerAllocationTest {
    private static final double[] KERNEL = {0, 1, 4, 6, 4, 1, 0};
    private static volatile Vec3 result;

    @Test
    void scalarAccumulationMatchesFrozenVectorFormulaAndEveryCallback() {
        Random random = new Random(481623);
        for (int run = 0; run < 300; ++run) {
            Vec3 position = new Vec3(random.nextDouble() * 200 - 100,
                    random.nextDouble() * 400 - 200, random.nextDouble() * 200 - 100);
            Vec3[] samples = new Vec3[216];
            for (int i = 0; i < samples.length; ++i) {
                samples[i] = new Vec3(component(random, run), component(random, run), component(random, run));
            }
            List<String> expectedCalls = new ArrayList<>();
            List<String> actualCalls = new ArrayList<>();
            Vec3 expected = reference(position, (x, y, z) -> {
                expectedCalls.add(x + ":" + y + ":" + z);
                return samples[expectedCalls.size() - 1];
            });
            Vec3 actual = CubicSampler.gaussianSampleVec3(position, (x, y, z) -> {
                actualCalls.add(x + ":" + y + ":" + z);
                return samples[actualCalls.size() - 1];
            });
            assertEquals(216, actualCalls.size());
            assertEquals(expectedCalls, actualCalls);
            assertBits(expected, actual);
        }
    }

    @Test
    void specializedScaleAndAccumulationKeepVirtualDispatch() {
        Vec3 position = new Vec3(.25, -.75, .5);
        int[] expectedCalls = {0};
        int[] actualCalls = {0};
        Vec3 expected = reference(position, specializedFetcher(expectedCalls));
        Vec3 actual = CubicSampler.gaussianSampleVec3(position, specializedFetcher(actualCalls));
        assertBits(expected, actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expectedCalls[0], actualCalls[0]);
        assertEquals(216, actualCalls[0]);
    }

    @Test
    void specializedWeightedResultPreservesExactAccumulator() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(Int1088.ONE.shiftLeftSaturated(160),
                .25, 70, Int1088.fromLong(-19), .5);
        Vec3 position = new Vec3(.25, -.75, .5);
        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> new Vec3(x, y, z) {
            @Override
            public Vec3 scale(double factor) {
                return new ExactVec3(exact);
            }
        };
        Vec3 expected = reference(position, fetcher);
        Vec3 actual = CubicSampler.gaussianSampleVec3(position, fetcher);
        assertBits(expected, actual);
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.exactPosition(), actual.exactPosition());
    }

    @Test
    void nestedSamplingAndExceptionsKeepCallbackSemantics() {
        Vec3 position = new Vec3(-.25, .5, 1.75);
        CubicSampler.Vec3Fetcher nested = (x, y, z) ->
                CubicSampler.gaussianSampleVec3(Vec3.ZERO, (a, b, c) -> new Vec3(x, y, z));
        assertBits(reference(position, nested), CubicSampler.gaussianSampleVec3(position, nested));
        RuntimeException failure = new RuntimeException("sample");
        int[] calls = {0};
        assertSame(failure, assertThrows(RuntimeException.class, () ->
                CubicSampler.gaussianSampleVec3(position, (x, y, z) -> {
                    if (++calls[0] == 17) throw failure;
                    return Vec3.ZERO;
                })));
        assertEquals(17, calls[0]);
    }

    private static double component(Random random, int run) {
        if (run % 10 == 0) {
            double[] special = {0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE,
                    Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
            return special[random.nextInt(special.length)];
        }
        return Math.scalb(random.nextDouble() * 2 - 1, random.nextInt(100) - 50);
    }

    private static CubicSampler.Vec3Fetcher specializedFetcher(int[] calls) {
        return (x, y, z) -> new Vec3(x, y, z) {
            @Override
            public Vec3 scale(double factor) {
                ++calls[0];
                return new Vec3(x * factor, y * factor, z * factor) {
                    @Override
                    public Vec3 add(double a, double b, double c) {
                        ++calls[0];
                        return super.add(a, b, c);
                    }
                };
            }
        };
    }

    private static void assertBits(Vec3 expected, Vec3 actual) {
        assertEquals(Double.doubleToLongBits(expected.x), Double.doubleToLongBits(actual.x));
        assertEquals(Double.doubleToLongBits(expected.y), Double.doubleToLongBits(actual.y));
        assertEquals(Double.doubleToLongBits(expected.z), Double.doubleToLongBits(actual.z));
    }

    // Frozen pre-optimization formula, independent of the production accumulator.
    private static Vec3 reference(Vec3 position, CubicSampler.Vec3Fetcher fetcher) {
        long x = Mth.lfloor(position.x());
        int y = Mth.floor(position.y());
        long z = Mth.lfloor(position.z());
        double dx = position.x() - (double) x;
        double dy = position.y() - (double) y;
        double dz = position.z() - (double) z;
        double weight = 0;
        Vec3 sum = Vec3.ZERO;
        for (int i = 0; i < 6; ++i) {
            double wx = Mth.lerp(dx, KERNEL[i + 1], KERNEL[i]);
            for (int j = 0; j < 6; ++j) {
                double wy = Mth.lerp(dy, KERNEL[j + 1], KERNEL[j]);
                for (int k = 0; k < 6; ++k) {
                    double wz = Mth.lerp(dz, KERNEL[k + 1], KERNEL[k]);
                    double w = wx * wy * wz;
                    weight += w;
                    sum = sum.add(fetcher.fetch(x - 2L + i, y - 2 + j, z - 2L + k).scale(w));
                }
            }
        }
        return sum.scale(1.0D / weight);
    }

    /** Headless diagnostic only; no timing/GC-pause acceptance assertion. */
    public static void main(String[] args) {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("allocation counters unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        Vec3 position = new Vec3(.25, -.75, .5);
        CubicSampler.Vec3Fetcher fetcher = (x, y, z) -> new Vec3(x / 255.0, y / 255.0, z / 255.0);
        for (int i = 0; i < 20000; ++i) {
            result = reference(position, fetcher);
            result = CubicSampler.gaussianSampleVec3(position, fetcher);
        }
        long thread = Thread.currentThread().getId();
        long start = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 10000; ++i) result = reference(position, fetcher);
        long referenceBytes = bean.getThreadAllocatedBytes(thread) - start;
        start = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 10000; ++i) result = CubicSampler.gaussianSampleVec3(position, fetcher);
        long optimizedBytes = bean.getThreadAllocatedBytes(thread) - start;
        System.out.println("reference_bytes=" + referenceBytes + " optimized_bytes=" + optimizedBytes);
        if (optimizedBytes >= referenceBytes) throw new AssertionError("no measured allocation reduction");
    }
}
