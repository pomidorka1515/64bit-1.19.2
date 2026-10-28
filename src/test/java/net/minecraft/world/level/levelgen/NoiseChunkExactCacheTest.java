package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NoiseChunkExactCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaultMode() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
    }

    private static class ColumnDensity implements DensityFunction.SimpleFunction {
        final AtomicInteger calls = new AtomicInteger();
        long lastX;
        long lastZ;

        public double compute(DensityFunction.FunctionContext context) {
            this.calls.incrementAndGet();
            this.lastX = context.blockXLimb(0);
            this.lastZ = context.blockZLimb(0);
            int height = 64 + (int) (context.blockXLimb(0) & 12L) + (int) (context.blockZLimb(0) & 12L);
            return context.blockY() <= height ? 1.0D : 0.0D;
        }

        public double minValue() { return 0.0D; }
        public double maxValue() { return 1.0D; }
        public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DensityFunctions.Constant.CODEC; }
    }

    @Test
    void exactCacheHitsDoNotAllocateCoordinateKeys() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(new ColumnDensity());
        DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(Long.MIN_VALUE, 80, Long.MAX_VALUE);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 100_000; ++i) cache.compute(context);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 10_000; ++i) cache.compute(context);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        org.junit.jupiter.api.Assertions.assertTrue(allocated < 1024L, "cache hits allocated " + allocated + " bytes");
    }

    @Test
    void exactAndLegacyCachesStayIndependentAcrossModeSwitches() {
        ColumnDensity density = new ColumnDensity();
        NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
        DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(0L, 80, 0L);
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        cache.compute(context);
        assertEquals(1, density.calls.get(), "zero must not be mistaken for an initialized key");
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
        cache.compute(context);
        assertEquals(2, density.calls.get());
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        cache.compute(context);
        assertEquals(2, density.calls.get());
    }

    @Test
    void exact2DCacheRetainsEachColumnAndStillReusesTheSameColumn() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        for (long origin : new long[] {Long.MAX_VALUE - 255L, Long.MIN_VALUE + 256L}) {
            ColumnDensity density = new ColumnDensity();
            NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
            for (int offset = 0; offset < 32; ++offset) {
                long x = origin + offset;
                long z = origin + 31 - offset;
                DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(x, 80, z);
                assertEquals(new ColumnDensity().compute(context), cache.compute(context));
                assertEquals(offset + 1, density.calls.get(), "distinct columns must not share a key");
                cache.compute(context);
                assertEquals(offset + 1, density.calls.get(), "same column must hit the cache");
            }
        }
    }

    @Test
    void otherModesKeepTheirExisting2DCacheBehavior() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            if (mode == FarlandsMode.Mode.BIT_64_PRECISE) continue;
            FarlandsMode.setMode(mode);
            ColumnDensity density = new ColumnDensity();
            NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
            cache.compute(new DensityFunction.SinglePointContext(Long.MAX_VALUE - 255L, 80, Long.MAX_VALUE - 255L));
            cache.compute(new DensityFunction.SinglePointContext(Long.MAX_VALUE - 251L, 80, Long.MAX_VALUE - 255L));
            assertEquals(1, density.calls.get(), mode + " must retain its existing key semantics");
        }
    }

    @Test
    void legacyCacheClampsBothEdgesButKeepsInteriorColumnsDistinct() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            if (mode == FarlandsMode.Mode.BIT_64_PRECISE) continue;
            FarlandsMode.setMode(mode);
            for (long origin : new long[] {Long.MIN_VALUE + 256L, Long.MAX_VALUE - 256L}) {
                ColumnDensity density = new ColumnDensity();
                NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
                cache.compute(new DensityFunction.SinglePointContext(origin, 80, origin));
                cache.compute(new DensityFunction.SinglePointContext(origin + 4L, 80, origin + 4L));
                assertEquals(1, density.calls.get(), mode.toString());
                cache.compute(new DensityFunction.SinglePointContext(0L, 80, 0L));
                cache.compute(new DensityFunction.SinglePointContext(4L, 80, 0L));
                assertEquals(3, density.calls.get(), "interior columns must not alias");
            }
        }
    }

    @Test
    void legacySurfaceSamplingRetainsItsSignedLongChunkDomain() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            if (mode == FarlandsMode.Mode.BIT_64_PRECISE) continue;
            FarlandsMode.setMode(mode);
            for (long origin : new long[] {Long.MAX_VALUE - 255L, Long.MIN_VALUE + 256L}) {
                ColumnDensity density = new ColumnDensity();
                NoiseGeneratorSettings settings = settings(density);
                NoiseChunk chunk = chunk(origin, settings);
                long expectedCoordinate = origin < 0 ? Long.MIN_VALUE >> 4 : Long.MAX_VALUE >> 4;
                int expectedHeight = 64 + (int) (expectedCoordinate & 12L) * 2;
                expectedHeight = Math.floorDiv(expectedHeight, settings.noiseSettings().getCellHeight())
                        * settings.noiseSettings().getCellHeight();
                assertEquals(expectedHeight, chunk.preliminarySurfaceLevel(origin, origin), mode.toString());
                assertEquals(expectedCoordinate, density.lastX);
                assertEquals(expectedCoordinate, density.lastZ);
                int calls = density.calls.get();
                assertEquals(expectedHeight, chunk.preliminarySurfaceLevel(origin + 4L, origin + 4L));
                assertEquals(calls, density.calls.get(), "legacy clamped columns must share a cache entry");
            }
        }
    }

    @Test
    void exactPreliminarySurfaceUsesUnclampedCoordinatesAcrossChunkBoundaries() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        ColumnDensity density = new ColumnDensity();
        NoiseGeneratorSettings settings = settings(density);
        for (long origin : new long[] {Long.MAX_VALUE - 255L, Long.MIN_VALUE + 256L}) {
            NoiseChunk chunk = chunk(origin, settings);
            for (int offset = 0; offset < 32; offset += 4) {
                long x = origin + offset;
                int expected = 64 + (int) (x & 12L) + (int) (origin & 12L);
                expected = Math.floorDiv(expected, settings.noiseSettings().getCellHeight())
                        * settings.noiseSettings().getCellHeight();
                assertEquals(expected, chunk.preliminarySurfaceLevel(x, origin));
                int calls = density.calls.get();
                assertEquals(expected, chunk.preliminarySurfaceLevel(x + 1L, origin + 1L));
                assertEquals(calls, density.calls.get(), "same quart column must hit the cache");
            }
        }
    }

    private static NoiseGeneratorSettings settings(ColumnDensity density) {
        DensityFunction zero = DensityFunctions.zero();
        NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                density, zero, zero, zero, zero);
        NoiseGeneratorSettings base = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new NoiseGeneratorSettings(base.noiseSettings(), base.defaultBlock(),
                base.defaultFluid(), router, base.surfaceRule(), List.of(), 63, false, false, false, false);
    }

    @Test
    void fullWidthChunkTransportCarriesAcrossLimbsAndSaturatesAtWorldEdge() {
        Int1088[] origins = {
                Int1088.of(-16L, 5L), Int1088.of(Long.MIN_VALUE, 0L), Int1088.of(0L, -2L),
                Int1088.fromLong(Long.MAX_VALUE - 15L),
                Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.fromLong(16L)),
                Int1088.MAX.subtract(Int1088.fromLong(15L))};
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            for (Int1088 origin : origins) {
                NoiseGeneratorSettings settings = settings(new ColumnDensity());
                RandomState state = RandomState.create(settings, BuiltinRegistries.NOISE, 42L);
                NoiseChunk chunk = new NoiseChunk(4, state, origin, origin, settings.noiseSettings(),
                        DensityFunctions.BeardifierMarker.INSTANCE, settings,
                        (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
                chunk.initializeForFirstCellX();
                chunk.advanceCellX(0);
                chunk.selectCellYZ(0, 0);
                assertContext(origin, origin, chunk);
                chunk.advanceCellX(3);
                chunk.selectCellYZ(0, 3);
                Int1088 last = origin.addSaturated(Int1088.fromLong(15L));
                chunk.updateForX(last, 0.75D);
                chunk.updateForZ(last, 0.75D);
                assertContext(last, last, chunk);
                chunk.advanceCellX(4);
                chunk.selectCellYZ(0, 4);
                Int1088 next = origin.addSaturated(Int1088.fromLong(16L));
                chunk.updateForX(next, 0.0D);
                chunk.updateForZ(next, 0.0D);
                assertContext(next, next, chunk);
                chunk.stopInterpolation();
            }
        }
    }

    @Test
    void fullWidthPreliminarySurfaceCachesExactQuartColumns() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            ColumnDensity density = new ColumnDensity();
            NoiseChunk chunk = chunk(0L, settings(density));
            int height = chunk.preliminarySurfaceLevel(Int1088.of(32L, 17L), Int1088.of(48L, -19L));
            int calls = density.calls.get();
            assertEquals(height, chunk.preliminarySurfaceLevel(Int1088.of(35L, 17L), Int1088.of(51L, -19L)));
            assertEquals(calls, density.calls.get(), mode.toString());
            chunk.preliminarySurfaceLevel(Int1088.of(32L, 18L), Int1088.of(48L, -19L));
            org.junit.jupiter.api.Assertions.assertTrue(density.calls.get() > calls);
        }
    }

    @Test
    void cacheKeysIncludeBothHighLimbsInEveryMode() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            ColumnDensity density = new ColumnDensity();
            NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
            cache.compute(new DensityFunction.SinglePointContext(Int1088.of(17L, 5L), 80, Int1088.of(19L, 7L)));
            cache.compute(new DensityFunction.SinglePointContext(Int1088.of(17L, 6L), 80, Int1088.of(19L, 7L)));
            cache.compute(new DensityFunction.SinglePointContext(Int1088.of(17L, 6L), 80, Int1088.of(19L, 8L)));
            assertEquals(3, density.calls.get(), mode.toString());
        }
    }

    private static void assertContext(Int1088 x, Int1088 z, DensityFunction.FunctionContext context) {
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            assertEquals(x.limb(limb), context.blockXLimb(limb));
            assertEquals(z.limb(limb), context.blockZLimb(limb));
        }
    }

    @Test
    void exactCacheMissesReadEveryUpperLimbAndIgnoreY() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            ColumnDensity density = new ColumnDensity();
            NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(density);
            int calls = 0;
            for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
                for (int sign : new int[] {-1, 1}) {
                    Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64);
                    if (sign < 0) high = high.negate();
                    for (boolean x : new boolean[] {true, false}) {
                        Int1088 bx = x ? high : Int1088.of(3L, 1L);
                        Int1088 bz = x ? Int1088.of(5L, 1L) : high;
                        cache.compute(new DensityFunction.SinglePointContext(bx, 0, bz));
                        assertEquals(++calls, density.calls.get(), "limb " + limb + " " + mode);
                        cache.compute(new DensityFunction.SinglePointContext(bx.copy(), Integer.MIN_VALUE, bz.copy()));
                        assertEquals(calls, density.calls.get(), "Y must not invalidate a 2D cache");
                    }
                }
            }
        }
    }

    private static NoiseChunk chunk(long origin, NoiseGeneratorSettings settings) {
        RandomState state = RandomState.create(settings, BuiltinRegistries.NOISE, 42L);
        return new NoiseChunk(4, state, origin, origin, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings,
                    (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
    }
}
