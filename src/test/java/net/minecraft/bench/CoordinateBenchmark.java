// Baseline harness for the pre-conversion hot paths. Non-gating: it never
// asserts a timing. S10.09 compares the same named rows against this output.
package net.minecraft.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateBenchmark {
    private static final int WARMUP = 5;
    private static final int SAMPLES = 9;
    /** Far enough that a double cannot name the block, matching the current exact-position paths. */
    private static final long FAR = 1L << 53;
    private static final long[] REGION_COORDS = {
            0L, -1L, 1L, 105283943060505L, -105283943060506L, Long.MIN_VALUE >> 5, Long.MAX_VALUE >> 5, 1L << 40
    };

    private static final long[] SINK = new long[1];

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recordBaseline() throws Exception {
        StringBuilder report = new StringBuilder(2048);
        report.append("coordinate-benchmark\n");
        report.append("java ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vm.name")).append(")\n");
        report.append("os ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append('\n');
        report.append("warmup ").append(WARMUP).append(" samples ").append(SAMPLES).append('\n');
        report.append("non-gating true\n");
        report.append("metric,ops,median_ns,min_ns,max_ns,median_ns_per_op,checksum\n");

        report.append(measure("block-iteration", this::iterateBlocks));
        report.append(measure("collision-iteration", this::iterateCollisions));
        report.append(measure("chunk-key-lookup", this::lookupChunkKeys));
        report.append(measure("region-name-formatting", this::formatRegionNames));
        report.append(measure("sector-vec3-relative-math", this::relativeMath));
        report.append("block-iteration: BlockPos.betweenClosed 16x16x16 at ")
                .append(FAR).append(" plus Cursor3D.advance over the same span, 32 reps\n");
        report.append("collision-iteration: SectorBlockCollisions player box 0.6x1.8 at ")
                .append(FAR).append(", ns/sweep over 2000 sweeps\n");
        report.append("chunk-key-lookup: Object2ObjectOpenHashMap<ChunkPos,Integer>.get(new ChunkPos) ")
                .append("over a 33x33 window, 20 reps\n");
        report.append("region-name-formatting: \"r.\" + regionX + \".\" + regionZ + \".mca\", 4000 reps of 8 names\n");
        report.append("sector-vec3-relative-math: relativeX/Y/Z at ").append(FAR).append(", 20000 calls\n");

        String text = report.toString();
        System.out.print(text);
        Path benchmarks = Path.of(System.getProperty("user.dir")).resolve("../128bit-migration/benchmarks");
        Files.createDirectories(benchmarks);
        Files.writeString(benchmarks.resolve("s0.03-coordinate-benchmark.txt"), text);
        SINK[0] = text.length();
    }

    private static String measure(String name, Work work) {
        for (int i = 0; i < WARMUP; ++i) {
            long checksum = work.run();
            assertTrue(checksum != 0L, name);
            SINK[0] ^= checksum;
        }
        long[] nanos = new long[SAMPLES];
        long checksum = 0L;
        long ops = 0L;
        for (int i = 0; i < SAMPLES; ++i) {
            long start = System.nanoTime();
            Result result = work.timed();
            nanos[i] = System.nanoTime() - start;
            checksum = result.checksum;
            ops = result.ops;
            SINK[0] ^= checksum;
        }
        assertTrue(ops > 0L, name);
        assertTrue(checksum != 0L, name);
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        return String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%.3f,%d%n",
                name, ops, median, sorted[0], sorted[sorted.length - 1], (double) median / (double) ops, checksum);
    }

    private Result iterateBlocks() {
        long checksum = 1L;
        long ops = 0L;
        long x0 = FAR;
        long z0 = -FAR;
        for (int rep = 0; rep < 32; ++rep) {
            for (BlockPos pos : BlockPos.betweenClosed(x0, 0, z0, x0 + 15L, 15, z0 + 15L)) {
                checksum += pos.getX() ^ (pos.getZ() << 1) ^ pos.getY();
                ++ops;
            }
            Cursor3D cursor = new Cursor3D(x0, 0, z0, x0 + 15L, 15, z0 + 15L);
            while (cursor.advance()) {
                checksum += cursor.nextX() ^ (cursor.nextZ() << 1) ^ cursor.nextY();
                ++ops;
            }
        }
        return new Result(ops, checksum);
    }

    private Result iterateCollisions() {
        BlockPos solid = new BlockPos(FAR, 64, FAR);
        CollisionGetter level = new SingleBlockCollisionGetter(solid);
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(FAR, 64, FAR);
        SectorVec3 player = SectorVec3.fromBlockAndFraction(FAR, 0.25D, 64.0D, FAR, 0.25D);
        SectorAABB exactBox = SectorAABB.around(player, 0.6D, 1.8D);
        AABB localBox = exactBox.toLocalAABB(origin);
        long checksum = 1L;
        long shapes = 0L;
        for (int sweep = 0; sweep < 2000; ++sweep) {
            for (VoxelShape shape : level.getSectorBlockCollisions(null, exactBox, localBox, origin)) {
                checksum += Double.doubleToRawLongBits(shape.min(net.minecraft.core.Direction.Axis.X));
                ++shapes;
            }
        }
        if (shapes == 0L) {
            throw new IllegalStateException("collision iteration visited no shapes");
        }
        checksum += shapes;
        return new Result(2000L, checksum);
    }

    private Result lookupChunkKeys() {
        long originX = FAR >> 4;
        long originZ = -FAR >> 4;
        Object2ObjectOpenHashMap<ChunkPos, Integer> keys = new Object2ObjectOpenHashMap<>(1089);
        for (int dx = -16; dx <= 16; ++dx) {
            for (int dz = -16; dz <= 16; ++dz) {
                keys.put(new ChunkPos(originX + dx, originZ + dz), dx * 33 + dz);
            }
        }
        long checksum = 1L;
        long ops = 0L;
        for (int rep = 0; rep < 20; ++rep) {
            for (int dx = -16; dx <= 16; ++dx) {
                for (int dz = -16; dz <= 16; ++dz) {
                    Integer found = keys.get(new ChunkPos(originX + dx, originZ + dz));
                    if (found == null) {
                        throw new IllegalStateException("missing chunk key");
                    }
                    checksum += found.intValue() + 1L;
                    ++ops;
                }
            }
        }
        return new Result(ops, checksum);
    }

    private Result formatRegionNames() {
        long checksum = 1L;
        long ops = 0L;
        for (int rep = 0; rep < 4000; ++rep) {
            for (long region : REGION_COORDS) {
                String name = "r." + region + "." + (region ^ 0x5A5A5A5A5A5A5A5AL) + ".mca";
                checksum += name.length();
                checksum += name.charAt(2);
                ++ops;
            }
        }
        return new Result(ops, checksum);
    }

    private Result relativeMath() {
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(FAR, 0.25D, 64.0D, -FAR, 0.75D);
        SectorVec3 nearby = SectorVec3.fromBlockAndFraction(FAR + 3L, 0.5D, 66.5D, -FAR - 2L, 0.25D);
        long checksum = 1L;
        long ops = 0L;
        for (int i = 0; i < 20000; ++i) {
            double dx = nearby.relativeX(origin);
            double dy = nearby.relativeY(origin);
            double dz = nearby.relativeZ(origin);
            checksum += Double.doubleToRawLongBits(dx + dy + dz);
            ++ops;
        }
        return new Result(ops, checksum);
    }

    private interface Work {
        Result timed();

        default long run() {
            return this.timed().checksum;
        }
    }

    private record Result(long ops, long checksum) {
    }

    private static final class SingleBlockCollisionGetter implements CollisionGetter {
        private final BlockPos solid;
        private final BlockState stone = Blocks.STONE.defaultBlockState();

        private SingleBlockCollisionGetter(BlockPos solid) {
            this.solid = solid;
        }

        @Override
        public WorldBorder getWorldBorder() {
            return new WorldBorder();
        }

        @Override
        @Nullable
        public net.minecraft.world.level.BlockGetter getChunkForCollisions(long chunkX, long chunkZ) {
            return this;
        }

        @Override
        public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
            return Collections.emptyList();
        }

        @Override
        @Nullable
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.solid.equals(pos) ? this.stone : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
