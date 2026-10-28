import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

/** Isolates proto-chunk block lookups, not lighting throughput or frame time. */
public final class ChunkLookupPerfProbe {
    private static volatile int sink;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (int bit : new int[]{0, 127, 512}) {
            Int1088 x = bit == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bit);
            Int1088 z = x.negate();
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(x.shiftRight(4), z.shiftRight(4)),
                    UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), BuiltinRegistries.BIOME, null);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().set(x, 20, z);
            chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
            for (int round = 0; round < 8; ++round) {
                int sum = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 1000000; ++i) {
                    if (chunk.getBlockState(pos) == Blocks.STONE.defaultBlockState()) ++sum;
                }
                long elapsed = System.nanoTime() - start;
                sink = sum;
                System.out.printf("bit %d round %d: %.1f ns/lookup checksum %d%n", bit, round, elapsed / 1000000.0, sum);
            }
        }
    }
}
