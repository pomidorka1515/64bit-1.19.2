package net.minecraft.world.phys;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
   private final Direction direction;
   private final BlockPos blockPos;
   private final boolean miss;
   private final boolean inside;
   /** Exact split-coordinate hit point used by sector-aware picking and the wire codec. */
   @Nullable
   private final SectorVec3 exactLocation;

   public static BlockHitResult miss(Vec3 p_82427_, Direction p_82428_, BlockPos p_82429_) {
      return new BlockHitResult(true, p_82427_, p_82428_, p_82429_, false, null);
   }

   public static BlockHitResult missExact(SectorVec3 p_82427_, Direction p_82428_, BlockPos p_82429_) {
      return new BlockHitResult(true, p_82427_.toApproximateVec3(), p_82428_, p_82429_, false, p_82427_);
   }

   public BlockHitResult(Vec3 p_82415_, Direction p_82416_, BlockPos p_82417_, boolean p_82418_) {
      this(false, p_82415_, p_82416_, p_82417_, p_82418_, null);
   }

   /** Creates a hit whose world X/Z are retained exactly. */
   public BlockHitResult(SectorVec3 p_82415_, Direction p_82416_, BlockPos p_82417_, boolean p_82418_) {
      this(false, p_82415_.toApproximateVec3(), p_82416_, p_82417_, p_82418_, p_82415_);
   }

   private BlockHitResult(boolean p_82420_, Vec3 p_82421_, Direction p_82422_, BlockPos p_82423_, boolean p_82424_, @Nullable SectorVec3 exactLocation) {
      super(p_82421_);
      this.miss = p_82420_;
      this.direction = p_82422_;
      this.blockPos = p_82423_;
      this.inside = p_82424_;
      this.exactLocation = exactLocation;
   }

   @Nullable
   public SectorVec3 getExactLocation() {
      return this.exactLocation;
   }

   public BlockHitResult withDirection(Direction p_82433_) {
      return new BlockHitResult(this.miss, this.location, p_82433_, this.blockPos, this.inside, this.exactLocation);
   }

   public BlockHitResult withPosition(BlockPos p_82431_) {
      return new BlockHitResult(this.miss, this.location, this.direction, p_82431_, this.inside, this.exactLocation);
   }

   public BlockPos getBlockPos() {
      return this.blockPos;
   }

   public Direction getDirection() {
      return this.direction;
   }

   public HitResult.Type getType() {
      return this.miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
   }

   public boolean isInside() {
      return this.inside;
   }
}