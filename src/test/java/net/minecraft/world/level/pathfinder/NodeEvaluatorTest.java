package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

class NodeEvaluatorTest {
    @Test
    void blockGoalDoesNotUsePassabilityFilteredNodeOverride() {
        NodeEvaluator evaluator = new SwimNodeEvaluator(false);
        for (Int128 coordinate : new Int128[] {Int128.fromLong(9_007_199_254_740_993L),
                Int128.of(123L, -1L), Int128.MIN, Int128.MAX}) {
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
        Node first = evaluator.getNodeUnchecked(Int128.of(1L, 7L), 64, Int128.of(2L, 9L));
        Node differentX = evaluator.getNodeUnchecked(Int128.of(3L, 7L), 64, Int128.of(2L, 9L));
        Node differentZ = evaluator.getNodeUnchecked(Int128.of(1L, 7L), 64, Int128.of(4L, 9L));
        assertSame(first, evaluator.getNodeUnchecked(Int128.of(1L, 7L), 64, Int128.of(2L, 9L)));
        assertNotSame(first, differentX);
        assertNotSame(first, differentZ);
        assertEquals(Int128.of(3L, 7L), differentX.x);
        assertEquals(Int128.of(4L, 9L), differentZ.z);
    }
}
