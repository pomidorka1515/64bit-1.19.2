package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class WaterBoundPathNavigation extends PathNavigation {
   private boolean allowBreaching;

   public WaterBoundPathNavigation(Mob p_26594_, Level p_26595_) {
      super(p_26594_, p_26595_);
   }

   protected PathFinder createPathFinder(int p_26598_) {
      this.allowBreaching = this.mob.getType() == EntityType.DOLPHIN;
      this.nodeEvaluator = new SwimNodeEvaluator(this.allowBreaching);
      return new PathFinder(this.nodeEvaluator, p_26598_);
   }

   protected boolean canUpdatePath() {
      return this.allowBreaching || this.isInLiquid();
   }

   protected double getGroundY(Vec3 p_186136_) {
      return p_186136_.y;
   }

   @Override
   protected double getGroundY(BlockPos blockPos, double targetY) {
      return targetY;
   }

   protected boolean canMoveDirectly(Vec3 p_186138_, Vec3 p_186139_) {
      return isClearForMovementBetween(this.mob, p_186138_, p_186139_);
   }

   @Override
   protected boolean canMoveDirectlyLocal(net.minecraft.world.phys.SectorVec3 from,
                                          net.minecraft.world.phys.SectorVec3 to) {
      net.minecraft.world.phys.SectorVec3 target = to.add(0.0D,
            (double)this.mob.getBbHeight() * 0.5D, 0.0D);
      return net.minecraft.world.level.SectorClipper.clip(this.level, from, target, this.mob,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE).getType()
            == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   public boolean isStableDestination(BlockPos p_26608_) {
      return !this.level.getBlockState(p_26608_).isSolidRender(this.level, p_26608_);
   }

   public void setCanFloat(boolean p_26612_) {
   }
}