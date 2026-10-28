package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldgenDiagnosticsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void surfaceProbesShareGenerationSamplingAtUpperLimbsAndDomainEdges() {
        var high = java.math.BigInteger.ONE.shiftLeft(1024).add(java.math.BigInteger.valueOf(123));
        var coordinates = List.of(Int1088.parseDecimal(high.toString()), Int1088.parseDecimal(high.negate().toString()),
                Int1088.MIN, Int1088.MAX);
        for (var dimension : List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(dimension);
            for (var mode : GeneratorMode.values()) {
                for (var precision : CoordinatePrecision.values()) {
                    try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                        var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                        for (var x : coordinates) {
                            var z = x.not();
                            List<String> lines = new ArrayList<>();
                            WorldgenDiagnostics.append(lines, random, new BlockPos(x, 64, z), settings);
                            if (dimension.equals(NoiseGeneratorSettings.END)) {
                                assertTrue(lines.isEmpty());
                            } else {
                                assertTrue(lines.contains("Surface primary: " + BlendedNoise.formatDebugValue(
                                        SurfaceSystem.sampleNoise(random.getOrCreateNoise(Noises.SURFACE), x, 1.0, z))
                                        + " secondary: " + BlendedNoise.formatDebugValue(
                                        SurfaceSystem.sampleNoise(random.getOrCreateNoise(Noises.SURFACE_SECONDARY), x, 1.0, z))));
                                if (dimension.equals(NoiseGeneratorSettings.OVERWORLD)) {
                                    assertTrue(lines.contains("Badlands surface: " + BlendedNoise.formatDebugValue(
                                            SurfaceSystem.sampleNoise(random.getOrCreateNoise(Noises.BADLANDS_SURFACE), x, 1.0, z))
                                            + " pillar: " + BlendedNoise.formatDebugValue(
                                            SurfaceSystem.sampleNoise(random.getOrCreateNoise(Noises.BADLANDS_PILLAR), x, 0.2, z))
                                            + " roof: " + BlendedNoise.formatDebugValue(
                                            SurfaceSystem.sampleNoise(random.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF), x, 0.75, z))));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void roughnessWallIsVisibleWithoutTruncatingCoordinatesOrChangingTheRouter() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var config = new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.PRECISE);
        try (var ignored = GenerationContext.open(config)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0L);
            var x = Int1088.parseDecimal("289893046333429650128");
            String inward = null;
            String outward = null;
            for (Int1088 coordinate : List.of(x.subtract(Int1088.fromLong(4)), x)) {
                var pos = new BlockPos(coordinate, 244, Int1088.fromLong(-200));
                var context = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
                double before = random.router().finalDensity().compute(context);
                List<String> lines = new ArrayList<>();
                WorldgenDiagnostics.append(lines, random, pos, settings);
                double raw = random.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS)
                        .getValueScaled(coordinate, 1.0D, 244L, 1.0D, pos.getZ());
                String roughness = lines.stream().filter(line -> line.startsWith("Roughness ")).findFirst().orElseThrow();
                assertTrue(roughness.startsWith("Roughness " + BlendedNoise.formatDebugValue(raw)));
                assertEquals(Double.doubleToLongBits(before), Double.doubleToLongBits(random.router().finalDensity().compute(context)));
                assertTrue(lines.stream().anyMatch(line -> line.startsWith("Caves layer:") && line.contains(" selected: ")));
                assertTrue(lines.stream().noneMatch(line -> line.contains("=")));
                if (coordinate.equals(x)) outward = roughness;
                else inward = roughness;
            }
            assertNotEquals(inward, outward);
        }
    }

    @Test
    void netherOnlyShowsActiveRouterAndSurfaceFields() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.NETHER);
        try (var ignored = GenerationContext.open(GeneratorConfig.DEFAULT)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0L);
            var generator = new NoiseBasedChunkGenerator(
                    BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                    new net.minecraft.world.level.biome.FixedBiomeSource(
                            BuiltinRegistries.BIOME.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES)), settings);
            var pos = new BlockPos(123, 64, -456);
            List<String> lines = new ArrayList<>();
            generator.addDebugScreenInfo(lines, random, pos);
            assertEquals(4, lines.size());
            assertTrue(lines.get(0).matches("NoiseRouter T: .* V: .* N: .*"));
            assertTrue(lines.get(1).startsWith("Surface primary:"));
            assertTrue(lines.get(2).startsWith("Nether soul_sand_layer:"));
            assertTrue(lines.get(3).startsWith("Nether netherrack:"));
            NoiseBasedChunkGenerator.toggleWrapRouterDebugInfo();
            try {
                lines.clear();
                generator.addDebugScreenInfo(lines, random, pos);
                assertEquals(7, lines.size());
                assertEquals("(Verbose) NoiseRouter: ", lines.get(0));
                assertTrue(lines.get(1).startsWith("Temperature: "));
                assertTrue(lines.get(2).startsWith("Vegetation: "));
                assertTrue(lines.get(3).startsWith("Final Density: "));
            } finally {
                NoiseBasedChunkGenerator.toggleWrapRouterDebugInfo();
            }
        }
    }

    @Test
    void allOverworldPresetsHaveSeededContributions() {
        for (var preset : List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.LARGE_BIOMES, NoiseGeneratorSettings.AMPLIFIED)) {
            var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(preset);
            try (var ignored = GenerationContext.open(GeneratorConfig.DEFAULT)) {
                var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0);
                List<String> lines = new ArrayList<>();
                WorldgenDiagnostics.append(lines, random, new BlockPos(123, 64, -456), settings);
                assertTrue(lines.size() < 40);
                assertTrue(lines.stream().anyMatch(line -> line.startsWith("Terrain offset:")));
                assertTrue(lines.stream().noneMatch(line -> line.contains("NaN")));
                assertTrue(lines.stream().anyMatch(line -> line.startsWith("Surface primary:") && line.contains(" secondary: ")));
                assertTrue(lines.stream().anyMatch(line -> line.startsWith(" jaggedness:") && line.contains(" jagged: ")));
                assertTrue(lines.stream().anyMatch(line -> line.startsWith(" elevation:") && line.contains(" thickness: ") && line.contains(" result: ")));
                assertTrue(lines.stream().noneMatch(line -> line.contains("rareness:") || line.contains("veins [")));
                assertEquals(18, lines.size());
            }
        }
    }
}
