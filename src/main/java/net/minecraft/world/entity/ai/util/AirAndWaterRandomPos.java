package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class AirAndWaterRandomPos {
   @Nullable
   public static Vec3 getPos(PathfinderMob mob, int horizontalRange, int verticalRange, int startHeight,
                             double directionX, double directionZ, double maxAngle) {
      SectorVec3 exact = getSectorPos(mob, horizontalRange, verticalRange, startHeight,
            directionX, directionZ, maxAngle);
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPos(PathfinderMob mob, int horizontalRange, int verticalRange,
                                         int startHeight, double directionX, double directionZ,
                                         double maxAngle) {
      boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
      return RandomPos.generateRandomSectorPos(mob, () -> generateRandomPos(mob, horizontalRange,
            verticalRange, startHeight, directionX, directionZ, maxAngle, restricted));
   }

   @Nullable
   public static BlockPos generateRandomPos(PathfinderMob p_148366_, int p_148367_, int p_148368_, int p_148369_, double p_148370_, double p_148371_, double p_148372_, boolean p_148373_) {
      BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(p_148366_.getRandom(), p_148367_, p_148368_, p_148369_, p_148370_, p_148371_, p_148372_);
      if (blockpos == null) {
         return null;
      } else {
         BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(p_148366_, p_148367_, p_148366_.getRandom(), blockpos);
         if (!GoalUtils.isOutsideLimits(blockpos1, p_148366_) && !GoalUtils.isRestricted(p_148373_, p_148366_, blockpos1)) {
            blockpos1 = RandomPos.moveUpOutOfSolid(blockpos1, p_148366_.level.getMaxBuildHeight(), (p_148376_) -> {
               return GoalUtils.isSolid(p_148366_, p_148376_);
            });
            return GoalUtils.hasMalus(p_148366_, blockpos1) ? null : blockpos1;
         } else {
            return null;
         }
      }
   }
}