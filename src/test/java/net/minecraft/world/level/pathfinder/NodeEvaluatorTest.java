package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

class NodeEvaluatorTest {
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
