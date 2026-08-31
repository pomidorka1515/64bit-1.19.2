package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class GoalUtils {
   public static boolean hasGroundPathNavigation(Mob p_26895_) {
      return p_26895_.getNavigation() instanceof GroundPathNavigation;
   }

   public static boolean mobRestricted(PathfinderMob mob, int horizontalRange) {
      if (!mob.hasRestriction()) return false;
      BlockPos center = mob.getRestrictCenter();
      double maxDistance = (double)(mob.getRestrictRadius() + (float)horizontalRange) + 1.0D;
      double dx = WorldBounds.signedDifference(mob.getBlockX(), center.getX()) + mob.sectorPosition().subX() - 0.5D;
      double dy = mob.getY() - ((double)center.getY() + 0.5D);
      double dz = WorldBounds.signedDifference(mob.getBlockZ(), center.getZ()) + mob.sectorPosition().subZ() - 0.5D;
      return dx * dx + dy * dy + dz * dz < maxDistance * maxDistance;
   }

   public static boolean isOutsideLimits(BlockPos p_148452_, PathfinderMob p_148453_) {
      return p_148452_.getY() < p_148453_.level.getMinBuildHeight() || p_148452_.getY() > p_148453_.level.getMaxBuildHeight();
   }

   public static boolean isRestricted(boolean p_148455_, PathfinderMob p_148456_, BlockPos p_148457_) {
      return p_148455_ && !p_148456_.isWithinRestriction(p_148457_);
   }

   public static boolean isNotStable(PathNavigation p_148449_, BlockPos p_148450_) {
      return !p_148449_.isStableDestination(p_148450_);
   }

   public static boolean isWater(PathfinderMob p_148446_, BlockPos p_148447_) {
      return p_148446_.level.getFluidState(p_148447_).is(FluidTags.WATER);
   }

   public static boolean hasMalus(PathfinderMob p_148459_, BlockPos p_148460_) {
      return p_148459_.getPathfindingMalus(WalkNodeEvaluator.getBlockPathTypeStatic(p_148459_.level, p_148460_.mutable())) != 0.0F;
   }

   public static boolean isSolid(PathfinderMob p_148462_, BlockPos p_148463_) {
      return p_148462_.level.getBlockState(p_148463_).getMaterial().isSolid();
   }
}