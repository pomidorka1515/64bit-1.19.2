import java.lang.management.ManagementFactory;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;

/** Isolated split-position normalization, not a frame-time benchmark. */
public final class SectorNormalizationPerfProbe {
    private static volatile long sink;

    public static void main(String[] args) {
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        Int1088 block = Int1088.ONE.shiftLeftSaturated(127);
        double[] fractions = {0.25, 0.75, -0.25, 1.25};
        for (int round = 0; round < 8; ++round) {
            long beforeBytes = allocation.getThreadAllocatedBytes(thread);
            long before = System.nanoTime();
            long checksum = 0;
            int iterations = 500_000;
            for (int i = 0; i < iterations; ++i) {
                SectorVec3 p = SectorVec3.fromBlockAndFraction(block, fractions[i & 3], 64, block, fractions[(i + 1) & 3]);
                checksum += p.blockX().lowLimb() + Double.doubleToLongBits(p.subZ());
            }
            long elapsed = System.nanoTime() - before;
            long bytes = allocation.getThreadAllocatedBytes(thread) - beforeBytes;
            sink = checksum;
            System.out.printf("round %d: %.1f ns/op, %.1f bytes/op, checksum %d%n", round,
                    (double) elapsed / iterations, (double) bytes / iterations, checksum);
        }
    }
}
