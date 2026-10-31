package net.minecraft.client.renderer;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;

/** Headless, non-gating microbenchmarks; not an FPS or chunk-throughput test. */
public final class ClientHotspotPerformanceProbe {
    public static final int OPERATIONS = 100000;
    private static volatile long sink;
    private static final com.sun.management.ThreadMXBean BEAN =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) {
        for (int bits : new int[] {0, 160, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 first = bits == 0 ? Int1088.fromLong(-16)
                        : Int1088.ONE.shiftLeftSaturated(bits).multiplySaturated(sign);
                ViewArea.AxisIndex axis = new ViewArea.AxisIndex(first, 33);
                BlockPos[] positions = new BlockPos[1024];
                for (int i = 0; i < positions.length; ++i) {
                    Int1088 block = axis.blocks[i % 33].addSaturated(i & 15);
                    positions[i] = new BlockPos(block, 64, block);
                }
                String label = "render-index bits=" + bits + " sign=" + sign;
                long old = measure(label + " remainder", () -> lookup(positions, axis, false));
                long next = measure(label + " window", () -> lookup(positions, axis, true));
                if (old != next) throw new AssertionError("Index checksum mismatch");
            }
        }
    }

    private static long lookup(BlockPos[] positions, ViewArea.AxisIndex axis, boolean optimized) {
        long checksum = 0;
        for (int i = 0; i < OPERATIONS; ++i) {
            BlockPos pos = positions[i & 1023];
            checksum += optimized ? axis.slot(axis.blockOffset(pos, true))
                    : ViewArea.floorMod(pos.getX().shiftRight(4), 33);
        }
        return checksum;
    }

    public static long measure(String label, LongSupplier operation) {
        if (!BEAN.isThreadAllocatedMemoryEnabled()) BEAN.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 10; ++i) sink = operation.getAsLong();
        long thread = Thread.currentThread().getId();
        long[] wall = new long[7], cpu = new long[7], bytes = new long[7];
        for (int i = 0; i < wall.length; ++i) {
            long allocated = BEAN.getThreadAllocatedBytes(thread);
            long startCpu = BEAN.getCurrentThreadCpuTime();
            long start = System.nanoTime();
            sink = operation.getAsLong();
            wall[i] = System.nanoTime() - start;
            cpu[i] = BEAN.getCurrentThreadCpuTime() - startCpu;
            bytes[i] = BEAN.getThreadAllocatedBytes(thread) - allocated;
        }
        Arrays.sort(wall);
        Arrays.sort(cpu);
        Arrays.sort(bytes);
        System.out.printf("%s: median %.2f ns/op, %.2f cpu-ns/op, %.2f B/op; checksum=%d%n",
                label, wall[3] / (double) OPERATIONS, cpu[3] / (double) OPERATIONS,
                bytes[3] / (double) OPERATIONS, sink);
        return sink;
    }
}
