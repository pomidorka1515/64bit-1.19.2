package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndIslandDensityFunctionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void outerIslandsSurviveAtLargeCoordinates() {
        FarlandsMode.Mode previous = FarlandsMode.getMode();
        boolean rings = EndRingsMode.isEnabled();
        try {
            FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
            EndRingsMode.setEnabled(false);
            DensityFunction density = new DensityFunctions.EndIslandDensityFunction(1234L);
            long[] origins = {1L << 36, 1L << 56, 1L << 62, -(1L << 62), Long.MAX_VALUE - 4096, Long.MIN_VALUE};
            for (long origin : origins) {
                int land = 0;
                for (int x = 0; x < 32; ++x) {
                    for (int z = 0; z < 32; ++z) {
                        double value = density.compute(new DensityFunction.SinglePointContext(origin + x * 64L, 0, origin + z * 64L));
                        assertTrue(Double.isFinite(value));
                        if (value > 0.0D) {
                            ++land;
                        }
                    }
                }
                assertTrue(land > 50, "outer islands disappeared at " + origin + ": " + land);
                assertTrue(land < 950, "outer islands lost their void gaps at " + origin + ": " + land);
            }
        } finally {
            FarlandsMode.setMode(previous);
            EndRingsMode.setEnabled(rings);
        }
    }
}
