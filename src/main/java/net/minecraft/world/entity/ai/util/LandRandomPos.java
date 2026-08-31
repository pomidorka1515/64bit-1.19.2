package net.minecraft.world.entity.ai.util;

import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class LandRandomPos {
   @Nullable
   public static Vec3 getPos(PathfinderMob mob, int horizontalRange, int verticalRange) {
      SectorVec3 exact = getSectorPos(mob, horizontalRange, verticalRange);
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPos(PathfinderMob mob, int horizontalRange, int verticalRange) {
      return getSectorPos(mob, horizontalRange, verticalRange, mob::getWalkTargetValue);
   }

   @Nullable
   public static Vec3 getPos(PathfinderMob mob, int horizontalRange, int verticalRange,
                             ToDoubleFunction<BlockPos> score) {
      SectorVec3 exact = getSectorPos(mob, horizontalRange, verticalRange, score);
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPos(PathfinderMob mob, int horizontalRange, int verticalRange,
                                         ToDoubleFunction<BlockPos> score) {
      boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
      return RandomPos.generateRandomSectorPos(() -> {
         BlockPos offset = RandomPos.generateRandomDirection(mob.getRandom(), horizontalRange, verticalRange);
         BlockPos candidate = generateRandomPosTowardDirection(mob, horizontalRange, restricted, offset);
         return candidate == null ? null : movePosUpOutOfSolid(mob, candidate);
      }, score);
   }

   @Nullable
   public static Vec3 getPosTowards(PathfinderMob mob, int horizontalRange, int verticalRange, Vec3 target) {
      SectorVec3 exact = getSectorPosTowards(mob, horizontalRange, verticalRange,
            SectorVec3.fromApproximate(target.x, target.y, target.z));
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPosTowards(PathfinderMob mob, int horizontalRange, int verticalRange,
                                                SectorVec3 target) {
      return getSectorPosInDirection(mob, horizontalRange, verticalRange,
            target.relativeTo(mob.sectorPosition()), GoalUtils.mobRestricted(mob, horizontalRange));
   }

   @Nullable
   public static SectorVec3 getSectorPosTowards(PathfinderMob mob, int horizontalRange, int verticalRange,
                                                Entity target) {
      return getSectorPosTowards(mob, horizontalRange, verticalRange, target.sectorPosition());
   }

   @Nullable
   public static Vec3 getPosAway(PathfinderMob mob, int horizontalRange, int verticalRange, Vec3 avoid) {
      SectorVec3 exact = getSectorPosAway(mob, horizontalRange, verticalRange,
            SectorVec3.fromApproximate(avoid.x, avoid.y, avoid.z));
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 getSectorPosAway(PathfinderMob mob, int horizontalRange, int verticalRange,
                                             SectorVec3 avoid) {
      return getSectorPosInDirection(mob, horizontalRange, verticalRange,
            mob.sectorPosition().relativeTo(avoid), GoalUtils.mobRestricted(mob, horizontalRange));
   }

   @Nullable
   public static SectorVec3 getSectorPosAway(PathfinderMob mob, int horizontalRange, int verticalRange,
                                             Entity avoid) {
      return getSectorPosAway(mob, horizontalRange, verticalRange, avoid.sectorPosition());
   }

   @Nullable
   private static SectorVec3 getSectorPosInDirection(PathfinderMob mob, int horizontalRange,
                                                      int verticalRange, Vec3 direction, boolean restricted) {
      return RandomPos.generateRandomSectorPos(mob, () -> {
         BlockPos offset = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), horizontalRange,
               verticalRange, 0, direction.x, direction.z, (double)((float)Math.PI / 2F));
         if (offset == null) return null;
         BlockPos candidate = generateRandomPosTowardDirection(mob, horizontalRange, restricted, offset);
         return candidate == null ? null : movePosUpOutOfSolid(mob, candidate);
      });
   }

   @Nullable
   public static BlockPos movePosUpOutOfSolid(PathfinderMob p_148519_, BlockPos p_148520_) {
      p_148520_ = RandomPos.moveUpOutOfSolid(p_148520_, p_148519_.level.getMaxBuildHeight(), (p_148534_) -> {
         return GoalUtils.isSolid(p_148519_, p_148534_);
      });
      return !GoalUtils.isWater(p_148519_, p_148520_) && !GoalUtils.hasMalus(p_148519_, p_148520_) ? p_148520_ : null;
   }

   @Nullable
   public static BlockPos generateRandomPosTowardDirection(PathfinderMob p_148514_, int p_148515_, boolean p_148516_, BlockPos p_148517_) {
      BlockPos blockpos = RandomPos.generateRandomPosTowardDirection(p_148514_, p_148515_, p_148514_.getRandom(), p_148517_);
      return !GoalUtils.isOutsideLimits(blockpos, p_148514_) && !GoalUtils.isRestricted(p_148516_, p_148514_, blockpos) && !GoalUtils.isNotStable(p_148514_.getNavigation(), blockpos) ? blockpos : null;
   }
}