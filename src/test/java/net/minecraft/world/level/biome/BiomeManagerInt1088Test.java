package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import org.junit.jupiter.api.Test;

class BiomeManagerInt1088Test {
    @Test
    void hoistedCornersPreserveOriginalBiomeSelection() {
        for (long seed : new long[] {0, 1234, Long.MIN_VALUE, Long.MAX_VALUE}) {
            BlockPos[] sampled = new BlockPos[1];
            BiomeManager manager = new BiomeManager((x, y, z) -> {
                sampled[0] = new BlockPos(x, y, z);
                return null;
            }, seed);
            for (Int1088 anchor : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 7), Int1088.of(0, -7),
                    Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
                for (int offset = -8; offset <= 8; ++offset) {
                    BlockPos pos = new BlockPos(anchor.addSaturated(offset), offset - 64, anchor.addSaturated(-offset));
                    BlockPos expected = originalSelection(pos, seed);
                    manager.getBiome(pos);
                    assertEquals(expected, sampled[0]);
                    manager.getBiome(pos.mutable());
                    assertEquals(expected, sampled[0]);
                }
            }
        }
    }

    private static BlockPos originalSelection(BlockPos pos, long seed) {
        Int1088 x = WorldBounds.subtractBlockOffset(pos.getX(), 2);
        Int1088 z = WorldBounds.subtractBlockOffset(pos.getZ(), 2);
        int y = pos.getY() - 2;
        double best = Double.POSITIVE_INFINITY;
        BlockPos result = null;
        for (int corner = 0; corner < 8; ++corner) {
            int dx = (corner >> 2) & 1, dy = (corner >> 1) & 1, dz = corner & 1;
            Int1088 qx = WorldBounds.addBlockOffset(x.shiftRight(2), dx);
            Int1088 qz = WorldBounds.addBlockOffset(z.shiftRight(2), dz);
            int qy = (y >> 2) + dy;
            long sx = WorldgenCoordinate.seedCoordinate(qx), sz = WorldgenCoordinate.seedCoordinate(qz);
            long state = seed;
            for (int repeat = 0; repeat < 2; ++repeat) {
                state = LinearCongruentialGenerator.next(state, sx);
                state = LinearCongruentialGenerator.next(state, qy);
                state = LinearCongruentialGenerator.next(state, sz);
            }
            double fx = fiddle(state);
            state = LinearCongruentialGenerator.next(state, seed);
            double fy = fiddle(state);
            state = LinearCongruentialGenerator.next(state, seed);
            double fz = fiddle(state);
            double nx = (x.lowLimb() & 3) / 4.0D - dx + fx;
            double ny = (y & 3) / 4.0D - dy + fy;
            double nz = (z.lowLimb() & 3) / 4.0D - dz + fz;
            double distance = nz * nz + ny * ny + nx * nx;
            if (best > distance) {
                best = distance;
                result = new BlockPos(qx, qy, qz);
            }
        }
        return result;
    }

    private static double fiddle(long state) {
        return (Math.floorMod(state >> 24, 1024) / 1024.0D - 0.5D) * 0.9D;
    }

    @Test
    void runtimeQueriesDispatchFullWidthQuartCoordinates() {
        Int1088 x = Int1088.of(64L, 123L);
        Int1088 z = Int1088.of(128L, -98L);
        Int1088[] queried = new Int1088[2];
        BiomeManager manager = new BiomeManager(new BiomeManager.NoiseBiomeSource() {
            public Holder<Biome> getNoiseBiome(long x, int y, long z) {
                throw new AssertionError("runtime lookup narrowed before provider dispatch");
            }

            public Holder<Biome> getNoiseBiome(Int1088 x, int y, Int1088 z) {
                queried[0] = x;
                queried[1] = z;
                return null;
            }
        }, 1234L);

        manager.getNoiseBiomeAtPosition(new BlockPos(x, 70, z));
        assertEquals(x.shiftRight(2), queried[0]);
        assertEquals(z.shiftRight(2), queried[1]);
        manager.getBiome(new BlockPos(x, 70, z));
        assertTrue(queried[0].equals(x.shiftRight(2))
                || queried[0].equals(x.shiftRight(2).subtract(Int1088.ONE)));
        assertTrue(queried[1].equals(z.shiftRight(2))
                || queried[1].equals(z.shiftRight(2).subtract(Int1088.ONE)));
    }

    @Test
    void defaultSamplingProviderReceivesFullQuartCoordinates() {
        BiomeManager.NoiseBiomeSource source = (x, y, z) -> {
            assertEquals(Int1088.of(17L, 42L), x);
            assertEquals(Int1088.of(-19L, -42L), z);
            throw new Sampled();
        };
        assertThrows(Sampled.class, () -> source.getNoiseBiome(
                Int1088.of(17L, 42L), 5, Int1088.of(-19L, -42L)));
    }

    private static class Sampled extends RuntimeException {
    }
}
