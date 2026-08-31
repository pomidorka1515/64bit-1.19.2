package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class NodeEvaluatorTest {
   @Test
   void blockGoalDoesNotUsePassabilityFilteredNodeOverride() {
      NodeEvaluator evaluator = new SwimNodeEvaluator(false);
      Target target = evaluator.getGoal(new BlockPos(9_007_199_254_740_993L, 64,
            -9_007_199_254_740_993L));
      assertNotNull(target);
      assertEquals(9_007_199_254_740_993L, target.x);
      assertEquals(64, target.y);
      assertEquals(-9_007_199_254_740_993L, target.z);
   }
}
