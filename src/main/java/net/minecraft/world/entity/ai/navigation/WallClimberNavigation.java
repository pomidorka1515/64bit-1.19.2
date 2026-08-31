package net.minecraft.world.entity.ai.navigation;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

public class WallClimberNavigation extends GroundPathNavigation {
   @Nullable
   private BlockPos pathToPosition;

   public WallClimberNavigation(Mob p_26580_, Level p_26581_) {
      super(p_26580_, p_26581_);
   }

   public Path createPath(BlockPos p_26589_, int p_26590_) {
      this.pathToPosition = p_26589_;
      return super.createPath(p_26589_, p_26590_);
   }

   public Path createPath(Entity p_26586_, int p_26587_) {
      this.pathToPosition = p_26586_.blockPosition();
      return super.createPath(p_26586_, p_26587_);
   }

   public boolean moveTo(Entity p_26583_, double p_26584_) {
      Path path = this.createPath(p_26583_, 0);
      if (path != null) {
         return this.moveTo(path, p_26584_);
      } else {
         this.pathToPosition = p_26583_.blockPosition();
         this.speedModifier = p_26584_;
         return true;
      }
   }

   public void tick() {
      if (!this.isDone()) {
         super.tick();
      } else {
         if (this.pathToPosition != null) {
            net.minecraft.world.phys.SectorVec3 target = net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(
                  this.pathToPosition.getX(), 0.5D, (double)this.pathToPosition.getY(),
                  this.pathToPosition.getZ(), 0.5D);
            net.minecraft.world.phys.Vec3 delta = target.relativeTo(this.mob.sectorPosition());
            double width = (double)this.mob.getBbWidth();
            boolean close = delta.lengthSqr() < width * width;
            boolean closeHorizontallyAbove = this.mob.getY() > (double)this.pathToPosition.getY()
                  && delta.x * delta.x + delta.z * delta.z < width * width;
            if (!close && !closeHorizontallyAbove) {
               this.mob.getMoveControl().setWantedPosition(target, this.speedModifier);
            } else {
               this.pathToPosition = null;
            }
         }

      }
   }
}