import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;

/** Isolated model of the render-section dirty check, not a frame-time benchmark. */
public final class RenderChunkScanPerfProbe {
    private static volatile long sink;
    private static final int SECTIONS = 4096;
    private static final int REPEATS = 200;

    private static long scan(BlockPos[] positions, boolean[] dirty, boolean optimized) {
        long checksum = 0;
        for (int repeat = 0; repeat < REPEATS; ++repeat) {
            for (int i = 0; i < positions.length; ++i) {
                if (optimized && !dirty[i]) continue;
                ChunkPos pos = new ChunkPos(positions[i]);
                if (dirty[i]) checksum += pos.hashCode();
            }
        }
        return checksum;
    }

    public static void main(String[] args) {
        for (int bit : new int[]{0, 512}) {
            Int1088 origin = bit == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bit);
            BlockPos[] positions = new BlockPos[SECTIONS];
            for (int i = 0; i < SECTIONS; ++i) {
                positions[i] = new BlockPos.MutableBlockPos().set(
                        origin.addSaturated(Int1088.fromLong((i & 63) * 16L)), 64,
                        origin.addSaturated(Int1088.fromLong((i >>> 6) * 16L)));
            }
            for (int stride : new int[]{0, 16, 1}) {
                boolean[] dirty = new boolean[SECTIONS];
                for (int i = 0; i < SECTIONS; ++i) dirty[i] = stride != 0 && i % stride == 0;
                for (int round = 0; round < 6; ++round) {
                    long start = System.nanoTime();
                    long before = scan(positions, dirty, false);
                    long middle = System.nanoTime();
                    long after = scan(positions, dirty, true);
                    long end = System.nanoTime();
                    if (before != after) throw new AssertionError("checksum mismatch");
                    sink = after;
                    System.out.printf("bit %d dirty stride %d round %d: before %.1f after %.1f ns/section, checksum %d%n",
                            bit, stride, round, (middle - start) / (double)(SECTIONS * REPEATS),
                            (end - middle) / (double)(SECTIONS * REPEATS), after);
                }
            }
        }
    }
}
