package net.minecraft.world.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.SectorVec3;

public class MoveBackToVillageGoal extends RandomStrollGoal {
   private static final int MAX_XZ_DIST = 10;
   private static final int MAX_Y_DIST = 7;

   public MoveBackToVillageGoal(PathfinderMob p_25568_, double p_25569_, boolean p_25570_) {
      super(p_25568_, p_25569_, 10, p_25570_);
   }

   public boolean canUse() {
      ServerLevel serverlevel = (ServerLevel)this.mob.level;
      BlockPos blockpos = this.mob.blockPosition();
      return serverlevel.isVillage(blockpos) ? false : super.canUse();
   }

   @Nullable
   protected SectorVec3 getPosition() {
      ServerLevel serverlevel = (ServerLevel)this.mob.level;
      BlockPos blockpos = this.mob.blockPosition();
      SectionPos sectionpos = SectionPos.of(blockpos);
      SectionPos closest = BehaviorUtils.findSectionClosestToVillage(serverlevel, sectionpos, 2);
      BlockPos center = closest.center();
      return closest != sectionpos ? DefaultRandomPos.getSectorPosTowards(this.mob, 10, 7,
            SectorVec3.fromBlockAndFraction(center.getX(), 0.5D, center.getY(), center.getZ(), 0.5D),
            (double)((float)Math.PI / 2F)) : null;
   }
}