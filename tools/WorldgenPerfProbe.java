import net.minecraft.core.Int1088;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;

/** Run with the main runtime classpath; compare on the same JVM and machine. */
public final class WorldgenPerfProbe {
    private static volatile long sink;

    public static void main(String[] args) {
        Int1088 origin = Int1088.ONE.shiftLeftSaturated(512).addSaturated(Int1088.fromLong(12345));
        for (int round = 0; round < 5; ++round) {
            PreciseNoiseCoordinate.Split split = new PreciseNoiseCoordinate.Split();
            long sum = 0;
            long start = System.nanoTime();
            for (int i = 0; i < 200000; ++i) {
                WorldgenCoordinate.scale(origin, 0.1, -(i & 15), split);
                sum += split.lattice ^ Double.doubleToRawLongBits(split.fraction);
            }
            long noise = System.nanoTime() - start;
            start = System.nanoTime();
            Int1088 position = origin;
            for (int i = 0; i < 200000; ++i) {
                position = position.addSaturated(Int1088.ONE);
                sum += position.shiftRight(4).lowLimb();
            }
            long offsets = System.nanoTime() - start;
            sink = sum;
            System.out.printf("round %d: noise %.1f ns/op, offset+shift %.1f ns/op, checksum %d%n",
                    round, noise / 200000.0, offsets / 200000.0, sum);
        }
    }
}
