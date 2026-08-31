package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class HoverRandomPos {
   @Nullable
   public static Vec3 getPos(PathfinderMob mob, int horizontalRange, int verticalRange,
                             double directionX, double directionZ, float maxAngle,
                             int maxAboveSolid, int minAboveSolid) {
      SectorVec3 exact = getSectorPos(mob, horizontalRange, verticalRange, directionX, directionZ,
            maxAngle, maxAboveSolid, minAboveSolid);
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPos(PathfinderMob mob, int horizontalRange, int verticalRange,
                                         double directionX, double directionZ, float maxAngle,
                                         int maxAboveSolid, int minAboveSolid) {
      boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
      return RandomPos.generateRandomSectorPos(mob, () -> {
         BlockPos offset = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), horizontalRange,
               verticalRange, 0, directionX, directionZ, (double)maxAngle);
         if (offset == null) return null;
         BlockPos candidate = LandRandomPos.generateRandomPosTowardDirection(mob, horizontalRange,
               restricted, offset);
         if (candidate == null) return null;
         candidate = RandomPos.moveUpToAboveSolid(candidate,
               mob.getRandom().nextInt(maxAboveSolid - minAboveSolid + 1) + minAboveSolid,
               mob.level.getMaxBuildHeight(), position -> GoalUtils.isSolid(mob, position));
         return !GoalUtils.isWater(mob, candidate) && !GoalUtils.hasMalus(mob, candidate) ? candidate : null;
      });
   }
}