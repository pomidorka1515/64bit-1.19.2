import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;

/** Run unchanged against both classpaths on the same JVM; not an FPS benchmark. */
public final class CoordinatePerfProbe {
    private static volatile long sink;
    private static final int COUNT = 200000;

    public static void main(String[] args) {
        for (int bit : new int[]{80, 512, 1080}) {
            Int1088 origin = Int1088.ONE.shiftLeftSaturated(bit).addSaturated(Int1088.fromLong(12345));
            BlockPos source = new BlockPos(origin, 64, origin.negate());
            BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
            PreciseNoiseCoordinate.Split split = new PreciseNoiseCoordinate.Split();
            Int1088[] columns = new Int1088[16];
            for (int i = 0; i < columns.length; ++i) columns[i] = origin.addSaturated(Int1088.fromLong(i));
            for (int round = 0; round < 6; ++round) {
                long sum = 0;
                long start = System.nanoTime();
                for (int i = 0; i < COUNT; ++i) {
                    WorldgenCoordinate.scale(origin, 0.1, -(i & 15), split);
                    sum += split.lattice ^ Double.doubleToRawLongBits(split.fraction);
                }
                long scale = System.nanoTime() - start;
                start = System.nanoTime();
                for (int i = 0; i < COUNT; ++i) {
                    Int1088 column = columns[i & 15];
                    sum += Int1088Math.signedDifferenceAsLong(column, origin);
                    sum += column.compareTo(origin);
                }
                long difference = System.nanoTime() - start;
                start = System.nanoTime();
                for (int i = 0; i < COUNT; ++i) {
                    neighbor.setWithOffset(source, (i & 15) - 7L, 1, 1L);
                    sum += neighbor.xLow() ^ neighbor.zLow();
                }
                long offsets = System.nanoTime() - start;
                start = System.nanoTime();
                for (int i = 0; i < COUNT; ++i) sum += columns[i & 15].shiftLeftSaturated(4).lowLimb();
                long shift = System.nanoTime() - start;
                sink = sum;
                System.out.printf("bit %d round %d: scale %.1f, difference+compare %.1f, neighbor %.1f, left-shift %.1f ns/op; checksum %d%n",
                        bit, round, scale / (double)COUNT, difference / (double)COUNT,
                        offsets / (double)COUNT, shift / (double)COUNT, sum);
            }
        }
    }
}
