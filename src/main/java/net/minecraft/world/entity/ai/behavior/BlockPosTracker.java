package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.SectorVec3;

public class BlockPosTracker implements PositionTracker {
   private final BlockPos blockPos;
   private final SectorVec3 centerPosition;

   public BlockPosTracker(BlockPos p_22676_) {
      this(SectorVec3.fromBlockAndFraction(p_22676_.getX(), 0.5D,
            (double)p_22676_.getY() + 0.5D, p_22676_.getZ(), 0.5D));
   }

   public BlockPosTracker(SectorVec3 position) {
      this.blockPos = position.blockPosition();
      this.centerPosition = position;
   }

   public SectorVec3 currentExactPosition() {
      return this.centerPosition;
   }

   public BlockPos currentBlockPosition() {
      return this.blockPos;
   }

   public boolean isVisibleBy(LivingEntity p_22679_) {
      return true;
   }

   public String toString() {
      return "BlockPosTracker{blockPos=" + this.blockPos + ", centerPosition=" + this.centerPosition + "}";
   }
}