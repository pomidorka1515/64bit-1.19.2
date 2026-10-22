package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
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
            this.lastX = context.blockX();
            this.lastZ = context.blockZ();
            int height = 64 + (int) (context.blockX() & 12L) + (int) (context.blockZ() & 12L);
            return context.blockY() <= height ? 1.0D : 0.0D;
        }

        public double minValue() { return 0.0D; }
        public double maxValue() { return 1.0D; }
        public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DensityFunctions.Constant.CODEC; }
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

    private static NoiseChunk chunk(long origin, NoiseGeneratorSettings settings) {
        RandomState state = RandomState.create(settings, BuiltinRegistries.NOISE, 42L);
        return new NoiseChunk(4, state, origin, origin, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings,
                    (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
    }
}
