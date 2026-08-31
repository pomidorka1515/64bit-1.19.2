package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.SectorVec3;

public abstract class ContainerOpenersCounter {
   private static final int CHECK_TICK_DELAY = 5;
   private int openCount;

   protected abstract void onOpen(Level p_155460_, BlockPos p_155461_, BlockState p_155462_);

   protected abstract void onClose(Level p_155473_, BlockPos p_155474_, BlockState p_155475_);

   protected abstract void openerCountChanged(Level p_155463_, BlockPos p_155464_, BlockState p_155465_, int p_155466_, int p_155467_);

   protected abstract boolean isOwnContainer(Player p_155451_);

   public void incrementOpeners(Player p_155453_, Level p_155454_, BlockPos p_155455_, BlockState p_155456_) {
      int i = this.openCount++;
      if (i == 0) {
         this.onOpen(p_155454_, p_155455_, p_155456_);
         p_155454_.gameEvent(p_155453_, GameEvent.CONTAINER_OPEN, p_155455_);
         scheduleRecheck(p_155454_, p_155455_, p_155456_);
      }

      this.openerCountChanged(p_155454_, p_155455_, p_155456_, i, this.openCount);
   }

   public void decrementOpeners(Player p_155469_, Level p_155470_, BlockPos p_155471_, BlockState p_155472_) {
      int i = this.openCount--;
      if (this.openCount == 0) {
         this.onClose(p_155470_, p_155471_, p_155472_);
         p_155470_.gameEvent(p_155469_, GameEvent.CONTAINER_CLOSE, p_155471_);
      }

      this.openerCountChanged(p_155470_, p_155471_, p_155472_, i, this.openCount);
   }

   private int getOpenCount(Level p_155458_, BlockPos p_155459_) {
      int i = 0;

      for(Player player : p_155458_.players()) {
         // The former float-backed AABB query drifted off the chest beyond
         // 2^24, so scheduled rechecks erased legitimate openers.  Filtering
         // the level's player list in split-coordinate space keeps the exact
         // vanilla search volume: [block - 5, block + 6] on every axis.
         if (this.isOwnContainer(player) && isWithinRecheckRange(player, p_155459_)) {
            ++i;
         }
      }

      return i;
   }

   static boolean isWithinRecheckRange(Player p_155460_, BlockPos p_155461_) {
      SectorVec3 position = p_155460_.exactPosition();
      return position != null ? isWithinRecheckRange(position, p_155461_)
            : p_155460_.getX() >= (double)p_155461_.getX() - 5.0D && p_155460_.getX() <= (double)p_155461_.getX() + 6.0D
                  && p_155460_.getY() >= (double)p_155461_.getY() - 5.0D && p_155460_.getY() <= (double)p_155461_.getY() + 6.0D
                  && p_155460_.getZ() >= (double)p_155461_.getZ() - 5.0D && p_155460_.getZ() <= (double)p_155461_.getZ() + 6.0D;
   }

   static boolean isWithinRecheckRange(SectorVec3 p_155460_, BlockPos p_155461_) {
      SectorVec3 minimum = SectorVec3.fromBlockAndFraction(p_155461_.getX(), -5.0D,
            (double)p_155461_.getY() - 5.0D, p_155461_.getZ(), -5.0D);
      SectorVec3 maximum = SectorVec3.fromBlockAndFraction(p_155461_.getX(), 6.0D,
            (double)p_155461_.getY() + 6.0D, p_155461_.getZ(), 6.0D);
      return p_155460_.relativeX(minimum) >= 0.0D && p_155460_.relativeX(maximum) <= 0.0D
            && p_155460_.relativeY(minimum) >= 0.0D && p_155460_.relativeY(maximum) <= 0.0D
            && p_155460_.relativeZ(minimum) >= 0.0D && p_155460_.relativeZ(maximum) <= 0.0D;
   }

   public void recheckOpeners(Level p_155477_, BlockPos p_155478_, BlockState p_155479_) {
      int i = this.getOpenCount(p_155477_, p_155478_);
      int j = this.openCount;
      if (j != i) {
         boolean flag = i != 0;
         boolean flag1 = j != 0;
         if (flag && !flag1) {
            this.onOpen(p_155477_, p_155478_, p_155479_);
            p_155477_.gameEvent((Entity)null, GameEvent.CONTAINER_OPEN, p_155478_);
         } else if (!flag) {
            this.onClose(p_155477_, p_155478_, p_155479_);
            p_155477_.gameEvent((Entity)null, GameEvent.CONTAINER_CLOSE, p_155478_);
         }

         this.openCount = i;
      }

      this.openerCountChanged(p_155477_, p_155478_, p_155479_, j, i);
      if (i > 0) {
         scheduleRecheck(p_155477_, p_155478_, p_155479_);
      }

   }

   public int getOpenerCount() {
      return this.openCount;
   }

   private static void scheduleRecheck(Level p_155481_, BlockPos p_155482_, BlockState p_155483_) {
      p_155481_.scheduleTick(p_155482_, p_155483_.getBlock(), 5);
   }
}