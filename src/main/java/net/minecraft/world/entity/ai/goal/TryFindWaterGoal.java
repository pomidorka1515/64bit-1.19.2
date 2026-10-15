package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;

public class TryFindWaterGoal extends Goal {
   private final PathfinderMob mob;

   public TryFindWaterGoal(PathfinderMob p_25964_) {
      this.mob = p_25964_;
   }

   public boolean canUse() {
      return this.mob.isOnGround() && !this.mob.level.getFluidState(this.mob.blockPosition()).is(FluidTags.WATER);
   }

   public void start() {
      BlockPos blockpos = null;

      for(BlockPos blockpos1 : BlockPos.betweenClosed(this.mob.sectorPosition().add(-2.0D, -2.0D, -2.0D).blockPosition(), this.mob.sectorPosition().add(2.0D, 0.0D, 2.0D).blockPosition())) {
         if (this.mob.level.getFluidState(blockpos1).is(FluidTags.WATER)) {
            blockpos = blockpos1;
            break;
         }
      }

      if (blockpos != null) {
         this.mob.getMoveControl().setWantedPosition(net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.0D, (double)blockpos.getY(), blockpos.getZ(), 0.0D), 1.0D);
      }

   }
}
