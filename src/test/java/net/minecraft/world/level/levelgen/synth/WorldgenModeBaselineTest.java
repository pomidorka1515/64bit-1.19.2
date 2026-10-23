package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pre-change golden outputs for the legacy worldgen noise entry points.
 *
 * <p>Values are raw IEEE-754 bits captured at commit e07ba4ce before the
 * full-width worldgen change, using seed 1234 and X coordinates
 * 0, +/-12550824, +/-9007199254740993, Long.MIN_VALUE and Long.MAX_VALUE
 * with Y=73 and Z=37. They must not be recomputed from production code.</p>
 */
class WorldgenModeBaselineTest {
    private static final long SEED = 1234L;
    private static final int Y = 73;
    private static final long Z = 37L;
    private static final double XZ_SCALE = 0.25D;
    private static final double Y_SCALE = 0.25D;
    private static final long[] HORIZONTAL_COORDINATES = {
        0L,
        12_550_824L,
        -12_550_824L,
        9_007_199_254_740_993L,
        -9_007_199_254_740_993L,
        Long.MIN_VALUE,
        Long.MAX_VALUE
    };
    private static final FarlandsMode.Mode[] MODES = {
        FarlandsMode.Mode.BIT_32,
        FarlandsMode.Mode.BIT_32_HYBRID,
        FarlandsMode.Mode.BIT_64,
        FarlandsMode.Mode.OFF,
        FarlandsMode.Mode.BIT_64_PRECISE
    };
    private static final long[][] BLENDED_BITS = {
        { 4586714566461134700L, 4716679300906784552L, 4594449728734478600L, 6134485197320775016L, 6134485197632599456L, 6404701175119069257L, 6404701175118764737L },
        { 4586714566461134700L, 4716679300906784552L, 4594449728734478600L, 6134485197320775016L, 6134485197632599456L, 6404701175119069257L, 6404701175118764737L },
        { 4586714566461134700L, 4594023652633469914L, 4594449728734478600L, -4633076371388490192L, -4622974313413040626L, 6405394265887082664L, 6404049157043905407L },
        { 4586714566461134700L, 4594023652633469914L, 4594449728734478600L, -4633076371388490192L, -4622974313413040626L, 4594499806390839609L, 4583493824319667903L },
        { 4586714566461134700L, 4594023652633469914L, 4594449728734478600L, -4633332307990479281L, -4622911908042814256L, -4629420029233298946L, -4628269345880856308L }
    };
    private static final long[][] NORMAL_DOUBLE_Y_BITS = {
        { -4624090276787532614L, -4633241608209855578L, -4627987123259149629L, -4620877759991156018L, 4594811239500448796L, -4624090276787532614L, -4624459688977721373L },
        { -4624090276787532614L, -4633241608209855578L, -4627987123259149629L, -4620877759991156018L, 4594811239500448796L, -4624090276787532614L, -4624459688977721373L },
        { -4624090276787532614L, -4633241608209855578L, -4627987123259149629L, -4620877759991156018L, 4594811239500448796L, -4624090276787532614L, -4624459688977721373L },
        { -4624090276787532614L, -4633241608209855578L, -4627987123259149629L, -4620877759991156018L, 4594811239500448796L, -4624090276787532614L, -4624459688977721373L },
        { -4624090276787532614L, -4633241608209855578L, -4627987123259149629L, -4620877759991156018L, 4594811239500448796L, -4624090276787532614L, -4624459688977721373L }
    };
    private static final long[][] NORMAL_LONG_Y_BITS = {
        { -4630408911466226428L, 4588003161692092304L, 4597752315476651310L, -4641435025463941732L, 4584920049179567172L, -4630408911466226428L, 4591669753276616528L },
        { -4630408911466226428L, 4588003161692092304L, 4597752315476651310L, -4641435025463941732L, 4584920049179567172L, -4630408911466226428L, 4591669753276616528L },
        { -4630408911466226428L, 4588003161692092304L, 4597752315476651310L, -4641435025463941732L, 4584920049179567172L, -4630408911466226428L, 4591669753276616528L },
        { -4630408911466226428L, 4588003161692092304L, 4597752315476651310L, -4641435025463941732L, 4584920049179567172L, -4630408911466226428L, 4591669753276616528L },
        { -4630408911466226428L, 4588003161692092304L, 4597752315476651310L, -4641435025463941732L, 4584920049179567172L, -4630408911466226428L, 4591669753276616528L }
    };

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaultMode() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
    }

    @Test
    void blendedNoiseMatchesPreChangeRawBitsForEveryMode() {
        BlendedNoise noise = new BlendedNoise(new LegacyRandomSource(SEED), XZ_SCALE, 0.125D, 80.0D, 160.0D, 8.0D);
        for (int modeIndex = 0; modeIndex < MODES.length; ++modeIndex) {
            FarlandsMode.setMode(MODES[modeIndex]);
            for (int coordinateIndex = 0; coordinateIndex < HORIZONTAL_COORDINATES.length; ++coordinateIndex) {
                long coordinate = HORIZONTAL_COORDINATES[coordinateIndex];
                double value = noise.compute(new DensityFunction.SinglePointContext(coordinate, Y, Z));
                assertEquals(BLENDED_BITS[modeIndex][coordinateIndex], Double.doubleToRawLongBits(value), MODES[modeIndex] + " x=" + coordinate);
            }
        }
    }

    @Test
    void normalNoiseDoubleYOverloadMatchesPreChangeRawBitsForEveryMode() {
        NormalNoise noise = NormalNoise.create(new LegacyRandomSource(SEED), 0, 1.0D, 0.5D, 0.25D);
        for (int modeIndex = 0; modeIndex < MODES.length; ++modeIndex) {
            FarlandsMode.setMode(MODES[modeIndex]);
            for (int coordinateIndex = 0; coordinateIndex < HORIZONTAL_COORDINATES.length; ++coordinateIndex) {
                long coordinate = HORIZONTAL_COORDINATES[coordinateIndex];
                double value = noise.getValueScaled(coordinate, XZ_SCALE, (double) Y, Z);
                assertEquals(NORMAL_DOUBLE_Y_BITS[modeIndex][coordinateIndex], Double.doubleToRawLongBits(value), MODES[modeIndex] + " x=" + coordinate);
            }
        }
    }

    @Test
    void normalNoiseLongYOverloadMatchesPreChangeRawBitsForEveryMode() {
        NormalNoise noise = NormalNoise.create(new LegacyRandomSource(SEED), 0, 1.0D, 0.5D, 0.25D);
        for (int modeIndex = 0; modeIndex < MODES.length; ++modeIndex) {
            FarlandsMode.setMode(MODES[modeIndex]);
            for (int coordinateIndex = 0; coordinateIndex < HORIZONTAL_COORDINATES.length; ++coordinateIndex) {
                long coordinate = HORIZONTAL_COORDINATES[coordinateIndex];
                double value = noise.getValueScaled(coordinate, XZ_SCALE, (long) Y, Y_SCALE, Z);
                assertEquals(NORMAL_LONG_Y_BITS[modeIndex][coordinateIndex], Double.doubleToRawLongBits(value), MODES[modeIndex] + " x=" + coordinate);
            }
        }
    }
}
