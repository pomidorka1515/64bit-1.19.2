package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class AirRandomPos {
   @Nullable
   public static Vec3 getPosTowards(PathfinderMob p_148388_, int p_148389_, int p_148390_, int p_148391_, Vec3 p_148392_, double p_148393_) {
      net.minecraft.world.phys.SectorVec3 result = getSectorPosTowards(p_148388_, p_148389_, p_148390_, p_148391_,
            net.minecraft.world.phys.SectorVec3.fromApproximate(p_148392_.x, p_148392_.y, p_148392_.z), p_148393_);
      return result == null ? null : result.toApproximateVec3();
   }

   @Nullable
   public static net.minecraft.world.phys.SectorVec3 getSectorPosTowards(PathfinderMob p_148388_, int p_148389_, int p_148390_, int p_148391_, net.minecraft.world.phys.SectorVec3 target, double p_148393_) {
      Vec3 vec3 = target.relativeTo(p_148388_.sectorPosition());
      boolean flag = GoalUtils.mobRestricted(p_148388_, p_148389_);
      return RandomPos.generateRandomSectorPos(p_148388_, () -> {
         BlockPos blockpos = AirAndWaterRandomPos.generateRandomPos(p_148388_, p_148389_, p_148390_, p_148391_, vec3.x, vec3.z, p_148393_, flag);
         return blockpos != null && !GoalUtils.isWater(p_148388_, blockpos) ? blockpos : null;
      });
   }
}
