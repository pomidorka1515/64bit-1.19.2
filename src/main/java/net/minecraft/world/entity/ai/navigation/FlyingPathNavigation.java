package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

public class FlyingPathNavigation extends PathNavigation {
   public FlyingPathNavigation(Mob p_26424_, Level p_26425_) {
      super(p_26424_, p_26425_);
   }

   protected PathFinder createPathFinder(int p_26428_) {
      this.nodeEvaluator = new FlyNodeEvaluator();
      this.nodeEvaluator.setCanPassDoors(true);
      return new PathFinder(this.nodeEvaluator, p_26428_);
   }

   protected boolean canUpdatePath() {
      return this.canFloat() && this.isInLiquid() || !this.mob.isPassenger();
   }

   @Override
   protected net.minecraft.world.phys.SectorVec3 getTempMobSectorPos() {
      return this.mob.sectorPosition();
   }

   public Path createPath(Entity p_26430_, int p_26431_) {
      return this.createPath(p_26430_.blockPosition(), p_26431_);
   }

   public void tick() {
      ++this.tick;
      if (this.hasDelayedRecomputation) {
         this.recomputePath();
      }

      if (!this.isDone()) {
         if (this.canUpdatePath()) {
            this.followThePath();
         } else if (this.path != null && !this.path.isDone()) {
            net.minecraft.world.phys.SectorVec3 next = this.path.getNextExactEntityPos(this.mob);
            if (this.mob.getBlockX() == next.blockX() && this.mob.getBlockY() == Mth.floor(next.y())
                  && this.mob.getBlockZ() == next.blockZ()) {
               this.path.advance();
            }
         }

         DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
         if (!this.isDone()) {
            this.mob.getMoveControl().setWantedPosition(this.path.getNextExactEntityPos(this.mob), this.speedModifier);
         }
      }
   }

   public void setCanOpenDoors(boolean p_26441_) {
      this.nodeEvaluator.setCanOpenDoors(p_26441_);
   }

   public boolean canPassDoors() {
      return this.nodeEvaluator.canPassDoors();
   }

   public void setCanPassDoors(boolean p_26444_) {
      this.nodeEvaluator.setCanPassDoors(p_26444_);
   }

   public boolean canOpenDoors() {
      return this.nodeEvaluator.canPassDoors();
   }

   public boolean isStableDestination(BlockPos p_26439_) {
      return this.level.getBlockState(p_26439_).entityCanStandOn(this.level, p_26439_, this.mob);
   }
}