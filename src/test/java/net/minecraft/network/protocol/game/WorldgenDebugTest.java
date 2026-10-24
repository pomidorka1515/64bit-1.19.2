package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldgenDebugTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blendedNoiseAbove256DisplaysTheSampleAt256() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        try (var ignored = GenerationContext.open(GeneratorConfig.DEFAULT)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
            BlendedNoise.beginDebugCapture();
            List<String> expected;
            try {
                random.router().finalDensity().mapAll(new DensityFunction.Visitor() {
                    public DensityFunction apply(DensityFunction function) {
                        if (function instanceof BlendedNoise noise) {
                            noise.compute(new DensityFunction.SinglePointContext(123L, 256, -456L));
                        }
                        return function;
                    }
                });
                expected = List.of(BlendedNoise.getDebugString(), BlendedNoise.getDebugString2());
                assertTrue(expected.stream().noneMatch(line -> line.contains("NaN")));
            } finally {
                BlendedNoise.endDebugCapture();
            }
            for (int y : new int[] {257, 320, 1000}) {
                var packet = WorldgenDebug.sample(NoiseGeneratorSettings.OVERWORLD.location(), generator, random,
                        new BlockPos(123, y, -456), GeneratorConfig.DEFAULT);
                var data = packet.getData();
                try {
                    data.readResourceLocation();
                    int count = data.readVarInt();
                    List<String> received = new ArrayList<>();
                    for (int i = 0; i < count; i++) received.add(data.readUtf());
                    assertTrue(received.containsAll(expected), "Y=" + y);
                } finally {
                    data.release();
                }
            }
        }
    }

    @Test
    void serverPayloadIncludesEveryGeneratorDiagnosticInEveryDimension() {
        for (var dimension : List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
            var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                    new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
            try (var ignored = GenerationContext.open(GeneratorConfig.DEFAULT)) {
                var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                var pos = new BlockPos(123, 64, -456);
                List<String> expected = new ArrayList<>();
                generator.addDebugScreenInfo(expected, random, pos);

                var packet = WorldgenDebug.sample(dimension.location(), generator, random, pos, GeneratorConfig.DEFAULT);
                assertEquals(WorldgenDebug.CHANNEL, packet.getIdentifier());
                var data = packet.getData();
                try {
                    assertEquals(dimension.location(), data.readResourceLocation());
                    int count = data.readVarInt();
                    assertTrue(count > expected.size() && count <= 64);
                    List<String> received = new ArrayList<>();
                    for (int i = 0; i < count; i++) received.add(data.readUtf());
                    assertEquals(expected, received.subList(0, expected.size()));
                    for (String field : List.of("cave_cheese:", "pillar:", "Shift X:", "Shift Z:",
                            "aquifer_lava:", "aquifer_barrier:", "surface:", "surface_secondary:")) {
                        assertTrue(received.stream().anyMatch(line -> line.contains(field)), field);
                    }
                    assertFalse(data.isReadable());
                } finally {
                    data.release();
                }
            }
        }
    }
}
