package net.minecraft.world.entity.ai.behavior;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class RandomSwim extends RandomStroll {
   public static final int[][] XY_DISTANCE_TIERS = new int[][]{{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

   public RandomSwim(float p_147853_) {
      super(p_147853_);
   }

   protected boolean checkExtraStartConditions(ServerLevel p_147858_, PathfinderMob p_147859_) {
      return p_147859_.isInWaterOrBubble();
   }

   @Nullable
   protected SectorVec3 getTargetPos(PathfinderMob mob) {
      SectorVec3 previous = null;
      SectorVec3 candidate = null;

      for (int[] distance : XY_DISTANCE_TIERS) {
         if (previous == null) {
            candidate = BehaviorUtils.getRandomSwimmableSectorPos(mob, distance[0], distance[1]);
         } else {
            Vec3 direction = previous.relativeTo(mob.sectorPosition()).normalize()
                  .multiply((double)distance[0], (double)distance[1], (double)distance[0]);
            candidate = mob.sectorPosition().add(direction.x, direction.y, direction.z);
         }

         if (candidate == null || mob.level.getFluidState(candidate.blockPosition()).isEmpty()) {
            return previous;
         }

         previous = candidate;
      }

      return candidate;
   }
}