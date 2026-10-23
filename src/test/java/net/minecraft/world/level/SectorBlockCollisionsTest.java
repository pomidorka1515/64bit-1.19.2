package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
    void scopedIteratorReusesStateAfterEarlyExitAndKeepsNestedQueriesIndependent() {
        Int128 x = Int128.of(7L, -1L);
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, x));
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, x);
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(x, 0.5D, 64, x, 0.5D), 0.6D, 1.8D);
        AABB local = box.toLocalAABB(origin);
        SectorBlockCollisions first;
        try (var outer = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
            first = outer;
            assertTrue(outer.hasNext()); // Leave the prefetched shape unconsumed.
            try (var inner = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
                assertNotSame(outer, inner);
                assertTrue(inner.hasNext());
                inner.next();
                assertFalse(inner.hasNext());
            }
            assertEquals(new AABB(0, 0, 0, 1, 1, 1), outer.next().bounds());
        }
        level.unloaded = true;
        try (var reused = SectorBlockCollisions.acquire(level, null, box, local, origin, true)) {
            assertSame(first, reused);
            assertFalse(reused.hasNext(), "Reset must discard prefetched shapes and chunk hits");
        }
        level.unloaded = false;
        try (var reused = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
            assertTrue(reused.hasNext(), "Unloaded entries must not survive a new query");
        }
    }

    @Test
    void leasedPartialShapesMatchOrdinaryIteratorAndOrdinaryShapesSurviveReuse() {
        Int128 x = Int128.of(-7L, -1L);
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, x));
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x.subtract(Int128.ONE), 63, x);
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(x, 0.5, 64, x, 0.5), 0.6, 1.8);
        AABB local = box.toLocalAABB(origin);
        for (BlockState state : new BlockState[] {Blocks.STONE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState()}) {
            level.state = state;
            VoxelShape ordinary = level.getSectorBlockCollisions(null, box, local, origin).iterator().next();
            var boxes = ordinary.toAabbs();
            try (var collisions = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
                assertEquals(boxes, collisions.next().toAabbs());
                assertFalse(collisions.hasNext());
            }
            assertEquals(boxes, ordinary.toAabbs());
        }
    }

    @Test
    void emittedCubeSweepsDoNotAllocateAfterWarmup() {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.lang.management.ManagementFactory.getThreadMXBean()
                instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Int128 x = Int128.of(7L, -1L);
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, x));
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, x);
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(x, 0.5, 64, x, 0.5), 0.6, 1.8);
        AABB local = box.toLocalAABB(origin);
        long thread = Thread.currentThread().getId();
        for (int pass = 0; pass < 2; ++pass) {
            long before = bean.getThreadAllocatedBytes(thread);
            int shapes = 0;
            for (int i = 0; i < 20000; ++i) {
                try (var collisions = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
                    while (collisions.hasNext()) {
                        shapes += (int) collisions.next().max(net.minecraft.core.Direction.Axis.X);
                    }
                }
            }
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            assertEquals(20000, shapes);
            if (pass == 1) assertTrue(allocated < 20000 * 8L, "Allocated " + allocated + " bytes for 20000 sweeps");
        }
    }

    @Test
    void leasedShapesRemainDistinctUntilCloseAndAreReusedAcrossSweeps() {
        Int128 x = Int128.of(7L, -1L);
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, x));
        level.unloaded = true; // Several conservative cubes, crossing a low-word carry.
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, x);
        SectorAABB box = new SectorAABB(x, 0.25, 64, x, 0.25,
                x.add(Int128.ONE), 0.75, 65.8, x, 0.75);
        AABB local = box.toLocalAABB(origin);
        VoxelShape first;
        VoxelShape second;
        try (var collisions = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
            first = collisions.next();
            AABB bounds = first.bounds();
            // Populate the face cache; reuse must invalidate it.
            first.getFaceShape(net.minecraft.core.Direction.EAST);
            second = collisions.next();
            assertNotSame(first, second);
            while (collisions.hasNext()) collisions.next();
            assertEquals(bounds, first.bounds());
            try (var nested = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
                assertNotSame(first, nested.next());
                assertEquals(bounds, first.bounds());
            }
        }
        try (var collisions = SectorBlockCollisions.acquire(level, null, box, local, origin, false)) {
            assertSame(first, collisions.next());
            assertSame(second, collisions.next());
        }
    }

    @Test
    void cachesFourBoundaryChunksIncludingUnloadedEntries() {
        for (boolean unloaded : new boolean[] {false, true}) {
            // Both axes cross a low-word carry as well as a chunk boundary.
            Int128 x = Int128.of(9L, 0L);
            SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x, 64, x));
            level.unloaded = unloaded;
            SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, x);
            SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(x, 0.0D, 64, x, 0.0D), 0.6D, 1.8D);
            try (var collisions = SectorBlockCollisions.acquire(level, null, box, box.toLocalAABB(origin), origin, false)) {
                while (collisions.hasNext()) collisions.next();
            }
            assertEquals(4, level.chunkLookups);
        }
    }

    @Test
    void cacheEvictionDoesNotLoseCollisionsAcrossMoreThanFourChunks() {
        Int128 x = Int128.of(-11L, 0L);
        SingleBlockCollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(x.add(Int128.fromLong(47)), 64, x));
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(x, 64, x);
        SectorAABB box = new SectorAABB(x, 0.25, 64, x, 0.25,
                x.add(Int128.fromLong(48)), 0.75, 65.8, x.add(Int128.fromLong(16)), 0.75);
        int shapes = 0;
        try (var collisions = SectorBlockCollisions.acquire(level, null, box, box.toLocalAABB(origin), origin, false)) {
            while (collisions.hasNext()) {
                assertEquals(47.0D, collisions.next().bounds().minX);
                ++shapes;
            }
        }
        assertEquals(1, shapes);
        assertTrue(level.chunkLookups > 4);
    }

    @Test
    void iterableIteratorsRemainIndependentAndRepeatable() {
        CollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(0L, 64, 0L));
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
        SectorAABB box = SectorAABB.around(SectorVec3.fromBlockAndFraction(0L, 0.5D, 64, 0L, 0.5D), 0.6D, 1.8D);
        var iterable = level.getSectorBlockCollisions(null, box, box.toLocalAABB(origin), origin);
        var first = iterable.iterator();
        var second = iterable.iterator();
        assertNotSame(first, second);
        assertTrue(first.hasNext());
        assertTrue(second.hasNext());
        assertEquals(first.next().bounds(), second.next().bounds());
        assertFalse(first.hasNext());
        assertThrows(java.util.NoSuchElementException.class, first::next);
        assertTrue(iterable.iterator().hasNext());
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
        private boolean unloaded;
        private BlockState state = Blocks.STONE.defaultBlockState();

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
            return this.unloaded ? null : this;
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
            return this.solid.equals(pos) ? this.state : Blocks.AIR.defaultBlockState();
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
