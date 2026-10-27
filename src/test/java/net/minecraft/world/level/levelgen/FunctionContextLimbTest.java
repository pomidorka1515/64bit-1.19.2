package net.minecraft.world.level.levelgen;

import net.minecraft.core.Int1088;
import net.minecraft.SharedConstants;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionContextLimbTest {
    @Test
    void exposesEveryLimbAndSignExtension() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024).addSaturated(Int1088.fromLong(7));
        Int1088 z = Int1088.ONE.shiftLeftSaturated(511).negate();
        DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(x, 42, z);
        assertEquals(x.limbCount(), context.blockXLimbCount());
        assertEquals(z.limbCount(), context.blockZLimbCount());
        assertFalse(context.blockXFitsLong());
        assertFalse(context.blockZFitsLong());
        assertEquals(42, context.blockY());
        for (int i = 0; i < Int1088.LIMBS; ++i) {
            assertEquals(x.limb(i), context.blockXLimb(i));
            assertEquals(z.limb(i), context.blockZLimb(i));
        }
    }

    @Test
    void narrowValuesSignExtend() {
        DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(-1, 0, 3);
        assertTrue(context.blockXFitsLong());
        assertTrue(context.blockZFitsLong());
        assertEquals(-1, context.blockXLow());
        assertEquals(-1, context.blockXLimb(16));
        assertEquals(0, context.blockZLimb(16));
    }

    @Test
    void noiseChunkCarriesEveryLimbAndSaturatesAtBothEdges() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            NoiseGeneratorSettings settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            RandomState random = RandomState.create(settings, BuiltinRegistries.NOISE, 0L);
            NoiseChunk chunk = new NoiseChunk(1, random, Int1088.ZERO, Int1088.ZERO, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings,
                    (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
            var xField = NoiseChunk.class.getDeclaredField("cellStartBlockX");
            var zField = NoiseChunk.class.getDeclaredField("cellStartBlockZ");
            xField.setAccessible(true);
            zField.setAccessible(true);
            for (int bit = 64; bit < Int1088.BITS - 1; bit += 64) {
                Int1088 seam = Int1088.ONE.shiftLeftSaturated(bit);
                Int1088 before = seam.subtractSaturated(Int1088.ONE);
                xField.set(chunk, before);
                zField.set(chunk, seam);
                chunk.inCellX = 2L;
                chunk.inCellZ = -2L;
                assertContextAxis(chunk, before.addSaturated(Int1088.fromLong(2)), true);
                assertContextAxis(chunk, seam.subtractSaturated(Int1088.fromLong(2)), false);
                // Changing offsets without changing the cell must invalidate cached limbs.
                chunk.inCellX = 0L;
                assertContextAxis(chunk, before, true);
            }
            xField.set(chunk, Int1088.MAX);
            zField.set(chunk, Int1088.MIN);
            chunk.inCellX = 1;
            chunk.inCellZ = -1;
            assertContextAxis(chunk, Int1088.MAX, true);
            assertContextAxis(chunk, Int1088.MIN, false);
        }
    }

    private static void assertContextAxis(DensityFunction.FunctionContext context, Int1088 expected, boolean x) {
        assertEquals(expected.limbCount(), x ? context.blockXLimbCount() : context.blockZLimbCount());
        for (int i = 0; i < Int1088.LIMBS; i++) {
            assertEquals(expected.limb(i), x ? context.blockXLimb(i) : context.blockZLimb(i), "limb " + i);
        }
    }
}
