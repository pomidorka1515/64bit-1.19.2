package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
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

class SectorBlockCollisionsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void spawnCollisionChecksUseExactBoundsRatherThanCollapsedDoubleBoxes() {
        for (Int128 coordinate : new Int128[] {Int128.of(5L, -1L), Int128.of(-5L, 0L),
                Int128.MIN.add(Int128.fromLong(8L)), Int128.MAX.subtract(Int128.fromLong(8L))}) {
            CollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(coordinate, 64, coordinate));
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.5D, 64.0D, coordinate, 0.5D);
            assertFalse(level.noCollision(AABB.fromSectorBounds(SectorAABB.around(position, 0.6D, 1.8D))));
            assertTrue(level.noCollision(AABB.fromSectorBounds(SectorAABB.around(position.add(1.0D, 0.0D, 0.0D), 0.6D, 1.8D))));
        }
    }

    @Test
    void findsHugeBlockAndReturnsLocalShape() {
        Int128 hugeX = Int128.of(1L << 36, -1L);
        Int128 hugeZ = Int128.of(-(1L << 36), 0L);
        BlockPos solidPos = new BlockPos(hugeX, 64, hugeZ);
        CollisionGetter level = new SingleBlockCollisionGetter(solidPos);
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(hugeX, 64, hugeZ);
        SectorVec3 player = SectorVec3.fromBlockAndFraction(hugeX, 0.25D, 64.0D, hugeZ, 0.25D);
        SectorAABB exactBox = SectorAABB.around(player, 0.6D, 1.8D);
        AABB localBox = exactBox.toLocalAABB(origin);

        List<VoxelShape> shapes = new java.util.ArrayList<>();
        for (VoxelShape shape : level.getSectorBlockCollisions(null, exactBox, localBox, origin)) {
            shapes.add(shape);
        }

        assertFalse(shapes.isEmpty());
        assertEquals(0.0D, shapes.get(0).bounds().minX, 0.0D);
        assertEquals(1.0D, shapes.get(0).bounds().maxX, 0.0D);
        assertEquals(0.0D, shapes.get(0).bounds().minZ, 0.0D);
        assertEquals(1.0D, shapes.get(0).bounds().maxZ, 0.0D);
    }

    @Test
    void findsSolidBlocksAtClippedWorldEdgesAndCorners() {
        Int128[][] coordinates = {
                {Int128.MIN, Int128.ZERO}, {Int128.MAX, Int128.ZERO},
                {Int128.ZERO, Int128.MIN}, {Int128.ZERO, Int128.MAX},
                {Int128.MIN, Int128.MIN}, {Int128.MIN, Int128.MAX},
                {Int128.MAX, Int128.MIN}, {Int128.MAX, Int128.MAX}
        };
        for (Int128[] coordinate : coordinates) {
            Int128 x = coordinate[0];
            Int128 z = coordinate[1];
            CollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, z));
            SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, z);
            SectorVec3 position = SectorVec3.fromBlockAndFraction(x, 0.5D, 64.0D, z, 0.5D);
            SectorAABB box = SectorAABB.around(position, 0.6D, 1.8D);
            List<VoxelShape> shapes = new java.util.ArrayList<>();
            for (VoxelShape shape : level.getSectorBlockCollisions(null, box, box.toLocalAABB(origin), origin)) {
                shapes.add(shape);
            }

            assertEquals(1, shapes.size(), "Collision at " + x + ", " + z);
            assertEquals(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D), shapes.get(0).bounds());
        }
    }

    @Test
    void rejectsAWorldSpanningSweepWithoutIterating() {
        SectorAABB box = new SectorAABB(Int128.MIN, 0.25D, 64.0D, Int128.ZERO, 0.25D,
                Int128.MAX, Math.nextDown(1.0D), 65.8D, Int128.ZERO, 0.75D);
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
        assertThrows(IllegalArgumentException.class, () -> box.toLocalAABB(origin));
        assertConservativeCollisionWithoutLookup(box, origin);
    }

    @Test
    void hugeSweepsAndSmallDistantBoxesCannotTunnelOrLookupChunks() {
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
        for (Int128 far : new Int128[] {Int128.ONE.shiftLeftSaturated(100),
                Int128.fromLong(1L << 62), Int128.of(0L, Long.MIN_VALUE),
                Int128.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L)}) {
            SectorAABB sweep = new SectorAABB(Int128.ZERO, 0.0D, 64.0D, Int128.ZERO, 0.0D,
                    far, 0.5D, 65.0D, Int128.ZERO, 0.5D);
            assertConservativeCollisionWithoutLookup(sweep, origin);
            SectorAABB distant = SectorAABB.around(SectorVec3.fromBlockAndFraction(
                    far, 0.5D, 64.0D, far, 0.5D), 0.5D, 1.0D);
            assertConservativeCollisionWithoutLookup(distant, origin);
        }
    }

    private static void assertConservativeCollisionWithoutLookup(SectorAABB box, SectorPhysicsOrigin origin) {
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(0L, 64, 0L));
        // The iterator independently validates the exact range even if supplied a small local box.
        AABB local = new AABB(0.0D, 0.0D, 0.0D, 0.5D, 1.0D, 0.5D);
        java.util.Iterator<VoxelShape> iterator = level.getSectorBlockCollisions(null, box, local, origin).iterator();
        assertTrue(iterator.hasNext());
        assertEquals(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D), iterator.next().bounds());
        assertFalse(iterator.hasNext());
        assertEquals(0, level.chunkLookups);
        assertEquals(0, level.blockLookups);
    }

    private static final class SingleBlockCollisionGetter implements CollisionGetter {
        private final BlockPos solid;
        private int chunkLookups;
        private int blockLookups;

        private SingleBlockCollisionGetter(BlockPos solid) {
            this.solid = solid;
        }

        @Override
        public WorldBorder getWorldBorder() {
            return new WorldBorder();
        }

        @Override
        @Nullable
        public BlockGetter getChunkForCollisions(long chunkX, long chunkZ) {
            throw new AssertionError("Exact collision lookup must not use the long bridge");
        }

        @Override
        @Nullable
        public BlockGetter getChunkForCollisions(Int128 chunkX, Int128 chunkZ) {
            ++this.chunkLookups;
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
            ++this.blockLookups;
            return this.solid.equals(pos) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
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
