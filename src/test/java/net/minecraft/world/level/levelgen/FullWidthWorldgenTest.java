package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FullWidthWorldgenTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @org.junit.jupiter.api.TestFactory
    @org.junit.jupiter.api.Tag("worldgen-matrix")
    java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> generatedChunksReloadAndRegenerateAcrossTheFullMatrix() {
        java.util.List<java.math.BigInteger[]> addresses = new java.util.ArrayList<>();
        addresses.add(new java.math.BigInteger[] {java.math.BigInteger.ZERO, java.math.BigInteger.ZERO});
        java.util.List<java.math.BigInteger> magnitudes = new java.util.ArrayList<>();
        for (int bit : new int[] {53, 63, 127, 128})
            magnitudes.add(java.math.BigInteger.ONE.shiftLeft(bit).add(java.math.BigInteger.ONE));
        magnitudes.add(java.math.BigInteger.ONE.shiftLeft(1023));
        magnitudes.add(java.math.BigInteger.ONE.shiftLeft(1024).subtract(java.math.BigInteger.ONE.shiftLeft(971)));
        magnitudes.add(java.math.BigInteger.ONE.shiftLeft(1024));
        magnitudes.add(java.math.BigInteger.ONE.shiftLeft(1087).subtract(java.math.BigInteger.ONE));
        for (var magnitude : magnitudes) {
            addresses.add(new java.math.BigInteger[] {magnitude, magnitude});
            addresses.add(new java.math.BigInteger[] {magnitude.negate(), magnitude.negate()});
            addresses.add(new java.math.BigInteger[] {magnitude, magnitude.negate()});
            addresses.add(new java.math.BigInteger[] {magnitude.negate(), magnitude});
        }
        var min = java.math.BigInteger.ONE.shiftLeft(1087).negate();
        addresses.add(new java.math.BigInteger[] {min, min});
        addresses.add(new java.math.BigInteger[] {min, min.negate().subtract(java.math.BigInteger.ONE)});
        addresses.add(new java.math.BigInteger[] {min.negate().subtract(java.math.BigInteger.ONE), min});
        java.util.List<org.junit.jupiter.api.DynamicTest> tests = new java.util.ArrayList<>();
        for (var dimension : java.util.List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            for (GeneratorMode mode : GeneratorMode.values()) {
                for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                    for (int index = 0; index < addresses.size(); index++) {
                        var address = addresses.get(index);
                        String name = dimension.location() + "/" + mode + "/" + precision + "/" + index;
                        // @Timeout on a factory only times test discovery, not its dynamic cases.
                        tests.add(org.junit.jupiter.api.DynamicTest.dynamicTest(name, () -> assertTimeoutPreemptively(
                                java.time.Duration.ofMinutes(2), () -> {
                            try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                                var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
                                var generator = matrixGenerator(dimension);
                                var pos = new net.minecraft.world.level.ChunkPos(new net.minecraft.core.BlockPos(
                                        Int1088.parseDecimal(address[0].toString()), 0, Int1088.parseDecimal(address[1].toString())));
                                // Independent floor division: matrix entries are block addresses, not chunk indices.
                                assertEquals(address[0].shiftRight(4).toString(), pos.x().toDecimalString());
                                assertEquals(address[1].shiftRight(4).toString(), pos.z().toDecimalString());
                                var height = settings.value().noiseSettings();
                                var chunk = new net.minecraft.world.level.chunk.ProtoChunk(pos,
                                        net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                                        LevelHeightAccessor.create(height.minY(), height.height()), BuiltinRegistries.BIOME, null);
                                var state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                                generateNoise(generator, settings.value(), state, chunk);
                                var before = snapshot(chunk, state);
                                var loaded = WorldgenReloadFixture.reload(directory.resolve(name), chunk, height);
                                assertEquals(pos, loaded.getPos());
                                assertEquals(before, snapshot(loaded, RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L)));
                                // Re-run actual biome/noise stages with a deserialized chunk and fresh sampler/cache state.
                                var reloadedState = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                                generateNoise(matrixGenerator(dimension), settings.value(), reloadedState, loaded);
                                assertEquals(pos, loaded.getPos());
                                assertEquals(before, snapshot(loaded, reloadedState));
                            }
                        })));
                    }
                }
            }
        }
        return tests.stream();
    }

    private static NoiseBasedChunkGenerator matrixGenerator(net.minecraft.resources.ResourceKey<NoiseGeneratorSettings> dimension) {
        net.minecraft.world.level.biome.BiomeSource biomes = dimension.equals(NoiseGeneratorSettings.END)
                ? new net.minecraft.world.level.biome.TheEndBiomeSource(BuiltinRegistries.BIOME)
                : (dimension.equals(NoiseGeneratorSettings.NETHER)
                        ? net.minecraft.world.level.biome.MultiNoiseBiomeSource.Preset.NETHER
                        : net.minecraft.world.level.biome.MultiNoiseBiomeSource.Preset.OVERWORLD)
                        .biomeSource(BuiltinRegistries.BIOME);
        return new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE, biomes,
                BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension));
    }

    private static void generateNoise(NoiseBasedChunkGenerator generator, NoiseGeneratorSettings settings,
            RandomState state, net.minecraft.world.level.chunk.ProtoChunk chunk) {
        var water = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        var lava = new Aquifer.FluidStatus(-54, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
        chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, DensityFunctions.BeardifierMarker.INSTANCE,
                settings, (x, y, z) -> y < Math.min(-54, settings.seaLevel()) ? lava : water,
                net.minecraft.world.level.levelgen.blending.Blender.empty()));
        generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state,
                net.minecraft.world.level.levelgen.blending.Blender.empty(), null, chunk)
                .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).join();
        chunk.setStatus(net.minecraft.world.level.chunk.ChunkStatus.BIOMES);
        generator.fillFromNoise(Runnable::run, net.minecraft.world.level.levelgen.blending.Blender.empty(), state, null, chunk)
                .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).join();
        // These are real BIOMES/NOISE stage tests, not a claim of FULL/live-server generation.
        chunk.setStatus(net.minecraft.world.level.chunk.ChunkStatus.NOISE);
    }

    private record ChunkSnapshot(net.minecraft.world.level.chunk.ChunkStatus status, int nonAir,
            java.util.List<net.minecraft.world.level.block.state.BlockState> blocks,
            java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> biomes,
            java.util.List<Long> densityBits) {
        @Override public String toString() {
            return "ChunkSnapshot[status=" + status + ", nonAir=" + nonAir + ", blockHash=" + blocks.hashCode()
                    + ", biomeHash=" + biomes.hashCode() + ", densityBits=" + densityBits + "]";
        }
    }

    private static ChunkSnapshot snapshot(net.minecraft.world.level.chunk.ProtoChunk chunk, RandomState state) {
        int nonAir = 0;
        var blocks = new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
        var biomes = new java.util.ArrayList<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>();
        for (var section : chunk.getSections()) {
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var block = section.getBlockState(x, y, z);
                blocks.add(block);
                if (!block.isAir()) nonAir++;
            }
            for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                biomes.add(section.getNoiseBiome(x, y, z).unwrapKey().orElseThrow());
        }
        var bits = new java.util.ArrayList<Long>();
        for (int[] offset : new int[][] {{0, 0, 0}, {7, 73, 11}, {15, 127, 15}}) {
            var point = new DensityFunction.SinglePointContext(chunk.getPos().getMinBlockX().addSaturated(Int1088.fromLong(offset[0])),
                    offset[1], chunk.getPos().getMinBlockZ().addSaturated(Int1088.fromLong(offset[2])));
            for (var density : java.util.List.of(state.router().temperature(), state.router().vegetation(),
                    state.router().continents(), state.router().initialDensityWithoutJaggedness(), state.router().finalDensity()))
                bits.add(Double.doubleToRawLongBits(density.compute(point)));
        }
        return new ChunkSnapshot(chunk.getStatus(), nonAir, blocks, biomes, bits);
    }

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void restoreMode() { FarlandsMode.setMode(FarlandsMode.Mode.BIT_32); }

    @Test
    void columnsGenerateInEveryDimensionAndModeAtFullWidthAddresses() {
        Int1088[] addresses = {Int1088.of(32L, 17L), Int1088.of(-32L, -19L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
        for (var dimension : java.util.List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
            NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                    new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
            LevelHeightAccessor height = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            for (GeneratorMode mode : GeneratorMode.values()) {
              for (CoordinatePrecision precision : CoordinatePrecision.values()) {
               try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                RandomState state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                for (Int1088 x : addresses) {
                    var column = generator.getBaseColumn(x, x, height, state);
                    assertNotNull(column, dimension + " " + mode + " " + x);
                    assertNotNull(column.getBlock(height.getMinBuildHeight()));
                }
               }
              }
            }
        }
    }

    @Test
    void preciseNoiseIncludesHighLimbsAndRemainsLocallyVolumetric() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        NormalNoise noise = NormalNoise.create(new LegacyRandomSource(1234L), -4, 1.0D, 0.5D);
        double first = noise.getValueScaled(Int1088.of(37L, 17L), 0.25D, 73L, 0.25D, Int1088.of(41L, -19L));
        assertTrue(Double.isFinite(first));
        assertNotEquals(first, noise.getValueScaled(Int1088.of(37L, 18L), 0.25D, 73L, 0.25D, Int1088.of(41L, -19L)));
        assertNotEquals(first, noise.getValueScaled(Int1088.of(38L, 17L), 0.25D, 73L, 0.25D, Int1088.of(41L, -19L)));
        assertNotEquals(first, noise.getValueScaled(Int1088.of(37L, 17L), 0.25D, 74L, 0.25D, Int1088.of(41L, -19L)));
        assertNotEquals(first, noise.getValueScaled(Int1088.of(37L, 17L), 0.25D, 73L, 0.25D, Int1088.of(42L, -19L)));
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
                        new net.minecraft.world.level.ChunkPos(Int1088.of(-1L, 17L), Int1088.of(0L, -19L)),
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
            double full = ContextCoordinates.scaled(Int1088.of(37L, 17L), 0.25D, 0.0D);
            double expected = Int1088.of(37L, 17L).toDoubleRounded() * 0.25D;
            if (mode == FarlandsMode.Mode.OFF)
                expected = Math.nextDown(0x1p63);
            assertEquals(expected, full, mode.toString());
            assertNotEquals(FarlandsMode.scaledNoiseCoordinate(37L, 0.25D), full);
        }
    }

    @Test
    void randomSpreadRegionDoesNotChangeAtSignedLongSeam() {
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(34, 8,
                net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1234);
        for (Int1088 edge : new Int1088[] {Int1088.fromLong(Long.MAX_VALUE), Int1088.fromLong(Long.MIN_VALUE)}) {
            Int1088 region = edge.floorDivideSaturated(34);
            Int1088 start = region.multiplySaturated(34);
            var expected = placement.getPotentialStructureChunk(1234L, start, start);
            for (int offset = 0; offset < 34; ++offset) {
                Int1088 coordinate = start.addSaturated(Int1088.fromLong(offset));
                assertEquals(expected, placement.getPotentialStructureChunk(1234L, coordinate, coordinate));
            }
        }
    }

    @Test
    void randomSpreadPlacementsStayNearTheirFullWidthRegion() {
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(32, 8,
                net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1234);
        for (long hi : new long[] {17L, -19L, Long.MIN_VALUE >> 4, Long.MAX_VALUE >> 4}) {
            Int1088 x = Int1088.of(-1L, hi), z = Int1088.of(0L, hi);
            var actual = placement.getPotentialStructureChunk(1234L, x, z);
            var rx = x.floorDivideSaturated(32);
            var rz = z.floorDivideSaturated(32);
            assertEquals(rx, actual.x().shiftRight(5));
            assertEquals(rz, actual.z().shiftRight(5));
        }
    }
}
