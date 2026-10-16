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
        return RandomPos.generateRandomSectorPos(mob, () -> generateRandomPos(mob, horizontalRange, verticalRange, startHeight, directionX, directionZ, maxAngle, restricted));
    }

    @Nullable
    public static BlockPos generateRandomPos(PathfinderMob pMob, int pMaxDistance, int pYRange, int pY, double pX, double pZ, double pAmplifier, boolean pShortCircuit) {
        BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(pMob.getRandom(), pMaxDistance, pYRange, pY, pX, pZ, pAmplifier);
        if (blockpos == null) {
            return null;
        } else {
            BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(pMob, pMaxDistance, pMob.getRandom(), blockpos);
            if (!GoalUtils.isOutsideLimits(blockpos1, pMob) && !GoalUtils.isRestricted(pShortCircuit, pMob, blockpos1)) {
                blockpos1 = RandomPos.moveUpOutOfSolid(blockpos1, pMob.level.getMaxBuildHeight(), (p_148376_) -> {
                    return GoalUtils.isSolid(pMob, p_148376_);
                });
                return GoalUtils.hasMalus(pMob, blockpos1) ? null : blockpos1;
            } else {
                return null;
            }
        }
    }
}
