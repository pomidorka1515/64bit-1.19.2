package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlendedNoisePrecisionTest {
    private final FarlandsMode.Mode previousMode = FarlandsMode.getMode();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreMode() {
        FarlandsMode.setMode(this.previousMode);
    }

    private static BlendedNoise netherNoise() {
        return new BlendedNoise(new LegacyRandomSource(1234L), 0.25D, 0.375D, 80.0D, 60.0D, 8.0D);
    }

    @Test
    void preciseSamplingCapturesF3DiagnosticsWithoutChangingDensity() {
        BlendedNoise noise = netherNoise();
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        for (long coordinate : new long[] {1L << 50, -(1L << 50), Long.MAX_VALUE, Long.MIN_VALUE}) {
            DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(coordinate, 64, coordinate);
            double expected = noise.compute(context);
            BlendedNoise.beginDebugCapture();
            ImprovedNoise.beginDebugCapture();
            try {
                assertEquals(expected, noise.compute(context));
                for (String line : new String[] {BlendedNoise.getDebugString(), BlendedNoise.getDebugString2(),
                        ImprovedNoise.getDebugString(), ImprovedNoise.getDebugString2()}) {
                    assertTrue(!line.contains("NaN") && !line.contains("Not evaluated"), line);
                }
            } finally {
                ImprovedNoise.endDebugCapture();
                BlendedNoise.endDebugCapture();
            }
        }
    }

    @Test
    void scaledNetherCoordinatesUsePreciseSamplingBeforeBlockPrecisionLimit() throws Exception {
        BlendedNoise noise = netherNoise();
        Method precise = BlendedNoise.class.getDeclaredMethod("computePrecise", DensityFunction.FunctionContext.class);
        precise.setAccessible(true);
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        long[] coordinates = {1L << 46, -(1L << 46), 1L << 50, -(1L << 50), 1L << 53, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long coordinate : coordinates) {
            for (int y = 32; y <= 96; y += 8) {
                DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(coordinate, y, 17L);
                double expected = (double) precise.invoke(noise, context);
                assertTrue(Double.isFinite(expected));
                assertEquals(expected, noise.compute(context), "X=" + coordinate + ", Y=" + y);
                context = new DensityFunction.SinglePointContext(17L, y, coordinate);
                assertEquals((double) precise.invoke(noise, context), noise.compute(context), "Z=" + coordinate + ", Y=" + y);
            }
        }
    }

    @Test
    void highCoordinateNetherRetainsCavities() {
        BlendedNoise noise = netherNoise();
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
        for (long origin : new long[] {1L << 50, -(1L << 50), 1L << 60, -(1L << 60)}) {
            boolean hasAir = false;
            boolean hasRock = false;
            for (int x = 0; x <= 128; x += 4) {
                for (int z = 0; z <= 128; z += 4) {
                    for (int y = 40; y <= 96; y += 8) {
                        double density = noise.compute(new DensityFunction.SinglePointContext(origin + x, y, origin + z));
                        assertTrue(Double.isFinite(density));
                        hasAir |= density < 0.0D;
                        hasRock |= density > 0.0D;
                    }
                }
            }
            assertTrue(hasAir, "Nether must retain cavities at " + origin);
            assertTrue(hasRock, "Nether must retain rock at " + origin);
        }
    }

    @Test
    void smallCoordinatesKeepLegacyParity() {
        BlendedNoise noise = netherNoise();
        for (long coordinate : new long[] {0L, 12345L, -12345L, 30000000L}) {
            DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(coordinate, 64, -coordinate);
            FarlandsMode.setMode(FarlandsMode.Mode.OFF);
            double expected = noise.compute(context);
            FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
            assertEquals(expected, noise.compute(context), 1.0E-6D);
        }
    }

    @Test
    void nonPreciseModesKeepExistingSampling() {
        BlendedNoise noise = netherNoise();
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            if (mode == FarlandsMode.Mode.BIT_64_PRECISE) {
                continue;
            }
            FarlandsMode.setMode(mode);
            double value = noise.compute(new DensityFunction.SinglePointContext(1L << 50, 64, -(1L << 50)));
            long expected = mode == FarlandsMode.Mode.BIT_32 || mode == FarlandsMode.Mode.BIT_32_HYBRID
                    ? -1819006672169036745L : 4593914356291187830L;
            assertEquals(expected, Double.doubleToRawLongBits(value), mode.toString());
        }
    }
}
