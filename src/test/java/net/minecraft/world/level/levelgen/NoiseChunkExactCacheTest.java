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

        public double compute(DensityFunction.FunctionContext context) {
            this.calls.incrementAndGet();
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
    void exactPreliminarySurfaceUsesUnclampedCoordinatesAcrossChunkBoundaries() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        ColumnDensity density = new ColumnDensity();
        DensityFunction zero = DensityFunctions.zero();
        NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                density, zero, zero, zero, zero);
        NoiseGeneratorSettings base = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(base.noiseSettings(), base.defaultBlock(),
                base.defaultFluid(), router, base.surfaceRule(), List.of(), 63, false, false, false, false);
        RandomState state = RandomState.create(settings, BuiltinRegistries.NOISE, 42L);
        for (long origin : new long[] {Long.MAX_VALUE - 255L, Long.MIN_VALUE + 256L}) {
            NoiseChunk chunk = new NoiseChunk(4, state, origin, origin, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings,
                    (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
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
}
