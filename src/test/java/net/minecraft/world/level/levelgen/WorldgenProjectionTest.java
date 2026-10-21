package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.Test;

class WorldgenProjectionTest {
    @Test
    void noiseHelpersProjectBeforeApplyingExistingFiniteSafeguards() {
        long[] lows = {Long.MIN_VALUE, Long.MIN_VALUE + 1L, -1L, 0L, 1L, Long.MAX_VALUE};
        long[] highs = {Long.MIN_VALUE, -7L, -1L, 0L, 7L, Long.MAX_VALUE};
        double[] scales = {0.0D, 0.125D, -2.0D, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN};
        for (long low : lows) {
            for (long high : highs) {
                Int128 coordinate = Int128.of(high, low);
                assertEquals(bits(WorldBounds.noiseCoordinate(low)), bits(WorldBounds.noiseCoordinate(coordinate)));
                for (double scale : scales) {
                    assertEquals(bits(WorldBounds.scaledNoiseCoordinate(low, scale)),
                            bits(WorldBounds.scaledNoiseCoordinate(coordinate, scale)));
                }
                assertEquals(high, coordinate.hi());
                assertEquals(low, coordinate.lo());
            }
        }
    }

    @Test
    void positionalRandomProjectsSeedsWithoutMutatingPlacementAnchors() {
        PositionalRandomFactory factory = new LegacyRandomSource(12345L).forkPositional();
        BlockPos exact = new BlockPos(Int128.of(17L, Long.MIN_VALUE), 73, Int128.of(-9L, Long.MAX_VALUE));
        BlockPos alias = new BlockPos(exact.getXLo(), exact.getY(), exact.getZLo());
        assertNotEquals(exact, alias);
        assertEquals(factory.at(alias).nextLong(), factory.at(exact).nextLong());
        assertEquals(Int128.of(17L, Long.MIN_VALUE), exact.getX());
        assertEquals(Int128.of(-9L, Long.MAX_VALUE), exact.getZ());
    }

    @Test
    void structureLocateKeepsHighHalvesDespiteSharedPlacementInputs() {
        RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 1234);
        ChunkPos exact = new ChunkPos(Int128.of(17L, 42L), Int128.of(-9L, -42L));
        ChunkPos alias = new ChunkPos(exact.xLo(), exact.zLo());
        assertEquals(placement.getPotentialStructureChunk(12345L, alias.xLo(), alias.zLo()),
                placement.getPotentialStructureChunk(12345L, exact.xLo(), exact.zLo()));
        BlockPos located = placement.getLocatePos(exact);
        assertEquals(exact.getMinBlockX(), located.getX());
        assertEquals(exact.getMinBlockZ(), located.getZ());
        assertNotEquals(placement.getLocatePos(alias), located);
    }

    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }
}
