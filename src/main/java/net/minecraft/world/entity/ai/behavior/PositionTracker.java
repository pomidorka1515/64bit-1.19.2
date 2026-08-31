package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public interface PositionTracker {
   SectorVec3 currentExactPosition();

   default Vec3 currentPosition() {
      return this.currentExactPosition().toApproximateVec3();
   }

   BlockPos currentBlockPosition();

   boolean isVisibleBy(LivingEntity p_23739_);
}