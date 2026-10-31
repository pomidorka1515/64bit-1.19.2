package net.minecraft.world.level.levelgen.synth;

import java.util.List;
import net.minecraft.core.Int1088;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class WideNoiseEntryPointTest {
    @BeforeAll
    static void bootstrap() { net.minecraft.server.Bootstrap.bootStrap(); }

    @Test
    void distantUnshiftedTerrainDoesNotAliasAtPowersOfTwo() {
        NormalNoise noise = NormalNoise.create(RandomSource.create(0), -9, 1, 1, 1);
        try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            for (int sign : new int[]{-1, 1}) {
                for (boolean zAxis : new boolean[]{false, true}) {
                    double[] first = new double[32], second = new double[32];
                    for (int i = 0; i < first.length; ++i) {
                        Int1088 a = Int1088.ONE.shiftLeftSaturated(1030).multiplySaturated(sign).add(Int1088.fromLong(i * 17));
                        Int1088 b = Int1088.ONE.shiftLeftSaturated(1036).multiplySaturated(sign).add(Int1088.fromLong(i * 17));
                        first[i] = noise.getValueScaled(zAxis ? Int1088.ZERO : a, .25, 64, .25, zAxis ? a : Int1088.ZERO);
                        second[i] = noise.getValueScaled(zAxis ? Int1088.ZERO : b, .25, 64, .25, zAxis ? b : Int1088.ZERO);
                    }
                    assertFalse(java.util.Arrays.equals(first, second), "Repeated unshifted terrain: sign=" + sign + ", Z=" + zAxis);
                }
            }
        }
    }

    @Test
    void distantUnshiftedNoiseDoesNotRepeatInsideOneHighWordRegion() {
        PerlinNoise noise = PerlinNoise.create(RandomSource.create(0), 0, 1);
        try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            Int1088 origin = Int1088.ONE.shiftLeftSaturated(1030);
            boolean differs = false;
            for (int i = 0; i < 32; ++i) {
                Int1088 a = origin.add(Int1088.fromLong(i));
                Int1088 b = a.add(Int1088.fromLong(256));
                differs |= noise.getValueScaled(a, 1, 0, Int1088.ZERO, 1, .3, Int1088.ZERO, .4)
                        != noise.getValueScaled(b, 1, 0, Int1088.ZERO, 1, .3, Int1088.ZERO, .4);
            }
            assertTrue(differs, "Discarding low-word bits above bit 7 creates a 256-cell repeat");
        }
    }
    @Test
    void narrowPathsPreserveExistingValues() {
        NormalNoise normal = NormalNoise.create(RandomSource.create(4), -2, 1, 1);
        PerlinNoise perlin = PerlinNoise.create(RandomSource.create(5), -2, 1, 1);
        SimplexNoise simplex = new SimplexNoise(RandomSource.create(6));
        PerlinSimplexNoise combined = new PerlinSimplexNoise(RandomSource.create(7), List.of(-2, -1, 0));
        for (GeneratorMode mode : GeneratorMode.values()) for (CoordinatePrecision precision : CoordinatePrecision.values()) {
            try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                for (long x : new long[]{-12345, -1, 0, 1, 12345, Long.MAX_VALUE}) {
                    long z = ~x;
                    Int1088 a = Int1088.fromLong(x), b = Int1088.fromLong(z), y = Int1088.fromLong(13);
                    assertEquals(normal.getValueScaled(x, .03, 13, .04, z), normal.getValueScaled(a, .03, 13, .04, b));
                    assertEquals(perlin.getValueScaled(x, .03, .2, 13, .04, .3, z, .4), perlin.getValueScaled(a, .03, .2, y, .04, .3, b, .4));
                    assertEquals(simplex.getValueAt(x, z), simplex.getValueAt(a, b));
                    assertEquals(simplex.getValueScaled(x, z, .03, -2, .1, .2), simplex.getValueScaled(a, b, .03, -2, .1, .2));
                    assertEquals(combined.getValueScaled(x, z, .03, true), combined.getValueScaled(a, b, .03, true));
                }
            }
        }
    }

    @Test
    void wideViewsMatchValuesAtAllModes() {
        NormalNoise normal = NormalNoise.create(RandomSource.create(14), -2, 1, 1);
        PerlinNoise perlin = PerlinNoise.create(RandomSource.create(15), -2, 1, 1);
        SimplexNoise simplex = new SimplexNoise(RandomSource.create(16));
        PerlinSimplexNoise combined = new PerlinSimplexNoise(RandomSource.create(17), List.of(-2, -1, 0));
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024).add(Int1088.fromLong(7));
        Int1088 z = Int1088.ONE.shiftLeftSaturated(127).negate();
        Int1088 y = Int1088.fromLong(13);
        long[] a = new long[19], b = new long[19], c = new long[19];
        x.copyFixed(a, 1); y.copyFixed(b, 1); z.copyFixed(c, 1);
        for (GeneratorMode mode : GeneratorMode.values()) for (CoordinatePrecision precision : CoordinatePrecision.values()) {
            try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                assertEquals(normal.getValueScaledAxes(x, y, z, .125), normal.getValueScaledAxes(a, 1, 17, b, 1, 17, c, 1, 17, .125));
                assertEquals(normal.getValueScaled(x, .125, 13, .04, z), normal.getValueScaled(a, 1, 17, .125, 13, .04, c, 1, 17));
                assertEquals(normal.getValueScaledShifted(x, .125, .2, 13, z, .4), normal.getValueScaledShifted(a, 1, 17, .125, .2, 13, c, 1, 17, .4));
                assertEquals(perlin.getValueScaled(x, .125, .2, y, .04, .3, z, .4), perlin.getValueScaled(a, 1, 17, .125, .2, b, 1, 17, .04, .3, c, 1, 17, .4));
                assertEquals(simplex.getValueAt(x, z), simplex.getValueAt(a, 1, 17, c, 1, 17));
                assertEquals(simplex.getValueScaled(x, z, .125, -2, .1, .2), simplex.getValueScaled(a, 1, 17, c, 1, 17, .125, -2, .1, .2));
                assertEquals(combined.getValueScaled(x, z, .125, true), combined.getValueScaled(a, 1, 17, c, 1, 17, .125, true));
            }
        }
    }
}
