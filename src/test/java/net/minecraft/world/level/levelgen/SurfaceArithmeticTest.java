package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.Test;

class SurfaceArithmeticTest {
    @Test
    void farlandsModesRetainRawNoiseAndOriginalIntArithmetic() {
        for (GeneratorMode mode : new GeneratorMode[] {GeneratorMode.BIT_32, GeneratorMode.BIT_64}) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    assertEquals(-1.0E20D, SurfaceArithmetic.noise(-1.0E20D));
                    assertEquals(Integer.MAX_VALUE, SurfaceArithmetic.depth(1.0E20D, 0.5D));
                    assertEquals(Integer.MIN_VALUE, SurfaceArithmetic.depth(-1.0E20D, 0.5D));
                    assertEquals(0, SurfaceArithmetic.depth(Double.NaN, 0.5D));
                    assertEquals(3, SurfaceArithmetic.depth(0.2D, 0.0D));
                    assertEquals(Integer.MAX_VALUE, SurfaceArithmetic.boundDepth(Integer.MAX_VALUE, 384));
                    assertEquals(Integer.MIN_VALUE, SurfaceArithmetic.add(Integer.MAX_VALUE, 1));
                    assertEquals(-2, SurfaceArithmetic.multiply(Integer.MAX_VALUE, 2));
                    // A huge positive depth puts the minimum surface level above the
                    // whole column, suppressing its normal grass/dirt surface rules.
                    assertEquals(Integer.MAX_VALUE - 72,
                            SurfaceArithmetic.add(SurfaceArithmetic.add(-64, Integer.MAX_VALUE), -8));
                }
            }
        }
    }

    @Test
    void patchedModeKeepsFiniteBoundedSurfaceArithmetic() {
        for (CoordinatePrecision precision : CoordinatePrecision.values()) {
            try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, precision))) {
                assertEquals(-1.0D, SurfaceArithmetic.noise(-1.0E20D));
                assertEquals(6, SurfaceArithmetic.depth(1.0E20D, 0.5D));
                assertEquals(0, SurfaceArithmetic.depth(-1.0E20D, 0.5D));
                assertEquals(3, SurfaceArithmetic.depth(Double.NaN, 0.5D));
                assertEquals(384, SurfaceArithmetic.boundDepth(Integer.MAX_VALUE, 384));
                assertEquals(Integer.MAX_VALUE, SurfaceArithmetic.add(Integer.MAX_VALUE, 1));
                assertEquals(Integer.MAX_VALUE, SurfaceArithmetic.multiply(Integer.MAX_VALUE, 2));
            }
        }
    }
}
