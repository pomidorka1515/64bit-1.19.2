package net.minecraft.world.level.levelgen.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.ContextCoordinates;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NoiseRouterDebugFormattingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void displayedRouterAndProbesUseGenerationArithmeticForEveryModeAndPrecision() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        List<BigInteger> coordinates = new ArrayList<>(List.of(BigInteger.ZERO, BigInteger.valueOf(-123)));
        for (int bit : new int[] {53, 63, 127, 128, 512, 1024}) {
            var value = BigInteger.ONE.shiftLeft(bit).add(BigInteger.ONE);
            coordinates.add(value);
            coordinates.add(value.negate());
        }
        coordinates.add(BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE));
        coordinates.add(BigInteger.ONE.shiftLeft(1087).negate());
        for (var mode : GeneratorMode.values()) {
            for (var precision : CoordinatePrecision.values()) {
                try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 1234L);
                    var router = random.router();
                    for (var coordinate : coordinates) {
                        var x = Int1088.parseDecimal(coordinate.toString());
                        var z = Int1088.parseDecimal(coordinate.not().toString());
                        var context = new DensityFunction.SinglePointContext(x, 64, z);
                        double before = router.finalDensity().compute(context);
                        List<String> lines = new ArrayList<>();
                        generator.addDebugScreenInfo(lines, random, new BlockPos(x, 64, z));
                        assertEquals("NoiseRouter T: " + format(router.temperature().compute(context), -1.5, 1.5)
                                + " V: " + format(router.vegetation().compute(context), -1.5, 1.5)
                                + " C: " + format(router.continents().compute(context), -1.5, 1.5)
                                + " E: " + format(router.erosion().compute(context), -1.5, 1.5), lines.get(0));
                        double ridges = router.ridges().compute(context);
                        double folded = -(Math.abs(Math.abs(ridges) - 2.0 / 3.0) - 1.0 / 3.0) * 3.0;
                        assertEquals(" D: " + format(router.depth().compute(context), -2.5, 2.5)
                                + " W: " + format(ridges, -1.5, 1.5) + " PV: " + format(folded, -1, 1)
                                + " AS: " + format(router.initialDensityWithoutJaggedness().compute(context), -64, 64)
                                + " N: " + format(before, -3, 3), lines.get(1));
                        var noise = random.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS);
                        boolean exact = precision == CoordinatePrecision.PRECISE
                                && (!x.fitsLong() || !z.fitsLong()
                                || PreciseNoiseCoordinate.needsPrecisePath(x.lowLimb())
                                || PreciseNoiseCoordinate.needsPrecisePath(z.lowLimb()));
                        double roughness = exact ? noise.getValueScaled(x, 1.0, 64L, 1.0, z)
                                : noise.getValue(ContextCoordinates.scaled(x, 1.0, 0), 64,
                                        ContextCoordinates.scaled(z, 1.0, 0));
                        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                                "Roughness " + BlendedNoise.formatDebugValue(roughness) + " modulator:")),
                                () -> mode + "/" + precision + " at " + coordinate + ": " + lines);
                        assertEquals(Double.doubleToLongBits(before),
                                Double.doubleToLongBits(router.finalDensity().compute(context)), "display must not mutate routing");
                    }
                }
            }
        }
    }

    private static String format(double value, double min, double max) {
        if (Double.isFinite(value) && value >= min && value <= max) {
            return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
        }
        return "\247c" + BlendedNoise.formatScientific(value) + "\247r";
    }
}
