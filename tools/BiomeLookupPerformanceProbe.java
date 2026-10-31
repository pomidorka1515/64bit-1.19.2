package net.minecraft.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.biome.BiomeManager;

/** The same smoothed-biome query workload can run against original/current classes. */
public final class BiomeLookupPerformanceProbe {
    public static void main(String[] args) {
        for (int bits : new int[] {0, 160, 1024}) {
            long[] selected = new long[1];
            BiomeManager manager = new BiomeManager((x, y, z) -> {
                selected[0] = (x.lowLimb() * 31 + y) * 31 + z.lowLimb();
                return null;
            }, 1234);
            Int1088 base = bits == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bits);
            for (boolean mutable : new boolean[] {false, true}) {
                BlockPos[] positions = new BlockPos[1024];
                for (int i = 0; i < positions.length; ++i) {
                    BlockPos pos = new BlockPos(base.addSaturated(i & 31), 64 + (i & 15),
                            base.negate().addSaturated(i >> 5));
                    positions[i] = mutable ? pos.mutable() : pos;
                }
                ClientHotspotPerformanceProbe.measure("biome bits=" + bits + " mutable=" + mutable, () -> {
                    long checksum = 0;
                    for (int i = 0; i < ClientHotspotPerformanceProbe.OPERATIONS; ++i) {
                        manager.getBiome(positions[i & 1023]);
                        checksum += selected[0];
                    }
                    return checksum;
                });
            }
        }
    }
}
