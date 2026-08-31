package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;

public class FleeSunGoal extends Goal {
   protected final PathfinderMob mob;
   @Nullable
   private SectorVec3 wantedPosition;
   private final double speedModifier;
   private final Level level;

   public FleeSunGoal(PathfinderMob p_25221_, double p_25222_) {
      this.mob = p_25221_;
      this.speedModifier = p_25222_;
      this.level = p_25221_.level;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   public boolean canUse() {
      if (this.mob.getTarget() != null) {
         return false;
      } else if (!this.level.isDay()) {
         return false;
      } else if (!this.mob.isOnFire()) {
         return false;
      } else if (!this.level.canSeeSky(this.mob.blockPosition())) {
         return false;
      } else {
         return !this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? false : this.setWantedPos();
      }
   }

   protected boolean setWantedPos() {
      this.wantedPosition = this.getHidePos();
      return this.wantedPosition != null;
   }

   public boolean canContinueToUse() {
      return !this.mob.getNavigation().isDone();
   }

   public void start() {
      if (this.wantedPosition != null) {
         this.mob.getNavigation().moveTo(this.wantedPosition, this.speedModifier);
      }
   }

   @Nullable
   protected SectorVec3 getHidePos() {
      RandomSource randomsource = this.mob.getRandom();
      BlockPos blockpos = this.mob.blockPosition();

      for(int i = 0; i < 10; ++i) {
         BlockPos blockpos1 = blockpos.offset(randomsource.nextInt(20) - 10, randomsource.nextInt(6) - 3, randomsource.nextInt(20) - 10);
         if (!this.level.canSeeSky(blockpos1) && this.mob.getWalkTargetValue(blockpos1) < 0.0F) {
            return SectorVec3.fromBlockAndFraction(blockpos1.getX(), 0.5D, blockpos1.getY(),
                  blockpos1.getZ(), 0.5D);
         }
      }

      return null;
   }
}