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
    public static boolean hasGroundPathNavigation(Mob pMob) {
        return pMob.getNavigation() instanceof GroundPathNavigation;
    }

    public static boolean mobRestricted(PathfinderMob mob, int horizontalRange) {
        if (!mob.hasRestriction())
            return false;
        BlockPos center = mob.getRestrictCenter();
        double maxDistance = (double) (mob.getRestrictRadius() + (float) horizontalRange) + 1.0D;
        double dx = WorldBounds.signedDifference(mob.getBlockX(), center.getX()) + mob.sectorPosition().subX() - 0.5D;
        double dy = mob.getY() - ((double) center.getY() + 0.5D);
        double dz = WorldBounds.signedDifference(mob.getBlockZ(), center.getZ()) + mob.sectorPosition().subZ() - 0.5D;
        return dx * dx + dy * dy + dz * dz < maxDistance * maxDistance;
    }

    public static boolean isOutsideLimits(BlockPos pPos, PathfinderMob pMob) {
        return pPos.getY() < pMob.level.getMinBuildHeight() || pPos.getY() > pMob.level.getMaxBuildHeight();
    }

    public static boolean isRestricted(boolean pShortCircuit, PathfinderMob pMob, BlockPos pPos) {
        return pShortCircuit && !pMob.isWithinRestriction(pPos);
    }

    public static boolean isNotStable(PathNavigation pNavigation, BlockPos pPos) {
        return !pNavigation.isStableDestination(pPos);
    }

    public static boolean isWater(PathfinderMob pMob, BlockPos pPos) {
        return pMob.level.getFluidState(pPos).is(FluidTags.WATER);
    }

    public static boolean hasMalus(PathfinderMob pMob, BlockPos pPos) {
        return pMob.getPathfindingMalus(WalkNodeEvaluator.getBlockPathTypeStatic(pMob.level, pPos.mutable())) != 0.0F;
    }

    public static boolean isSolid(PathfinderMob pMob, BlockPos pPos) {
        return pMob.level.getBlockState(pPos).getMaterial().isSolid();
    }
}
