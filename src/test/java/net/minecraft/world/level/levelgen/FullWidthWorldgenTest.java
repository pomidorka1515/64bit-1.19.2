package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int128;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FullWidthWorldgenTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void restoreMode() { FarlandsMode.setMode(FarlandsMode.Mode.BIT_32); }

    @Test
    void columnsGenerateInEveryDimensionAndModeAtFullWidthAddresses() {
        Int128[] addresses = {Int128.of(17L, 32L), Int128.of(-19L, -32L), Int128.MIN, Int128.MAX};
        for (var dimension : java.util.List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
            NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                    new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
            LevelHeightAccessor height = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
                FarlandsMode.setMode(mode);
                RandomState state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                for (Int128 x : addresses) {
                    var column = generator.getBaseColumn(x, x, height, state);
                    assertNotNull(column, dimension + " " + mode + " " + x);
                    assertNotNull(column.getBlock(height.getMinBuildHeight()));
                }
            }
        }
    }

    @Test
    void preciseNoiseIncludesHighLimbsAndRemainsLocallyVolumetric() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        NormalNoise noise = NormalNoise.create(new LegacyRandomSource(1234L), -4, 1.0D, 0.5D);
        double first = noise.getValueScaled(17L, 37L, 0.25D, 73L, 0.25D, -19L, 41L);
        assertTrue(Double.isFinite(first));
        assertNotEquals(first, noise.getValueScaled(18L, 37L, 0.25D, 73L, 0.25D, -19L, 41L));
        assertNotEquals(first, noise.getValueScaled(17L, 38L, 0.25D, 73L, 0.25D, -19L, 41L));
        assertNotEquals(first, noise.getValueScaled(17L, 37L, 0.25D, 74L, 0.25D, -19L, 41L));
        assertNotEquals(first, noise.getValueScaled(17L, 37L, 0.25D, 73L, 0.25D, -19L, 42L));
    }

    @Test
    void completeNoiseChunksAndBiomesGenerateAtDistantAddressesInEveryMode() {
        for (var dimension : java.util.List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
            var biome = BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS);
            var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE, new FixedBiomeSource(biome), settings);
            var height = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
                FarlandsMode.setMode(mode);
                var state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                var chunk = new net.minecraft.world.level.chunk.ProtoChunk(
                        new net.minecraft.world.level.ChunkPos(Int128.of(17L, -1L), Int128.of(-19L, 0L)),
                        net.minecraft.world.level.chunk.UpgradeData.EMPTY, height, BuiltinRegistries.BIOME, null);
                var water = new Aquifer.FluidStatus(settings.value().seaLevel(), settings.value().defaultFluid());
                var lava = new Aquifer.FluidStatus(-54, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
                chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, DensityFunctions.BeardifierMarker.INSTANCE,
                        settings.value(), (x, y, z) -> y < Math.min(-54, settings.value().seaLevel()) ? lava : water,
                        net.minecraft.world.level.levelgen.blending.Blender.empty()));
                generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state,
                        net.minecraft.world.level.levelgen.blending.Blender.empty(), null, chunk).join();
                chunk.setStatus(net.minecraft.world.level.chunk.ChunkStatus.BIOMES);
                generator.fillFromNoise(Runnable::run, net.minecraft.world.level.levelgen.blending.Blender.empty(), state, null, chunk).join();
                assertEquals(biome, chunk.getNoiseBiome(0L, 0, 0L));
                assertNotNull(chunk.getBlockState(new net.minecraft.core.BlockPos(chunk.getPos().getMinBlockX(), height.getMinBuildHeight(), chunk.getPos().getMinBlockZ())));
            }
        }
    }

    @Test
    void floatingPointModesKeepTheirOwnFullMagnitudePolicies() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            double full = FarlandsMode.scaledNoiseCoordinate(17L, 37L, 0.25D);
            double expected = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.toDouble(17L, 37L) * 0.25D;
            if (mode == FarlandsMode.Mode.OFF || mode == FarlandsMode.Mode.BIT_64_PRECISE)
                expected = Math.nextDown(0x1p63);
            assertEquals(expected, full, mode.toString());
            assertNotEquals(FarlandsMode.scaledNoiseCoordinate(37L, 0.25D), full);
        }
    }

    @Test
    void randomSpreadRegionDoesNotChangeAtSignedLongSeam() {
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(34, 8,
                net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1234);
        for (Int128 edge : new Int128[] {Int128.fromLong(Long.MAX_VALUE), Int128.fromLong(Long.MIN_VALUE)}) {
            Int128 region = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.floorDivide(edge.hi(), edge.lo(), 34);
            Int128 start = region.multiplySaturated(34);
            var expected = placement.getPotentialStructureChunk(1234L, start, start);
            for (int offset = 0; offset < 34; ++offset) {
                Int128 coordinate = start.addSaturated(Int128.fromLong(offset));
                assertEquals(expected, placement.getPotentialStructureChunk(1234L, coordinate, coordinate));
            }
        }
    }

    @Test
    void randomSpreadPlacementsStayNearTheirFullWidthRegion() {
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(32, 8,
                net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1234);
        for (long hi : new long[] {17L, -19L, Long.MIN_VALUE >> 4, Long.MAX_VALUE >> 4}) {
            Int128 x = Int128.of(hi, -1L), z = Int128.of(hi, 0L);
            var actual = placement.getPotentialStructureChunk(1234L, x, z);
            var rx = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.floorDivide(x.hi(), x.lo(), 32);
            var rz = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.floorDivide(z.hi(), z.lo(), 32);
            assertEquals(rx, actual.x().shiftRight(5));
            assertEquals(rz, actual.z().shiftRight(5));
        }
    }
}
