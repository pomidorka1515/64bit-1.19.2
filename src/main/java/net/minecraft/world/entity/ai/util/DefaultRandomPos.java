package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class DefaultRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int horizontalRange, int verticalRange) {
        SectorVec3 exact = getSectorPos(mob, horizontalRange, verticalRange);
        return exact == null ? null : exact.toExactVec3();
    }

    @Nullable
    public static SectorVec3 getSectorPos(PathfinderMob mob, int horizontalRange, int verticalRange) {
        boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
        return RandomPos.generateRandomSectorPos(mob, () -> {
            BlockPos offset = RandomPos.generateRandomDirection(mob.getRandom(), horizontalRange, verticalRange);
            return generateRandomPosTowardDirection(mob, horizontalRange, restricted, offset);
        });
    }

    @Nullable
    public static Vec3 getPosTowards(PathfinderMob mob, int horizontalRange, int verticalRange,
            Vec3 target, double maxAngle) {
        SectorVec3 exact = getSectorPosTowards(mob, horizontalRange, verticalRange,
                SectorVec3.fromApproximate(target), maxAngle);
        return exact == null ? null : exact.toExactVec3();
    }

    @Nullable
    public static SectorVec3 getSectorPosTowards(PathfinderMob mob, int horizontalRange, int verticalRange,
            SectorVec3 target, double maxAngle) {
        Vec3 direction = target.relativeTo(mob.sectorPosition());
        boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
        return RandomPos.generateRandomSectorPos(mob, () -> {
            BlockPos offset = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), horizontalRange,
                    verticalRange, 0, direction.x, direction.z, maxAngle);
            return offset == null ? null : generateRandomPosTowardDirection(mob, horizontalRange, restricted, offset);
        });
    }

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int horizontalRange, int verticalRange, Vec3 avoid) {
        SectorVec3 exact = getSectorPosAway(mob, horizontalRange, verticalRange,
                SectorVec3.fromApproximate(avoid));
        return exact == null ? null : exact.toExactVec3();
    }

    @Nullable
    public static SectorVec3 getSectorPosAway(PathfinderMob mob, int horizontalRange, int verticalRange,
            SectorVec3 avoid) {
        Vec3 direction = mob.sectorPosition().relativeTo(avoid);
        boolean restricted = GoalUtils.mobRestricted(mob, horizontalRange);
        return RandomPos.generateRandomSectorPos(mob, () -> {
            BlockPos offset = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), horizontalRange,
                    verticalRange, 0, direction.x, direction.z, (double) ((float) Math.PI / 2F));
            return offset == null ? null : generateRandomPosTowardDirection(mob, horizontalRange, restricted, offset);
        });
    }

    @Nullable
    public static SectorVec3 getSectorPosAway(PathfinderMob mob, int horizontalRange, int verticalRange,
            Entity avoid) {
        return getSectorPosAway(mob, horizontalRange, verticalRange, avoid.sectorPosition());
    }

    @Nullable
    private static BlockPos generateRandomPosTowardDirection(PathfinderMob pMob, int pRadius, boolean pShortCircuit, BlockPos pPos) {
        BlockPos blockpos = RandomPos.generateRandomPosTowardDirection(pMob, pRadius, pMob.getRandom(), pPos);
        return !GoalUtils.isOutsideLimits(blockpos, pMob) && !GoalUtils.isRestricted(pShortCircuit, pMob, blockpos) && !GoalUtils.isNotStable(pMob.getNavigation(), blockpos) && !GoalUtils.hasMalus(pMob, blockpos) ? blockpos : null;
    }
}
