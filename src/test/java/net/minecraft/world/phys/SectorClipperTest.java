package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.SectorClipper;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectorClipperTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void raysCrossEverySignedLimbSeamOnBothAxes() {
        List<Int1088> coordinates = new ArrayList<>();
        coordinates.add(Int1088.ZERO);
        coordinates.add(Int1088.ONE.shiftLeftSaturated(127));
        for (int bit = 64; bit <= 1024; bit += 64) {
            Int1088 seam = Int1088.ONE.shiftLeftSaturated(bit);
            coordinates.add(seam);
            coordinates.add(seam.negate());
        }
        for (Int1088 seam : coordinates) {
            for (boolean alongX : new boolean[] {true, false}) {
                for (int direction : new int[] {-1, 1}) {
                    BlockPos solid = new BlockPos(seam, 64, seam.negate());
                    TestLevel level = new TestLevel(solid, Blocks.STONE.defaultBlockState());
                    SectorVec3 center = SectorVec3.fromBlockAndFraction(solid.getX(), 0.5, 64.5, solid.getZ(), 0.5);
                    SectorVec3 from = center.add(alongX ? -2 * direction : 0, 0, alongX ? 0 : -2 * direction);
                    SectorVec3 to = center.add(alongX ? 2 * direction : 0, 0, alongX ? 0 : 2 * direction);
                    BlockHitResult hit = clip(level, from, to, ClipContext.Fluid.NONE);
                    assertEquals(HitResult.Type.BLOCK, hit.getType());
                    assertEquals(solid, hit.getBlockPos());
                    assertEquals(alongX ? (direction > 0 ? Direction.WEST : Direction.EAST)
                            : (direction > 0 ? Direction.NORTH : Direction.SOUTH), hit.getDirection());
                    assertEquals(center.add(alongX ? -0.5 * direction : 0, 0,
                            alongX ? 0 : -0.5 * direction), hit.getExactLocation());
                    assertEquals(hit.getExactLocation(), ExactVec3.position(hit.getLocation()));
                }
            }
        }
    }

    @Test
    void partialShapesFluidsVerticalRaysInsideHitsAndMissesKeepExactLocations() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024);
        BlockPos solid = new BlockPos(x, 64, x.negate());
        SectorVec3 center = SectorVec3.fromBlockAndFraction(solid.getX(), 0.5, 64.25, solid.getZ(), 0.5);
        TestLevel slab = new TestLevel(solid, Blocks.OAK_SLAB.defaultBlockState());
        assertEquals(center.withY(64.5), clip(slab, center.withY(67), center.withY(63), ClipContext.Fluid.NONE).getExactLocation());
        BlockHitResult inside = clip(slab, center, center.add(2, 0, 0), ClipContext.Fluid.NONE);
        assertTrue(inside.isInside());
        assertEquals(center.add(0.002, 0, 0), inside.getExactLocation());
        SectorVec3 end = center.add(2, 0.5, 0);
        BlockHitResult miss = clip(slab, center.add(-2, 0.5, 0), end, ClipContext.Fluid.NONE);
        assertEquals(HitResult.Type.MISS, miss.getType());
        assertEquals(end, miss.getExactLocation());
        assertEquals(end.blockPosition(), miss.getBlockPos());
        TestLevel water = new TestLevel(solid, Blocks.WATER.defaultBlockState());
        assertEquals(HitResult.Type.MISS, clip(water, center.add(-2, 0, 0), center.add(2, 0, 0), ClipContext.Fluid.NONE).getType());
        assertEquals(solid, clip(water, center.add(-2, 0, 0), center.add(2, 0, 0), ClipContext.Fluid.SOURCE_ONLY).getBlockPos());
        assertEquals(HitResult.Type.MISS, clip(slab, center, center, ClipContext.Fluid.NONE).getType());
    }

    @Test
    void edgeRaysHitRealBlocksWithoutWrappingAndCameraOutsideDomainIsAir() {
        for (Int1088 edge : new Int1088[] {Int1088.MIN, Int1088.MAX}) {
            int outward = edge.equals(Int1088.MAX) ? 1 : -1;
            BlockPos solid = new BlockPos(edge, 64, edge);
            TestLevel level = new TestLevel(solid, Blocks.STONE.defaultBlockState());
            SectorVec3 anchor = SectorVec3.fromBlockAndFraction(edge, 0.5, 64.5, edge, 0.5);
            BlockHitResult hit = clip(level, anchor.add(-2 * outward, 0, 0), anchor, ClipContext.Fluid.NONE);
            assertEquals(solid, hit.getBlockPos());
            assertEquals(anchor.add(-0.5 * outward, 0, 0), hit.getExactLocation());
            level.visited.clear();
            assertEquals(Double.POSITIVE_INFINITY, SectorClipper.clipCameraDistance(level, anchor,
                    new Vec3(2 * outward, 0, 0), new Vec3(4 * outward, 0, 0), new TestEntity()));
            assertTrue(level.visited.isEmpty(), "Out-of-domain camera blocks must not alias the edge");
        }
    }

    @Test
    void unsafeRaysAreRejectedBeforeWorldLookup() {
        TestLevel level = new TestLevel(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SectorVec3 from = SectorVec3.fromBlockAndFraction(0, 0.5, 64.5, 0, 0.5);
        for (Int1088 far : new Int1088[] {Int1088.fromLong(WorldBounds.MAX_SAFE_LOCAL_SPAN + 1),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 to = SectorVec3.fromBlockAndFraction(far, 0.5, 64.5, far, 0.5);
            assertThrows(IllegalArgumentException.class, () -> clip(level, from, to, ClipContext.Fluid.NONE));
        }
        assertTrue(level.visited.isEmpty());
    }

    @Test
    void hitResultsSnapshotMutableBlockAddressesAndRetainExactCarriers() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 64, x.negate());
        BlockPos expected = cursor.immutable();
        SectorVec3 location = SectorVec3.fromBlockAndFraction(x, 0.25, 64.5, x.negate(), 0.75);
        BlockHitResult hit = new BlockHitResult(location, Direction.WEST, cursor, false);
        BlockHitResult moved = hit.withPosition(cursor);
        BlockHitResult miss = BlockHitResult.missExact(location, Direction.EAST, cursor);
        BlockHitResult shapeHit = Shapes.block().clip(location.add(-2, 0, 0), location, cursor);
        cursor.set(0, 0, 0);
        for (BlockHitResult result : new BlockHitResult[] {hit, moved, miss, shapeHit}) {
            assertEquals(expected, result.getBlockPos());
            assertNotNull(result.getExactLocation());
        }
        assertEquals(location, hit.withDirection(Direction.UP).getExactLocation());
        assertEquals(location, new EntityHitResult(null, location).getExactLocation());
        assertEquals(location, new EntityHitResult(null, location.toExactVec3()).getExactLocation());
    }

    private static BlockHitResult clip(TestLevel level, SectorVec3 from, SectorVec3 to, ClipContext.Fluid fluid) {
        return SectorClipper.clip(level, from, to, new TestEntity(), ClipContext.Block.COLLIDER, fluid);
    }

    private static final class TestEntity extends net.minecraft.world.entity.Entity {
        TestEntity() { super(net.minecraft.world.entity.EntityType.PIG, null); }
        protected void defineSynchedData() {}
        protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
        protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
        public net.minecraft.network.protocol.Packet<?> getAddEntityPacket() { return null; }
    }

    private static final class TestLevel implements BlockGetter {
        private final BlockPos solid;
        private final BlockState state;
        private final List<BlockPos> visited = new ArrayList<>();

        TestLevel(BlockPos solid, BlockState state) {
            this.solid = solid;
            this.state = state;
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            this.visited.add(pos.immutable());
            return this.solid.equals(pos) ? this.state : Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
