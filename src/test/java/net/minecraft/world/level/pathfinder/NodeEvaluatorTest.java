package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.Direction;
import net.minecraft.world.level.PathNavigationRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class NodeEvaluatorTest {
    @BeforeAll
    static void bootstrap() { PathTest.bootstrap(); }

    @Test
    void everyLimbParticipatesInOpenClosedAndGoalIdentity() {
        NodeEvaluator evaluator = new SwimNodeEvaluator(false);
        Set<Node> distinct = new HashSet<>();
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            long[] words = new long[Int1088.LIMBS];
            words[limb] = 7L;
            Int1088 coordinate = Int1088.of(words);
            Node x = evaluator.getNodeUnchecked(coordinate, 64, Int1088.ZERO);
            Node z = evaluator.getNodeUnchecked(Int1088.ZERO, 64, coordinate);
            assertTrue(distinct.add(x));
            assertTrue(distinct.add(z));
            x.closed = true;
            x.heapIdx = 3;
            Node again = evaluator.getNodeUnchecked(Int1088.of(words), 64, Int1088.ZERO);
            assertSame(x, again);
            assertTrue(again.closed);
            assertTrue(again.inOpenSet());
            assertFalse(z.closed);
            assertFalse(z.inOpenSet());
            assertEquals(x, evaluator.getGoal(x.asBlockPos()));
        }
    }

    @Test
    void allEvaluatorsExpandAcrossSignedSeamsAndSaturateWithoutWrapping() throws Exception {
        NodeEvaluator[] evaluators = {new OpenWalkEvaluator(PathTest.mob()),
                new OpenAmphibiousEvaluator(PathTest.mob()), new OpenSwimEvaluator(), new FlyNodeEvaluator() {
            protected Node getNode(Int1088 x, int y, Int1088 z) {
                return this.getNodeUnchecked(x, y, z);
            }
        }};
        for (NodeEvaluator evaluator : evaluators) {
            for (Int1088 x : PathTest.coordinates()) {
                Node center = evaluator.getNodeUnchecked(x, 64, x.not());
                Node[] neighbors = new Node[32];
                int count = evaluator.getNeighbors(neighbors, center);
                Set<BlockPos> expected = new HashSet<>();
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dz = -1; dz <= 1; ++dz) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            if (!(evaluator instanceof FlyNodeEvaluator) && dy != 0 && (dx != 0 || dz != 0)) continue;
                            if (evaluator instanceof OpenWalkEvaluator && dy != 0) continue;
                            expected.add(new BlockPos(oracleAdd(x, dx), 64 + dy, oracleAdd(x.not(), dz)));
                        }
                    }
                }
                Set<BlockPos> actual = new HashSet<>();
                for (int index = 0; index < count; ++index) {
                    Node node = neighbors[index];
                    actual.add(node.asBlockPos());
                    assertSame(node, evaluator.getNodeUnchecked(node.x, node.y, node.z));
                }
                assertEquals(expected, actual);
                // Closed nodes (including saturated self-neighbors) cannot re-enter the open set.
                for (int index = 0; index < count; ++index) neighbors[index].closed = true;
                assertEquals(0, evaluator.getNeighbors(new Node[32], center));
                evaluator.nodes.clear();
            }
        }
    }

    @Test
    void astarFindsWideTargetsAcrossSeamsAndInwardFromBothEdges() throws Exception {
        Mob mob = PathTest.mob();
        ProfilerRegion region = PathTest.allocate(ProfilerRegion.class);
        for (Int1088 x : PathTest.coordinates()) {
            OpenSwimEvaluator evaluator = new OpenSwimEvaluator();
            evaluator.start = new BlockPos(x, 64, x.not());
            int direction = x.equals(Int1088.MAX) ? -1 : 1;
            BlockPos target = new BlockPos(oracleAdd(x, direction * 4), 64, x.not());
            PathFinder finder = new PathFinder(evaluator, 256);
            for (int search = 0; search < 2; ++search) {
                Path path = finder.findPath(region, mob, Set.of(target), 16.0F, 0, 1.0F);
                assertNotNull(path);
                assertTrue(path.canReach());
                assertEquals(target, path.getTarget());
                assertEquals(target, path.getEndNode().asBlockPos());
                assertEquals(5, path.getNodeCount());
                for (int index = 0; index < path.getNodeCount(); ++index) {
                    assertEquals(new BlockPos(oracleAdd(x, direction * index), 64, x.not()), path.getNodePos(index));
                }
            }
        }
    }

    private static Int1088 oracleAdd(Int1088 coordinate, int offset) {
        BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);
        return Int1088.parseDecimal(new BigInteger(coordinate.toDecimalString())
                .add(BigInteger.valueOf(offset)).max(min).min(max).toString());
    }

    private static class OpenSwimEvaluator extends SwimNodeEvaluator {
        private BlockPos start;
        OpenSwimEvaluator() { super(false); }
        protected Node getNode(Int1088 x, int y, Int1088 z) { return this.getNodeUnchecked(x, y, z); }
        public Node getStart() { return this.getNodeUnchecked(start.getX(), start.getY(), start.getZ()); }
    }

    // Isolate address expansion from world passability; the real getNeighbors implementations run.
    private static class OpenWalkEvaluator extends WalkNodeEvaluator {
        OpenWalkEvaluator(Mob mob) { this.mob = mob; }
        protected BlockPathTypes getCachedBlockType(Mob mob, Int1088 x, int y, Int1088 z) {
            return BlockPathTypes.WALKABLE;
        }
        protected double getFloorLevel(BlockPos pos) { return pos.getY(); }
        protected Node findAcceptedNode(Int1088 x, int y, Int1088 z, int step, double floor,
                Direction direction, BlockPathTypes previous) {
            return this.getNodeUnchecked(x, y, z);
        }
    }

    private static class OpenAmphibiousEvaluator extends AmphibiousNodeEvaluator {
        OpenAmphibiousEvaluator(Mob mob) { super(false); this.mob = mob; }
        protected BlockPathTypes getCachedBlockType(Mob mob, Int1088 x, int y, Int1088 z) {
            return BlockPathTypes.WATER;
        }
        protected double getFloorLevel(BlockPos pos) { return pos.getY(); }
        protected Node findAcceptedNode(Int1088 x, int y, Int1088 z, int step, double floor,
                Direction direction, BlockPathTypes previous) {
            Node node = this.getNodeUnchecked(x, y, z);
            node.type = BlockPathTypes.WATER;
            return node;
        }
    }

    private static class ProfilerRegion extends PathNavigationRegion {
        private ProfilerRegion() { super(null, BlockPos.ZERO, BlockPos.ZERO); }
        public ProfilerFiller getProfiler() { return InactiveProfiler.INSTANCE; }
    }

    @Test
    void blockGoalDoesNotUsePassabilityFilteredNodeOverride() {
        NodeEvaluator evaluator = new SwimNodeEvaluator(false);
        for (Int1088 coordinate : new Int1088[] {Int1088.fromLong(9_007_199_254_740_993L),
                Int1088.of(-1L, 123L), Int1088.MIN, Int1088.MAX}) {
            Target target = evaluator.getGoal(new BlockPos(coordinate, 64, coordinate));
            assertNotNull(target);
            assertEquals(coordinate, target.x);
            assertEquals(64, target.y);
            assertEquals(coordinate, target.z);
        }
    }

    @Test
    void nodeCacheDoesNotAliasMatchingLowWords() {
        NodeEvaluator evaluator = new SwimNodeEvaluator(false);
        Node first = evaluator.getNodeUnchecked(Int1088.of(7L, 1L), 64, Int1088.of(9L, 2L));
        Node differentX = evaluator.getNodeUnchecked(Int1088.of(7L, 3L), 64, Int1088.of(9L, 2L));
        Node differentZ = evaluator.getNodeUnchecked(Int1088.of(7L, 1L), 64, Int1088.of(9L, 4L));
        assertSame(first, evaluator.getNodeUnchecked(Int1088.of(7L, 1L), 64, Int1088.of(9L, 2L)));
        assertNotSame(first, differentX);
        assertNotSame(first, differentZ);
        assertEquals(Int1088.of(7L, 3L), differentX.x);
        assertEquals(Int1088.of(9L, 4L), differentZ.z);
    }
}
