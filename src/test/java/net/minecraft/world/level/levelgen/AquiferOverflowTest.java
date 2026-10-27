package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AquiferOverflowTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyExtrapolatingWaterCentersAreDiscarded() throws Exception {
        Method original = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("computeFluidUnfiltered", long.class, int.class, long.class);
        Method filtered = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("computeFluid", long.class, int.class, long.class);
        original.setAccessible(true);
        filtered.setAccessible(true);
        Method output = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("filterWaterOutput", Aquifer.FluidStatus.class, net.minecraft.world.level.block.state.BlockState.class);
        output.setAccessible(true);
        int healthyWater = 0;
        int lava = 0;
        for (GeneratorMode mode : GeneratorMode.values()) {
            try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.PRECISE))) {
                var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
                var random = RandomState.create(settings, BuiltinRegistries.NOISE, 0L);
                for (String coordinate : new String[] {"0", "2000000000000", "-225214492026149979000000"}) {
                    Int1088 x = Int1088.parseDecimal(coordinate).shiftRight(4).shiftLeftSaturated(4);
                    Aquifer.FluidPicker picker = (px, y, pz) -> y <= -54
                            ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                            : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
                    var chunk = new NoiseChunk(4, random, x, Int1088.ZERO, settings.noiseSettings(),
                            DensityFunctions.BeardifierMarker.INSTANCE, settings, picker, Blender.empty());
                    var aquifer = new Aquifer.NoiseBasedAquifer(chunk, new ChunkPos(x.shiftRight(4), Int1088.ZERO),
                            random.router(), random.aquiferRandom(), -64, 384, picker);
                    for (int y = -60; y <= 100; y += 8) {
                        long px = 0;
                        var context = new DensityFunction.SinglePointContext(x, y, Int1088.ZERO);
                        double density = random.router().finalDensity().compute(context);
                        double floodedness = random.router().fluidLevelFloodednessNoise().compute(context);
                        boolean broken = NoiseSampling.hasExtrapolation(() -> random.router().fluidLevelFloodednessNoise().compute(context));
                        var before = (Aquifer.FluidStatus) original.invoke(aquifer, px, y, 0L);
                        var after = (Aquifer.FluidStatus) filtered.invoke(aquifer, px, y, 0L);
                        assertEquals(before.fluidLevel, after.fluidLevel);
                        assertEquals(before.fluidType, after.fluidType, "Pressure inputs must stay unchanged");
                        var emitted = (net.minecraft.world.level.block.state.BlockState) output.invoke(aquifer, after, after.fluidType);
                        if (emitted.isAir() && !before.fluidType.isAir()) {
                            assertTrue(before.fluidType.is(Blocks.WATER));
                            assertNotEquals(DimensionType.WAY_BELOW_MIN_Y, before.fluidLevel);
                            assertTrue(broken);
                            assertFalse(aquifer.shouldScheduleFluidUpdate());
                        } else {
                            assertEquals(before.fluidType, emitted);
                            if (before.fluidType.is(Blocks.WATER) && !broken) healthyWater++;
                            if (before.fluidType.is(Blocks.LAVA)) lava++;
                        }
                        assertEquals(Double.doubleToLongBits(density), Double.doubleToLongBits(random.router().finalDensity().compute(context)));
                        assertEquals(Double.doubleToLongBits(floodedness), Double.doubleToLongBits(random.router().fluidLevelFloodednessNoise().compute(context)));
                    }
                }
            }
        }
        assertTrue(healthyWater > 0);
        assertTrue(lava > 0);
    }

    @Test
    void usesActualFloodednessSampleAndPreservesSharedSurfaceWater() throws Exception {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var random = RandomState.create(settings, BuiltinRegistries.NOISE, 0L);
        var sharedWater = new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
        Aquifer.FluidPicker picker = (x, y, z) -> sharedWater;
        var chunk = new NoiseChunk(4, random, 0L, 0L, settings.noiseSettings(),
                DensityFunctions.BeardifierMarker.INSTANCE, settings, picker, Blender.empty()) {
            @Override
            public int preliminarySurfaceLevel(Int1088 x, Int1088 z) {
                return 48;
            }
        };
        int[] samples = {0};
        var noise = new ImprovedNoise(new XoroshiroRandomSource(0));
        DensityFunction floodedness = new DensityFunction.SimpleFunction() {
            public double compute(DensityFunction.FunctionContext context) {
                samples[0]++;
                // Alternate broken and healthy evaluations to catch accidental re-sampling.
                noise.noise(samples[0] % 2 == 1 ? 1.0E30 : 0.0, 0, 0);
                return 1.0;
            }
            public double minValue() { return -1; }
            public double maxValue() { return 1; }
            public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException();
            }
        };
        DensityFunction zero = DensityFunctions.constant(0);
        var router = new NoiseRouter(zero, floodedness, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, zero, zero);
        var aquifer = new Aquifer.NoiseBasedAquifer(chunk, new ChunkPos(0, 0), router,
                random.aquiferRandom(), -64, 384, picker);
        Method compute = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("computeFluid", long.class, int.class, long.class);
        Method output = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("filterWaterOutput", Aquifer.FluidStatus.class,
                net.minecraft.world.level.block.state.BlockState.class);
        compute.setAccessible(true);
        output.setAccessible(true);

        var broken = (Aquifer.FluidStatus) compute.invoke(aquifer, 0L, 0, 0L);
        assertEquals(1, samples[0]);
        assertTrue(((net.minecraft.world.level.block.state.BlockState) output.invoke(aquifer, broken, broken.at(0))).isAir());
        var healthy = (Aquifer.FluidStatus) compute.invoke(aquifer, 1L, 0, 0L);
        assertEquals(2, samples[0]);
        assertEquals(Blocks.WATER.defaultBlockState(), output.invoke(aquifer, healthy, healthy.at(0)));

        // y=52 takes the surface-water early return; y=100 takes the above-surface one.
        for (int y : new int[] {52, 100}) {
            var surface = (Aquifer.FluidStatus) compute.invoke(aquifer, 0L, y, 0L);
            assertSame(sharedWater, surface);
            assertEquals(2, samples[0], "Surface returns must not sample floodedness");
            assertEquals(Blocks.WATER.defaultBlockState(), output.invoke(aquifer, surface, surface.at(63)));
        }
    }
}
