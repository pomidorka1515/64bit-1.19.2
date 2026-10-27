package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.Test;

class WorldgenProjectionTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test
    void wideNoiseHelpersPreserveFullMagnitudeWithoutLegacyClamps() {
        long[] lows = {Long.MIN_VALUE, Long.MIN_VALUE + 1L, -1L, 0L, 1L, Long.MAX_VALUE};
        long[] highs = {Long.MIN_VALUE, -7L, -1L, 0L, 7L, Long.MAX_VALUE};
        double[] scales = {0.0D, 0.125D, -2.0D, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN};
        for (long low : lows) {
            for (long high : highs) {
                Int1088 coordinate = Int1088.of(low, high);
                double magnitude = new java.math.BigInteger(coordinate.toString()).doubleValue();
                assertEquals(bits(magnitude), bits(WorldBounds.noiseCoordinate(coordinate)));
                for (double scale : scales) {
                    double product = magnitude * scale;
                    double expected = product;
                    assertEquals(bits(expected),
                            bits(WorldBounds.scaledNoiseCoordinate(coordinate, scale)));
                }
                assertEquals(high, coordinate.limb(1));
                assertEquals(low, coordinate.lowLimb());
            }
        }
    }

    @Test
    void positionalRandomIncludesHighLimbsWithoutMutatingPlacementAnchors() {
        PositionalRandomFactory factory = new LegacyRandomSource(12345L).forkPositional();
        BlockPos exact = new BlockPos(Int1088.of(Long.MIN_VALUE, 17L), 73, Int1088.of(Long.MAX_VALUE, -9L));
        BlockPos alias = new BlockPos(exact.xLow(), exact.getY(), exact.zLow());
        assertNotEquals(exact, alias);
        assertNotEquals(factory.at(alias).nextLong(), factory.at(exact).nextLong());
        assertEquals(Int1088.of(Long.MIN_VALUE, 17L), exact.getX());
        assertEquals(Int1088.of(Long.MAX_VALUE, -9L), exact.getZ());
    }

    @Test
    void structureLocateKeepsHighHalvesDespiteSharedPlacementInputs() {
        RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 1234);
        ChunkPos exact = new ChunkPos(Int1088.of(42L, 17L), Int1088.of(-42L, -9L));
        ChunkPos alias = new ChunkPos(exact.xLow(), exact.zLow());
        assertEquals(placement.getPotentialStructureChunk(12345L, alias.xLow(), alias.zLow()),
                placement.getPotentialStructureChunk(12345L, exact.xLow(), exact.zLow()));
        BlockPos located = placement.getLocatePos(exact);
        assertEquals(exact.getMinBlockX(), located.getX());
        assertEquals(exact.getMinBlockZ(), located.getZ());
        assertNotEquals(placement.getLocatePos(alias), located);
    }

    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }
}
