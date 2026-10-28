package net.minecraft.client.renderer.chunk;

import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;

/** Models the exact region indexing call sites, including mutable-coordinate materialization. */
public final class RenderRegionLookupPerfProbe {
    private static volatile long sink;

    public static void main(String[] args) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        int count = 300000;
        for (int bit : new int[]{0, 127, 512, 1024}) {
            Int1088 base = bit == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bit);
            Int1088 origin = base.shiftRight(4);
            BlockPos.MutableBlockPos[] positions = new BlockPos.MutableBlockPos[64];
            for (int i = 0; i < positions.length; ++i) {
                Int1088 coordinate = base.addSaturated(Int1088.fromLong(i));
                positions[i] = new BlockPos.MutableBlockPos(coordinate, 64, coordinate);
            }
            for (int round = 0; round < 6; ++round) for (boolean optimized : new boolean[]{false, true}) {
                long checksum = 0, allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
                for (int i = 0; i < count; ++i) {
                    var pos = positions[i & 63];
                    checksum += optimized ? RenderChunkRegion.localBlockIndex(pos, true, origin, 4)
                            : RenderChunkRegion.localBlockIndex(pos.getX(), origin, 4);
                }
                long elapsed = System.nanoTime() - start;
                allocated = bean.getThreadAllocatedBytes(thread) - allocated;
                sink = checksum;
                System.out.printf("bit %d round %d %s: %.1f ns/op, %.1f B/op; checksum %d%n",
                        bit, round, optimized ? "new" : "old", elapsed / (double) count, allocated / (double) count, checksum);
            }
        }
    }
}
