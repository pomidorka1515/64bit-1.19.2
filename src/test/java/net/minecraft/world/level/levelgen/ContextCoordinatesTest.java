package net.minecraft.world.level.levelgen;

import net.minecraft.core.Int1088;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GenerationPolicy;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextCoordinatesTest {
    @Test
    void narrowMatchesExistingPolicy() {
        for (GeneratorMode mode : GeneratorMode.values()) for (CoordinatePrecision precision : CoordinatePrecision.values()) {
            try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                for (long value : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
                    Int1088 coordinate = Int1088.fromLong(value);
                    assertEquals((double)value, ContextCoordinates.toDouble(coordinate));
                    assertEquals(GenerationPolicy.shiftedNoiseCoordinate(value, .125, .25), ContextCoordinates.scaled(coordinate, .125, .25));
                }
            }
        }
    }

    @Test
    void wideViewsAndCopiesAreIndependent() {
        Int1088 value = Int1088.ONE.shiftLeftSaturated(1024).negate().add(Int1088.ONE);
        long[] words = new long[19];
        value.copyFixed(words, 1);
        for (GeneratorMode mode : GeneratorMode.values()) for (CoordinatePrecision precision : CoordinatePrecision.values()) {
            try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                assertEquals(ContextCoordinates.toDouble(value), ContextCoordinates.toDouble(words, 1, 17));
                assertEquals(ContextCoordinates.scaled(value, .125, .25), ContextCoordinates.scaled(words, 1, 17, .125, .25));
                if (precision == CoordinatePrecision.PRECISE) assertEquals(ContextCoordinates.toDouble(value) * .125 + .25, ContextCoordinates.scaled(value, .125, .25));
            }
        }
        Int1088 copied = ContextCoordinates.copy(words, 1, 17);
        words[1]++;
        assertEquals(value, copied);
        assertEquals(value, ContextCoordinates.copy(value));
    }
}
