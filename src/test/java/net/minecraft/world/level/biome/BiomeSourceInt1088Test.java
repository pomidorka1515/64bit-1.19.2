package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.Codec;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.util.RandomSource;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

class BiomeSourceInt1088Test {
    @Test
    void closest3dSearchReturnsExactAnchor() {
        Holder<Biome> biome = new Holder.Direct<>(null);
        BlockPos origin = new BlockPos(Int1088.of(Long.MAX_VALUE, 123L), 70, Int1088.of(Long.MIN_VALUE, -98L));
        BiomeSource source = new BiomeSource(List.of(biome)) {
            protected Codec<? extends BiomeSource> codec() {
                throw new UnsupportedOperationException();
            }

            public Holder<Biome> getNoiseBiome(long x, int y, long z, Climate.Sampler sampler) {
                throw new AssertionError("3d search narrowed before provider dispatch");
            }

            public Holder<Biome> getNoiseBiome(Int1088 x, int y, Int1088 z, Climate.Sampler sampler) {
                assertEquals(origin.getX().shiftRight(2), x);
                assertEquals(origin.getZ().shiftRight(2), z);
                return biome;
            }
        };
        LevelReader level = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    default -> throw new AssertionError(method.getName());
                });
        assertEquals(origin, source.findClosestBiome3d(origin, 0, 1, 1, value -> true, null, level).getFirst());
    }

    private static BiomeSource source() {
        return new BiomeSource(List.<Holder<Biome>>of()) {
            protected Codec<? extends BiomeSource> codec() {
                throw new UnsupportedOperationException();
            }

            public Holder<Biome> getNoiseBiome(long x, int y, long z, Climate.Sampler sampler) {
                return null;
            }

            public Holder<Biome> getNoiseBiome(Int1088 x, int y, Int1088 z, Climate.Sampler sampler) {
                return null;
            }
        };
    }

    @Test
    void horizontalSearchKeepsHighHalvesAndCrossesLowWordSeams() {
        Int1088 x = Int1088.of(-4L, 123L);
        Int1088 z = Int1088.of(0L, -98L);
        BlockPos result = source().findBiomeHorizontal(x, 70, z, 4, 1, biome -> true,
                RandomSource.create(1L), true, null).getFirst();
        assertEquals(new BlockPos(x, 70, z), result);

        // Only accept the east quart cell, across the low-word carry.
        BiomeSource exact = new BiomeSource(List.<Holder<Biome>>of()) {
            protected Codec<? extends BiomeSource> codec() {
                throw new UnsupportedOperationException();
            }

            public Holder<Biome> getNoiseBiome(long x, int y, long z, Climate.Sampler sampler) {
                throw new AssertionError("search narrowed before provider dispatch");
            }

            public Holder<Biome> getNoiseBiome(Int1088 qx, int y, Int1088 qz, Climate.Sampler sampler) {
                if (qx.equals(Int1088.of(0L, 124L).shiftRight(2)) && qz.equals(z.shiftRight(2))) {
                    throw new Sampled();
                }
                return null;
            }
        };
        assertThrows(Sampled.class, () -> exact.findBiomeHorizontal(x, 70, z, 4, 1,
                biome -> false, RandomSource.create(1L), false, null));
    }

    @Test
    void samplingProjectsOnlyAtDensityBoundary() {
        var zero = DensityFunctions.zero();
        DensityFunction probe = new DensityFunction.SimpleFunction() {
            public double compute(DensityFunction.FunctionContext context) {
                assertEquals(68L, context.blockXLimb(0));
                assertEquals(20, context.blockY());
                assertEquals(-76L, context.blockZLimb(0));
                throw new Sampled();
            }

            public double minValue() {
                return 0.0D;
            }

            public double maxValue() {
                return 0.0D;
            }

            public KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException();
            }
        };
        Climate.Sampler sampler = new Climate.Sampler(probe, zero, zero, zero, zero, zero, List.of());
        assertThrows(Sampled.class, () -> sampler.sample(Int1088.of(17L, 42L), 5, Int1088.of(-19L, -42L)));
        assertEquals(BlockPos.ZERO, sampler.findSpawnPosition());
    }

    @Test
    void horizontalSearchDoesNotWrapAtInt1088Edges() {
        BlockPos min = source().findBiomeHorizontal(Int1088.MIN, 70, Int1088.MIN, 4, 1,
                biome -> true, RandomSource.create(1L), true, null).getFirst();
        BlockPos max = source().findBiomeHorizontal(Int1088.MAX, 70, Int1088.MAX, 4, 1,
                biome -> true, RandomSource.create(1L), true, null).getFirst();
        assertEquals(new BlockPos(Int1088.MIN, 70, Int1088.MIN), min);
        assertEquals(new BlockPos(Int1088.MAX.shiftRight(2).shiftLeftSaturated(2), 70,
                Int1088.MAX.shiftRight(2).shiftLeftSaturated(2)), max);
    }

    private static class Sampled extends RuntimeException {
    }
}
