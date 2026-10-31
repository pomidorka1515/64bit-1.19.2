package net.minecraft.world.level.lighting;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

/** Synthetic CPU/allocation probe, not a frame-time or chunk-throughput test. */
public final class LightingPerformanceProbe {
    private static volatile long sink;
    private static final ThreadMXBean ALLOCATIONS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) {
        for (int bits : new int[] {0, 128, 1024}) {
            Int1088 origin = bits == 0 ? Int1088.fromLong(-32) : Int1088.ONE.shiftLeftSaturated(bits).negate();
            BlockPos[] positions = new BlockPos[16 * 16 * 16];
            for (int i = 0; i < positions.length; ++i) {
                positions[i] = new BlockPos(origin.addSaturated(i & 15), (i >>> 8) - 8, origin.addSaturated((i >>> 4) & 15));
            }
            Graph graph = new Graph();
            measure("queue bits=" + bits, () -> {
                for (BlockPos pos : positions) graph.checkEdge(null, pos, 3, true);
                // Equal but separately allocated keys exercise duplicate detection.
                for (BlockPos pos : positions) graph.checkEdge(null, new BlockPos(pos.getX(), pos.getY(), pos.getZ()), 3, true);
                graph.runUpdates(Integer.MAX_VALUE);
                return graph.updates;
            });
            var layers = new BlockLightSectionStorage.BlockDataLayerStorageMap(new HashMap<>());
            for (BlockPos pos : positions) layers.setLayer(SectionPos.of(pos), new DataLayer());
            measure("section bits=" + bits, () -> {
                long sum = 0;
                for (int repeat = 0; repeat < 32; ++repeat) {
                    for (BlockPos pos : positions) {
                        SectionPos section = layers.sectionForBlock(pos);
                        if (layers.getLayer(section) != null) ++sum;
                    }
                }
                return sum;
            });
            var uncached = new BlockLightSectionStorage.BlockDataLayerStorageMap(new HashMap<>());
            SectionPos[] sections = new SectionPos[positions.length];
            DataLayer data = new DataLayer();
            for (int i = 0; i < sections.length; ++i) {
                BlockPos pos = positions[i];
                uncached.setLayer(SectionPos.of(pos.getX(), pos.getY(), pos.getZ()), data);
                sections[i] = SectionPos.of(pos.getX(), pos.getY(), pos.getZ());
            }
            uncached.disableCache();
            measure("map bits=" + bits, () -> {
                long sum = 0;
                for (int repeat = 0; repeat < 16; ++repeat) {
                    for (SectionPos section : sections) if (uncached.getLayer(section) != null) ++sum;
                }
                return sum;
            });
        }
        System.out.println("checksum=" + sink);
    }

    private static void measure(String name, java.util.function.LongSupplier workload) {
        for (int i = 0; i < 200; ++i) sink = workload.getAsLong();
        long[] times = new long[31];
        long bytes = 0;
        long thread = Thread.currentThread().getId();
        for (int i = 0; i < times.length; ++i) {
            long beforeBytes = ALLOCATIONS.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            sink = workload.getAsLong();
            times[i] = System.nanoTime() - start;
            bytes += ALLOCATIONS.getThreadAllocatedBytes(thread) - beforeBytes;
        }
        Arrays.sort(times);
        System.out.printf("%s median=%.3f ms allocated=%.0f bytes/iteration%n",
                name, times[times.length / 2] / 1e6, (double) bytes / times.length);
    }

    private static final class Graph extends DynamicGraphMinFixedPoint<BlockPos> {
        long updates;
        Graph() { super(16, 256, 8192); }
        protected boolean isSource(BlockPos pos) { return pos == null; }
        protected int getComputedLevel(BlockPos pos, BlockPos excluded, int level) { return level; }
        protected void checkNeighborsAfterUpdate(BlockPos pos, int level, boolean decreasing) {}
        protected int getLevel(BlockPos pos) { return 15; }
        protected void setLevel(BlockPos pos, int level) { ++updates; }
        protected int computeLevelFromNeighbor(BlockPos from, BlockPos to, int level) { return level + 1; }
    }
}
