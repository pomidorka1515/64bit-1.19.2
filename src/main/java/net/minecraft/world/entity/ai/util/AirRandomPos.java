package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class AirRandomPos {
    @Nullable
    public static Vec3 getPosTowards(PathfinderMob pMob, int pRadius, int pYRange, int pY, Vec3 pVectorPosition, double pAmplifier) {
        net.minecraft.world.phys.SectorVec3 result = getSectorPosTowards(pMob, pRadius, pYRange, pY,
                net.minecraft.world.phys.SectorVec3.fromApproximate(pVectorPosition.x, pVectorPosition.y, pVectorPosition.z), pAmplifier);
        return result == null ? null : result.toExactVec3();
    }

    @Nullable
    public static net.minecraft.world.phys.SectorVec3 getSectorPosTowards(PathfinderMob pMob, int pRadius, int pYRange, int pY, net.minecraft.world.phys.SectorVec3 target, double pAmplifier) {
        Vec3 vec3 = target.relativeTo(pMob.sectorPosition());
        boolean flag = GoalUtils.mobRestricted(pMob, pRadius);
        return RandomPos.generateRandomSectorPos(pMob, () -> {
            BlockPos blockpos = AirAndWaterRandomPos.generateRandomPos(pMob, pRadius, pYRange, pY, vec3.x, vec3.z, pAmplifier, flag);
            return blockpos != null && !GoalUtils.isWater(pMob, blockpos) ? blockpos : null;
        });
    }
}
