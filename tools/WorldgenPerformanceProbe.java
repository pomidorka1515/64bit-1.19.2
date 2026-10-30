package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.*;

/** In-memory generation benchmark. Never opens a save or changes generator policy. */
public final class WorldgenPerformanceProbe {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var context = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var state = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0);
            var biomes = MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME);
            var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE, biomes, settings);
            int repetitions = args.length == 0 ? 8 : Integer.parseInt(args[0]);
            // Warm all address-width branches before measuring.
            for (int i = 0; i < 3; ++i) for (int bits : new int[]{0, 160, 1024}) generate(generator, state, bits, i);
            try (var recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
                recording.start();
                var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                for (int bits : new int[]{0, 160, 1024}) {
                    long bytes = allocatedBytes(memory);
                    long cpu = cpuTime(memory);
                    long start = System.nanoTime();
                    long checksum = 0;
                    for (int i = 0; i < repetitions; ++i) checksum += generate(generator, state, bits, i + 10);
                    System.out.printf("bits=%d chunks=%d ms/chunk=%.3f cpu-ms/chunk=%.3f MB/chunk=%.3f checksum=%d%n", bits, repetitions,
                            (System.nanoTime() - start) / 1e6 / repetitions,
                            (cpuTime(memory) - cpu) / 1e6 / repetitions,
                            (allocatedBytes(memory) - bytes) / 1e6 / repetitions, checksum);
                }
                recording.stop();
                if (args.length > 1) recording.dump(Path.of(args[1]));
            }
        }
    }

    private static long allocatedBytes(com.sun.management.ThreadMXBean memory) {
        long total = 0;
        for (long bytes : memory.getThreadAllocatedBytes(memory.getAllThreadIds())) if (bytes > 0) total += bytes;
        return total;
    }

    private static long cpuTime(com.sun.management.ThreadMXBean memory) {
        long total = 0;
        for (long thread : memory.getAllThreadIds()) {
            long cpu = memory.getThreadCpuTime(thread);
            if (cpu > 0) total += cpu;
        }
        return total;
    }

    private static long generate(NoiseBasedChunkGenerator generator, RandomState state, int bits, int index) {
        Int1088 z = (bits == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bits)).add(Int1088.fromLong(index * 16L));
        var chunk = new ProtoChunk(new ChunkPos(Int1088.fromLong(8), z.shiftRight(4)), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384), BuiltinRegistries.BIOME, null);
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, DensityFunctions.BeardifierMarker.INSTANCE,
                settings, (x, y, worldZ) -> y < -54 ? new Aquifer.FluidStatus(-54, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()), Blender.empty()));
        generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state, Blender.empty(), null, chunk).join();
        generator.fillFromNoise(Runnable::run, Blender.empty(), state, null, chunk).join();
        long checksum = 0;
        for (var section : chunk.getSections()) {
            for (int y = 0; y < 16; ++y) for (int x = 0; x < 16; ++x) for (int localZ = 0; localZ < 16; ++localZ)
                checksum = checksum * 31 + net.minecraft.core.Registry.BLOCK.getId(section.getBlockState(x, y, localZ).getBlock());
        }
        return checksum;
    }
}
