package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldgenDebugTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void simplexDiagnosticsUseFullWidthCoordinates() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        var config = new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE);
        try (var ignored = GenerationContext.open(config)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
            for (long hi : new long[] {1L, -2L}) {
                var pos = new BlockPos(Int128.of(hi, 4747L), 64, Int128.of(hi, -456L));
                List<String> expected;
                List<String> truncated;
                SimplexNoise.beginDebugCapture();
                try {
                    Biome.BIOME_INFO_NOISE.getValueScaled(pos.getXHi(), pos.getXLo(), pos.getZHi(), pos.getZLo(), 1.0D, false);
                    expected = List.of(SimplexNoise.getDebugString());
                    Biome.BIOME_INFO_NOISE.getValue((double) pos.getXLo(), (double) pos.getZLo(), false);
                    truncated = List.of(SimplexNoise.getDebugString());
                    assertNotEquals(truncated, expected);
                } finally {
                    SimplexNoise.endDebugCapture();
                }
                var data = WorldgenDebug.sample(NoiseGeneratorSettings.OVERWORLD.location(), generator, random, pos, config).getData();
                try {
                    data.readResourceLocation();
                    int count = data.readVarInt();
                    List<String> received = new ArrayList<>();
                    for (int i = 0; i < count; i++) received.add(data.readUtf());
                    assertEquals(expected, received.subList(count - 1, count));
                } finally {
                    data.release();
                }
            }
        }
    }

    @Test
    void improvedOffsetsAboveSlideMatchActualDebugPayloadAt255() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        var config = new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE);
        var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
        var x = Int128.parseDecimal("1855315496539495033392");
        String expected = null;
        for (int y : new int[] {255, 256, 257, 320, 1000}) {
            var data = WorldgenDebug.sample(NoiseGeneratorSettings.OVERWORLD.location(), generator, random,
                    new BlockPos(x, y, Int128.ZERO), config).getData();
            try {
                data.readResourceLocation();
                int count = data.readVarInt();
                String offsets = null;
                for (int i = 0; i < count; ++i) {
                    String line = data.readUtf();
                    if (line.startsWith("Grid Sample point:")) offsets = line;
                }
                assertNotNull(offsets);
                if (y == 255) expected = offsets;
                else assertEquals(expected, offsets, "Y=" + y);
            } finally {
                data.release();
            }
        }
    }

    @Test
    void noiseAtAndAbove256DisplaysTheSampleAt255WithFullWidthCoordinates() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        var config = new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE);
        var x = Int128.of(1L, 4747L);
        var z = Int128.of(1L, -456L);
        try (var ignored = GenerationContext.open(config)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
            BlendedNoise.beginDebugCapture();
            ImprovedNoise.beginDebugCapture();
            List<String> expected;
            try {
                random.router().finalDensity().mapAll(new DensityFunction.Visitor() {
                    public DensityFunction apply(DensityFunction function) {
                        if (function instanceof BlendedNoise noise) {
                            noise.compute(new DensityFunction.SinglePointContext(x, 255, z));
                        }
                        return function;
                    }
                });
                expected = List.of(BlendedNoise.getDebugString(), BlendedNoise.getDebugString2());
                assertTrue(expected.stream().noneMatch(line -> line.contains("NaN")));
            } finally {
                ImprovedNoise.endDebugCapture();
                BlendedNoise.endDebugCapture();
            }
            for (int y : new int[] {256, 257, 320, 1000}) {
                var packet = WorldgenDebug.sample(NoiseGeneratorSettings.OVERWORLD.location(), generator, random,
                        new BlockPos(x, y, z), config);
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
                    assertTrue(received.get(0).startsWith("Config "));
                    assertEquals(expected, received.subList(1, 1 + expected.size()));
                    if (!dimension.equals(NoiseGeneratorSettings.END)) {
                        for (String field : List.of("Shift X:", " Z:",
                                "Aquifers lava:", "barrier:", "Surface primary:", "secondary:")) {
                            assertTrue(received.stream().anyMatch(line -> line.contains(field)), field);
                        }
                    }
                    if (dimension.equals(NoiseGeneratorSettings.OVERWORLD)) {
                        for (String field : List.of("cheese:", "Pillars ", "Roughness ",
                                "Caves layer:", "candidate:", "underground:", "selected:", "ridges: [")) {
                            assertTrue(received.stream().anyMatch(line -> line.contains(field)), field);
                        }
                    } else if (dimension.equals(NoiseGeneratorSettings.NETHER)) {
                        assertTrue(received.stream().anyMatch(line -> line.startsWith("Cave probes inactive:")));
                    } else {
                        assertTrue(received.stream().anyMatch(line -> line.startsWith("NoiseRouter E:")));
                        for (String field : List.of(" T:", " V:", " C:", " D:", " W:", " PV:",
                                "Cave probes", "Shift X:", "Aquifers", "Ores", "Surface", "Badlands",
                                "Iceberg", "Materials", "Snow/Ice", "Simplex:")) {
                            assertTrue(received.stream().noneMatch(line -> line.contains(field)), field);
                        }
                    }
                    assertFalse(data.isReadable());
                } finally {
                    data.release();
                }
            }
        }
    }

    @Test
    void verboseEndRouterOnlyShowsUsedFields() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.END);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.THE_END)), settings);
        NoiseBasedChunkGenerator.toggleWrapRouterDebugInfo();
        try (var ignored = GenerationContext.open(GeneratorConfig.DEFAULT)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
            List<String> lines = new ArrayList<>();
            generator.addDebugScreenInfo(lines, random, new BlockPos(123, 64, -456));
            assertEquals(4, lines.size());
            assertEquals("(Verbose) NoiseRouter: ", lines.get(0));
            assertTrue(lines.get(1).startsWith("Erosion: "));
            assertTrue(lines.get(2).startsWith("Initial Density: "));
            assertTrue(lines.get(3).startsWith("Final Density: "));
        } finally {
            NoiseBasedChunkGenerator.toggleWrapRouterDebugInfo();
        }
    }
}
