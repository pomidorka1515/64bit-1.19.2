package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.SectorVec3;

public class MoveTowardsTargetGoal extends Goal {
   private final PathfinderMob mob;
   @Nullable
   private LivingEntity target;
   @Nullable
   private SectorVec3 wantedPosition;
   private final double speedModifier;
   private final float within;

   public MoveTowardsTargetGoal(PathfinderMob p_25646_, double p_25647_, float p_25648_) {
      this.mob = p_25646_;
      this.speedModifier = p_25647_;
      this.within = p_25648_;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   public boolean canUse() {
      this.target = this.mob.getTarget();
      if (this.target == null) {
         return false;
      } else if (this.target.distanceToSqr(this.mob) > (double)(this.within * this.within)) {
         return false;
      } else {
         this.wantedPosition = DefaultRandomPos.getSectorPosTowards(this.mob, 16, 7,
               this.target.sectorPosition(), (double)((float)Math.PI / 2F));
         return this.wantedPosition != null;
      }
   }

   public boolean canContinueToUse() {
      return !this.mob.getNavigation().isDone() && this.target.isAlive() && this.target.distanceToSqr(this.mob) < (double)(this.within * this.within);
   }

   public void stop() {
      this.target = null;
   }

   public void start() {
      if (this.wantedPosition != null) {
         this.mob.getNavigation().moveTo(this.wantedPosition, this.speedModifier);
      }
   }
}