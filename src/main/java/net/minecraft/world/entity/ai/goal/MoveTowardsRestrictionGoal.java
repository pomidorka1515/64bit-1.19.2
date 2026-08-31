package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.SectorVec3;

public class MoveTowardsRestrictionGoal extends Goal {
   private final PathfinderMob mob;
   @Nullable
   private SectorVec3 wantedPosition;
   private final double speedModifier;

   public MoveTowardsRestrictionGoal(PathfinderMob p_25633_, double p_25634_) {
      this.mob = p_25633_;
      this.speedModifier = p_25634_;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   public boolean canUse() {
      if (this.mob.isWithinRestriction()) {
         return false;
      } else {
         BlockPos center = this.mob.getRestrictCenter();
         this.wantedPosition = DefaultRandomPos.getSectorPosTowards(this.mob, 16, 7,
               SectorVec3.fromBlockAndFraction(center.getX(), 0.5D, center.getY(), center.getZ(), 0.5D),
               (double)((float)Math.PI / 2F));
         return this.wantedPosition != null;
      }
   }

   public boolean canContinueToUse() {
      return !this.mob.getNavigation().isDone();
   }

   public void start() {
      if (this.wantedPosition != null) {
         this.mob.getNavigation().moveTo(this.wantedPosition, this.speedModifier);
      }
   }
}