package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PathTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static List<Int1088> coordinates() {
        List<Int1088> values = new ArrayList<>(List.of(Int1088.ZERO, Int1088.MIN, Int1088.MAX,
                Int1088.ONE.shiftLeftSaturated(127), Int1088.fromDoubleFloorSaturated(Double.MAX_VALUE)));
        for (int bit = 64; bit <= 1024; bit += 64) {
            BigInteger seam = BigInteger.ONE.shiftLeft(bit);
            for (int sign : new int[] {-1, 1}) {
                for (int offset = -1; offset <= 1; ++offset) {
                    values.add(Int1088.parseDecimal(seam.multiply(BigInteger.valueOf(sign))
                            .add(BigInteger.valueOf(offset)).toString()));
                }
            }
        }
        return values;
    }

    @Test
    void pathIdentityPositionsAndWireRetainEveryLimb() throws Exception {
        TestMob mob = mob();
        for (Int1088 x : coordinates()) {
            Node first = new Node(x, 64, x.not());
            Node last = new Node(x.addSaturated(Int1088.ONE), 65, x.not().addSaturated(Int1088.fromLong(-1L)));
            BlockPos target = last.asBlockPos();
            Path path = new Path(new ArrayList<>(List.of(first, last)), target, true);
            assertEquals(0.0F, path.getDistToTarget());
            assertEquals(first.asBlockPos(), path.getNextNodePos());
            SectorVec3 exact = SectorVec3.fromBlockAndFraction(x, 0.5D, 64.0D, x.not(), 0.5D);
            assertEquals(exact, path.getNextExactEntityPos(mob));
            assertEquals(exact, SectorVec3.fromApproximate(path.getNextEntityPos(mob)));
            assertTrue(path.sameAs(new Path(List.of(first, last), target, true)));
            assertFalse(path.sameAs(new Path(List.of(new Node(x.not(), 64, x), last), target, true)));
            path.advance();
            assertSame(first, path.getPreviousNode());
            first.closed = true;
            last.walkedDistance = 3.5F;
            last.costMalus = 2.0F;
            last.type = BlockPathTypes.WATER;
            last.f = 7.0F;
            path.setDebug(new Node[] {last}, new Node[] {first}, Set.of(new Target(last)));
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                path.writeToStream(buffer);
                Path decoded = Path.createFromStream(buffer);
                assertEquals(0, buffer.readableBytes());
                assertTrue(path.sameAs(decoded));
                assertEquals(target, decoded.getTarget());
                assertEquals(1, decoded.getNextNodeIndex());
                assertTrue(decoded.canReach());
                assertEquals(last, decoded.getOpenSet()[0]);
                assertEquals(3.5F, decoded.getOpenSet()[0].walkedDistance);
                assertEquals(2.0F, decoded.getOpenSet()[0].costMalus);
                assertEquals(BlockPathTypes.WATER, decoded.getOpenSet()[0].type);
                assertEquals(7.0F, decoded.getOpenSet()[0].f);
                assertTrue(decoded.getClosedSet()[0].closed);
            } finally {
                buffer.release();
            }
            path.advance();
            assertTrue(path.isDone());
        }
    }

    @Test
    void localNodeDistancesAndCloneAreIndependentOfAbsoluteMagnitude() {
        for (Int1088 x : coordinates()) {
            Int1088 startX = x.equals(Int1088.MAX) ? x.addSaturated(Int1088.fromLong(-8L)) : x;
            Int1088 startZ = startX.not();
            Node first = new Node(startX, 64, startZ);
            Node second = new Node(startX.addSaturated(Int1088.fromLong(3L)), 68,
                    startZ.addSaturated(Int1088.fromLong(-4L)));
            assertEquals((float) Math.sqrt(41.0D), first.distanceTo(second));
            assertEquals(5.0F, first.distanceToXZ(second));
            assertEquals(41.0F, first.distanceToSqr(second));
            assertEquals(11.0F, first.distanceManhattan(second));
            assertEquals(first.distanceTo(second), first.distanceTo(second.asBlockPos()));
            assertEquals(41.0F, first.distanceToSqr(second.asBlockPos()));
            assertEquals(11.0F, first.distanceManhattan(second.asBlockPos()));
            first.cameFrom = second;
            first.closed = true;
            first.g = 2.0F;
            first.h = 3.0F;
            first.f = 5.0F;
            first.heapIdx = 7;
            first.walkedDistance = 9.0F;
            first.costMalus = 1.0F;
            first.type = BlockPathTypes.WATER;
            Node clone = first.cloneAndMove(second.x, second.y, second.z);
            assertEquals(second, clone);
            assertSame(second, clone.cameFrom);
            assertTrue(clone.closed);
            assertEquals(first.g, clone.g);
            assertEquals(first.h, clone.h);
            assertEquals(first.f, clone.f);
            assertEquals(first.heapIdx, clone.heapIdx);
            assertEquals(first.walkedDistance, clone.walkedDistance);
            assertEquals(first.costMalus, clone.costMalus);
            assertEquals(first.type, clone.type);
        }
        Node min = new Node(Int1088.MIN, Integer.MIN_VALUE, Int1088.MIN);
        Node max = new Node(Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX);
        assertTrue(min.distanceTo(max) > 1.0E18F);
        assertTrue(Float.isFinite(min.distanceToSqr(max)));
        assertEquals(4294967296.0F, new Node(0L, Integer.MIN_VALUE, 0L)
                .distanceTo(new Node(0L, Integer.MAX_VALUE, 0L)));
    }

    @Test
    void fallbackNavigationUsesValueEqualityForIndependentlyCreatedCoordinates() throws Exception {
        TestMob mob = mob();
        for (Int1088 x : coordinates()) {
            Int1088 z = x.not();
            Node node = new Node(x, 64, z);
            for (boolean flying : new boolean[] {false, true}) {
                Path path = new Path(List.of(node), node.asBlockPos(), true);
                // Equal coordinates with distinct object identities, as after decoding.
                SectorVec3 position = SectorVec3.fromBlockAndFraction(copy(x), 0.5D,
                        flying ? 64.5D : 65.0D, copy(z), 0.5D);
                set(Entity.class, mob, "sectorPosition", position);
                set(Entity.class, mob, "blockPosition", position.blockPosition());
                set(Entity.class, mob, "position", new Vec3(0.0D, position.y(), 0.0D));
                PathNavigation navigation = flying ? new PausedFlyingNavigation(mob) : new PausedNavigation(mob);
                set(PathNavigation.class, navigation, "path", path);
                navigation.tick();
                assertTrue(path.isDone(), "fallback failed at " + x + ", flying=" + flying);

                // A different high limb with the same low word must not advance the path.
                Node distant = new Node(x.xor(Int1088.ONE.shiftLeftSaturated(1024)), 64, z);
                Path other = new Path(List.of(distant), distant.asBlockPos(), true);
                set(PathNavigation.class, navigation, "path", other);
                navigation.tick();
                assertFalse(other.isDone());
            }
        }
    }

    @Test
    void regionSnapshotLookupUsesFullChunkDifferencesAndRejectsUnsafeSpans() throws Exception {
        for (Int1088 coordinate : coordinates()) {
            Int1088 x = snapshotOrigin(coordinate.shiftRight(4));
            Int1088 z = snapshotOrigin(coordinate.not().shiftRight(4));
            SnapshotRegion region = allocate(SnapshotRegion.class);
            ChunkAccess first = allocate(ProtoChunk.class);
            ChunkAccess last = allocate(ProtoChunk.class);
            set(PathNavigationRegion.class, region, "centerX", x);
            set(PathNavigationRegion.class, region, "centerZ", z);
            set(PathNavigationRegion.class, region, "chunks", new ChunkAccess[][] {{first, null}, {null, last}});
            assertSame(first, region.getChunkForCollisions(copy(x), copy(z)));
            assertSame(last, region.getChunkForCollisions(WorldBounds.addChunkOffset(x, 1), WorldBounds.addChunkOffset(z, 1)));
            assertNull(region.getChunkForCollisions(WorldBounds.addChunkOffset(x, -1), z));
            assertNull(region.getChunkForCollisions(x, WorldBounds.addChunkOffset(z, 2)));
            assertNull(region.getChunkForCollisions(x.xor(Int1088.ONE.shiftLeftSaturated(1024)), z));
            assertNull(region.getChunkForCollisions(x, z.xor(Int1088.ONE.shiftLeftSaturated(1024))));
        }
        assertThrows(IllegalArgumentException.class, () -> new PathNavigationRegion(null,
                new BlockPos(16L, 0, 0L), BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PathNavigationRegion(null,
                new BlockPos(Int1088.MIN, 0, Int1088.ZERO), new BlockPos(Int1088.MAX, 0, Int1088.ZERO)));
    }

    private static Int1088 copy(Int1088 value) {
        return Int1088.parseDecimal(value.toDecimalString());
    }

    private static Int1088 snapshotOrigin(Int1088 value) {
        // Leave room for the two-chunk fixture and its outside probes at either world edge.
        if (value.equals(WorldBounds.MAX_CHUNK)) return WorldBounds.addChunkOffset(value, -2);
        if (value.equals(WorldBounds.MIN_CHUNK)) return WorldBounds.addChunkOffset(value, 2);
        return value;
    }

    static TestMob mob() throws Exception {
        TestMob mob = allocate(TestMob.class);
        set(Entity.class, mob, "dimensions", EntityDimensions.fixed(0.6F, 1.8F));
        mob.control = new MoveControl(mob);
        return mob;
    }

    static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static class TestMob extends Mob {
        private MoveControl control;
        private TestMob() { super(EntityType.PIG, null); }
        public double getAttributeValue(Attribute attribute) { return 16.0D; }
        public float getPathfindingMalus(BlockPathTypes type) { return 0.0F; }
        public MoveControl getMoveControl() { return this.control; }
    }

    private static class PausedNavigation extends PathNavigation {
        PausedNavigation(Mob mob) { super(mob, null); }
        protected PathFinder createPathFinder(int limit) { return new PathFinder(new WalkNodeEvaluator(), limit); }
        protected boolean canUpdatePath() { return false; }
        protected double getGroundY(BlockPos pos, double y) { return y; }
    }

    private static class PausedFlyingNavigation extends FlyingPathNavigation {
        PausedFlyingNavigation(Mob mob) { super(mob, null); }
        protected boolean canUpdatePath() { return false; }
    }

    private static class SnapshotRegion extends PathNavigationRegion {
        private SnapshotRegion() { super(null, BlockPos.ZERO, BlockPos.ZERO); }
    }
}
