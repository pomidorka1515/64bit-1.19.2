package net.minecraft.world.phys.shapes;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class SubShape extends DiscreteVoxelShape {
   private final DiscreteVoxelShape parent;
   private final long startX;
   private final int startY;
   private final long startZ;
   private final long endX;
   private final int endY;
   private final long endZ;

   protected SubShape(DiscreteVoxelShape p_83190_, long p_83191_, int p_83192_, long p_83193_, long p_83194_, int p_83195_, long p_83196_) {
      super((int) (p_83194_ - p_83191_), p_83195_ - p_83192_, (int) (p_83196_ - p_83193_));
      this.parent = p_83190_;
      this.startX = p_83191_;
      this.startY = p_83192_;
      this.startZ = p_83193_;
      this.endX = p_83194_;
      this.endY = p_83195_;
      this.endZ = p_83196_;
   }

   public boolean isFull(long p_83206_, int p_83207_, long p_83208_) {
      return this.parent.isFull(this.startX + p_83206_, this.startY + p_83207_, this.startZ + p_83208_);
   }

   public void fill(long p_166060_, int p_166061_, long p_166062_) {
      this.parent.fill(this.startX + p_166060_, this.startY + p_166061_, this.startZ + p_166062_);
   }

   public int firstFull(Direction.Axis p_83204_) {
      return this.clampToShape(p_83204_, this.parent.firstFull(p_83204_));
   }

   public int lastFull(Direction.Axis p_83210_) {
      return this.clampToShape(p_83210_, this.parent.lastFull(p_83210_));
   }

   private int clampToShape(Direction.Axis p_166057_, int p_166058_) {
	  long i = p_166057_.choose(this.startX, this.startY, this.startZ);
	  long j = p_166057_.choose(this.endX, this.endY, this.endZ);
      return (int) (Mth.clamp(p_166058_, i, j) - i);
   }
}