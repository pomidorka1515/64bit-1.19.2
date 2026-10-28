package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.*;

/** Deterministic BIOMES + NOISE stage benchmark; excludes features, lighting and rendering. */
public final class ChunkGenerationPerfProbe {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                    MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME), settings);
            var state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
            var height = settings.value().noiseSettings();
            var water = new Aquifer.FluidStatus(settings.value().seaLevel(), settings.value().defaultFluid());
            var lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
            for (int bit : new int[]{0, 512}) {
                Int1088 origin = bit == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bit);
                for (int round = 0; round < 8; ++round) {
                    long elapsed = 0, checksum = 1;
                    for (int index = 0; index < 4; ++index) {
                        var pos = new ChunkPos(origin.addSaturated(Int1088.fromLong(index)), origin.negate());
                        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY,
                                LevelHeightAccessor.create(height.minY(), height.height()), BuiltinRegistries.BIOME, null);
                        long start = System.nanoTime();
                        chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state,
                                DensityFunctions.BeardifierMarker.INSTANCE, settings.value(),
                                (x, y, z) -> y < -54 ? lava : water, Blender.empty()));
                        generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state, Blender.empty(), null, chunk).join();
                        chunk.setStatus(ChunkStatus.BIOMES);
                        generator.fillFromNoise(Runnable::run, Blender.empty(), state, null, chunk).join();
                        elapsed += System.nanoTime() - start;
                        for (var section : chunk.getSections()) {
                            for (int y = 0; y < 16; ++y) for (int z = 0; z < 16; ++z) for (int x = 0; x < 16; ++x)
                                checksum = checksum * 31 + Block.getId(section.getBlockState(x, y, z));
                            for (int y = 0; y < 4; ++y) for (int z = 0; z < 4; ++z) for (int x = 0; x < 4; ++x)
                                checksum = checksum * 31 + section.getNoiseBiome(x, y, z).unwrapKey().orElseThrow().location().hashCode();
                        }
                    }
                    System.out.printf("bit %d round %d: %.2f ms/chunk; checksum %d%n", bit, round, elapsed / 4000000.0, checksum);
                }
            }
        }
    }
}
