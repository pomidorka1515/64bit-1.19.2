package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
    void roughnessWallIsVisibleWithoutTruncatingCoordinatesOrChangingTheRouter() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var config = new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.PRECISE);
        try (var ignored = GenerationContext.open(config)) {
            var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0L);
            var x = Int128.parseDecimal("289893046333429650128");
            String inward = null;
            String outward = null;
            for (Int128 coordinate : List.of(x.subtract(Int128.of(0, 4)), x)) {
                var pos = new BlockPos(coordinate, 244, Int128.fromLong(-200));
                var context = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
                double before = random.router().finalDensity().compute(context);
                List<String> lines = new ArrayList<>();
                WorldgenDiagnostics.append(lines, random, pos, settings);
                double raw = random.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS)
                        .getValueScaled(coordinate.hi(), coordinate.lo(), 1.0D, 244L, 1.0D, pos.getZHi(), pos.getZLo());
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
