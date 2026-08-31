package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class StrollThroughVillageGoal extends Goal {
   private static final int DISTANCE_THRESHOLD = 10;
   private final PathfinderMob mob;
   private final int interval;
   @Nullable
   private BlockPos wantedPos;

   public StrollThroughVillageGoal(PathfinderMob p_25907_, int p_25908_) {
      this.mob = p_25907_;
      this.interval = reducedTickDelay(p_25908_);
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   public boolean canUse() {
      if (this.mob.isVehicle()) {
         return false;
      } else if (this.mob.level.isDay()) {
         return false;
      } else if (this.mob.getRandom().nextInt(this.interval) != 0) {
         return false;
      } else {
         ServerLevel serverlevel = (ServerLevel)this.mob.level;
         BlockPos blockpos = this.mob.blockPosition();
         if (!serverlevel.isCloseToVillage(blockpos, 6)) {
            return false;
         } else {
            SectorVec3 target = LandRandomPos.getSectorPos(this.mob, 15, 7, (p_25912_) -> {
               return (double)(-serverlevel.sectionsToVillage(SectionPos.of(p_25912_)));
            });
            this.wantedPos = target == null ? null : target.blockPosition();
            return this.wantedPos != null;
         }
      }
   }

   public boolean canContinueToUse() {
      return this.wantedPos != null && !this.mob.getNavigation().isDone() && this.mob.getNavigation().getTargetPos().equals(this.wantedPos);
   }

   public void tick() {
      if (this.wantedPos != null) {
         PathNavigation pathnavigation = this.mob.getNavigation();
         SectorVec3 wantedCenter = SectorVec3.fromBlockAndFraction(this.wantedPos.getX(), 0.5D,
               this.wantedPos.getY(), this.wantedPos.getZ(), 0.5D);
         Vec3 delta = wantedCenter.relativeTo(this.mob.sectorPosition());
         if (pathnavigation.isDone() && delta.lengthSqr() >= 100.0D) {
            SectorVec3 intermediate = this.mob.sectorPosition().add(
                  delta.x * 0.6D, delta.y * 0.6D, delta.z * 0.6D);
            Vec3 remaining = wantedCenter.relativeTo(intermediate).normalize().scale(10.0D);
            BlockPos blockpos = intermediate.add(remaining.x, remaining.y, remaining.z).blockPosition();
            blockpos = this.mob.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos);
            if (!pathnavigation.moveTo(blockpos, 1.0D)) {
               this.moveRandomly();
            }
         }

      }
   }

   private void moveRandomly() {
      RandomSource randomsource = this.mob.getRandom();
      BlockPos blockpos = this.mob.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.mob.blockPosition().offset(-8 + randomsource.nextInt(16), 0, -8 + randomsource.nextInt(16)));
      this.mob.getNavigation().moveTo(blockpos, 1.0D);
   }
}