package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class AmphibiousPathNavigation extends PathNavigation {
   public AmphibiousPathNavigation(Mob p_217788_, Level p_217789_) {
      super(p_217788_, p_217789_);
   }

   protected PathFinder createPathFinder(int p_217792_) {
      this.nodeEvaluator = new AmphibiousNodeEvaluator(false);
      this.nodeEvaluator.setCanPassDoors(true);
      return new PathFinder(this.nodeEvaluator, p_217792_);
   }

   protected boolean canUpdatePath() {
      return true;
   }

   @Override
   protected net.minecraft.world.phys.SectorVec3 getTempMobSectorPos() {
      return this.mob.sectorPosition().add(0.0D, (double)this.mob.getBbHeight() * 0.5D, 0.0D);
   }

   protected double getGroundY(Vec3 p_217794_) {
      return p_217794_.y;
   }

   @Override
   protected double getGroundY(BlockPos blockPos, double targetY) {
      return targetY;
   }

   protected boolean canMoveDirectly(Vec3 p_217796_, Vec3 p_217797_) {
      return this.isInLiquid() ? isClearForMovementBetween(this.mob, p_217796_, p_217797_) : false;
   }

   @Override
   protected boolean canMoveDirectlyLocal(net.minecraft.world.phys.SectorVec3 from,
                                          net.minecraft.world.phys.SectorVec3 to) {
      if (!this.isInLiquid()) return false;
      net.minecraft.world.phys.SectorVec3 target = to.add(0.0D,
            (double)this.mob.getBbHeight() * 0.5D, 0.0D);
      return net.minecraft.world.level.SectorClipper.clip(this.level, from, target, this.mob,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE).getType()
            == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   public boolean isStableDestination(BlockPos p_217799_) {
      return !this.level.getBlockState(p_217799_.below()).isAir();
   }

   public void setCanFloat(boolean p_217801_) {
   }
}