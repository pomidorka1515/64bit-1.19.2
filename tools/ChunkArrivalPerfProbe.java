package net.minecraft.client.multiplayer;

import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.core.SectionPos;

/** CPU/allocation probe, not an FPS benchmark. Run on Java 17 with the main runtime classpath. */
public final class ChunkArrivalPerfProbe {
    private static volatile long sink;
    private static final int COUNT = 200000;

    public static void main(String[] args) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        for (int bit : new int[]{0, 127, 512, 1024}) {
            Int1088 base = bit == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bit);
            BlockPos.MutableBlockPos[] positions = new BlockPos.MutableBlockPos[64];
            SectionPos[] sections = new SectionPos[64];
            Int1088[] coordinates = new Int1088[64];
            for (int i = 0; i < 64; ++i) {
                coordinates[i] = base.addSaturated(Int1088.fromLong(i - 32));
            }
            for (int i = 0; i < 64; ++i) {
                positions[i] = new BlockPos.MutableBlockPos(coordinates[i], 64, coordinates[63 - i]);
                sections[i] = SectionPos.of(positions[i]);
            }
            int residue = ClientChunkCache.storageModulo(base, 65);
            for (int round = 0; round < 6; ++round) {
                for (String task : new String[]{"contains-old", "contains-new", "modulo-old", "modulo-new", "copy", "sum"}) {
                    if (args.length > 0 && args[0].equals("--baseline") && task.equals("modulo-new")) continue;
                    long checksum = 0;
                    long[] a = new long[Int1088.LIMBS], b = new long[Int1088.LIMBS], out = new long[Int1088.LIMBS];
                    base.copyFixed(a, 0);
                    base.copyFixed(b, 0);
                    int length = base.limbCount();
                    long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
                    for (int i = 0; i < COUNT; ++i) {
                        int index = i & 63;
                        BlockPos p = positions[index];
                        SectionPos s = sections[index];
                        switch (task) {
                            case "contains-old" -> checksum += s.getX().equals(p.getX().shiftRight(4)) && s.getZ().equals(p.getZ().shiftRight(4)) ? 1 : 0;
                            case "contains-new" -> checksum += s.contains(p) ? 1 : 0;
                            case "modulo-old" -> checksum += ClientChunkCache.storageModulo(coordinates[index], 65);
                            case "modulo-new" -> checksum += ClientChunkCache.anchoredStorageModulo(coordinates[index], base, residue, 65);
                            case "copy" -> { coordinates[index].copyFixed(out, 0); checksum += out[0]; }
                            case "sum" -> { a[0] = i; checksum += Int1088Math.addSaturated(a, 0, length, b, 0, length, out, 0) + out[0]; }
                        }
                    }
                    long elapsed = System.nanoTime() - start;
                    allocated = bean.getThreadAllocatedBytes(thread) - allocated;
                    sink = checksum;
                    System.out.printf("bit %d round %d %s: %.1f ns/op, %.1f B/op; checksum %d%n",
                            bit, round, task, elapsed / (double) COUNT, allocated / (double) COUNT, checksum);
                }
            }
        }
    }
}
