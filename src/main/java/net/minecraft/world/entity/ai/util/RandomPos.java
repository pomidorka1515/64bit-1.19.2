package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class RandomPos {
   private static final int RANDOM_POS_ATTEMPTS = 10;

   public static BlockPos generateRandomDirection(RandomSource p_217852_, int p_217853_, int p_217854_) {
      int i = p_217852_.nextInt(2 * p_217853_ + 1) - p_217853_;
      int j = p_217852_.nextInt(2 * p_217854_ + 1) - p_217854_;
      int k = p_217852_.nextInt(2 * p_217853_ + 1) - p_217853_;
      return new BlockPos(i, j, k);
   }

   @Nullable
   public static BlockPos generateRandomDirectionWithinRadians(RandomSource p_217856_, int p_217857_, int p_217858_, int p_217859_, double p_217860_, double p_217861_, double p_217862_) {
      double d0 = Mth.atan2(p_217861_, p_217860_) - (double)((float)Math.PI / 2F);
      double d1 = d0 + (double)(2.0F * p_217856_.nextFloat() - 1.0F) * p_217862_;
      double d2 = Math.sqrt(p_217856_.nextDouble()) * (double)Mth.SQRT_OF_TWO * (double)p_217857_;
      double d3 = -d2 * Math.sin(d1);
      double d4 = d2 * Math.cos(d1);
      if (!(Math.abs(d3) > (double)p_217857_) && !(Math.abs(d4) > (double)p_217857_)) {
         int i = p_217856_.nextInt(2 * p_217858_ + 1) - p_217858_ + p_217859_;
         return new BlockPos(d3, (double)i, d4);
      } else {
         return null;
      }
   }

   @VisibleForTesting
   public static BlockPos moveUpOutOfSolid(BlockPos p_148546_, int p_148547_, Predicate<BlockPos> p_148548_) {
      if (!p_148548_.test(p_148546_)) {
         return p_148546_;
      } else {
         BlockPos blockpos;
         for(blockpos = p_148546_.above(); blockpos.getY() < p_148547_ && p_148548_.test(blockpos); blockpos = blockpos.above()) {
         }

         return blockpos;
      }
   }

   @VisibleForTesting
   public static BlockPos moveUpToAboveSolid(BlockPos p_26948_, int p_26949_, int p_26950_, Predicate<BlockPos> p_26951_) {
      if (p_26949_ < 0) {
         throw new IllegalArgumentException("aboveSolidAmount was " + p_26949_ + ", expected >= 0");
      } else if (!p_26951_.test(p_26948_)) {
         return p_26948_;
      } else {
         BlockPos blockpos;
         for(blockpos = p_26948_.above(); blockpos.getY() < p_26950_ && p_26951_.test(blockpos); blockpos = blockpos.above()) {
         }

         BlockPos blockpos1;
         BlockPos blockpos2;
         for(blockpos1 = blockpos; blockpos1.getY() < p_26950_ && blockpos1.getY() - blockpos.getY() < p_26949_; blockpos1 = blockpos2) {
            blockpos2 = blockpos1.above();
            if (p_26951_.test(blockpos2)) {
               break;
            }
         }

         return blockpos1;
      }
   }

   @Nullable
   public static Vec3 generateRandomPos(PathfinderMob mob, Supplier<BlockPos> supplier) {
      SectorVec3 exact = generateRandomSectorPos(mob, supplier);
      return exact == null ? null : exact.toApproximateVec3();
   }

   @Nullable
   public static SectorVec3 generateRandomSectorPos(PathfinderMob mob, Supplier<BlockPos> supplier) {
      return generateRandomSectorPos(supplier, mob::getWalkTargetValue);
   }

   @Nullable
   public static Vec3 generateRandomPos(Supplier<BlockPos> supplier, ToDoubleFunction<BlockPos> score) {
      SectorVec3 exact = generateRandomSectorPos(supplier, score);
      return exact == null ? null : exact.toApproximateVec3();
   }

   /** Selects a random block target without converting its long X/Z to doubles. */
   @Nullable
   public static SectorVec3 generateRandomSectorPos(Supplier<BlockPos> supplier,
                                                     ToDoubleFunction<BlockPos> score) {
      double bestScore = Double.NEGATIVE_INFINITY;
      BlockPos best = null;

      for (int i = 0; i < RANDOM_POS_ATTEMPTS; ++i) {
         BlockPos candidate = supplier.get();
         if (candidate != null) {
            double value = score.applyAsDouble(candidate);
            if (value > bestScore) {
               bestScore = value;
               best = candidate;
            }
         }
      }

      return best == null ? null : SectorVec3.fromBlockAndFraction(best.getX(), 0.5D,
            (double)best.getY(), best.getZ(), 0.5D);
   }

   public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int horizontalRange,
                                                            RandomSource random, BlockPos offset) {
      long offsetX = offset.getX();
      long offsetZ = offset.getZ();
      if (mob.hasRestriction() && horizontalRange > 1) {
         BlockPos restriction = mob.getRestrictCenter();
         // Vanilla compares the mob's coordinate with the restriction block's
         // lower corner. Keep that behavior using the exact fractional component;
         // comparing only getBlockX/Z made every mob in the center block choose
         // the same outward adjustment.
         boolean eastOfRestriction = mob.getBlockX() > restriction.getX()
               || mob.getBlockX() == restriction.getX() && mob.sectorPosition().subX() > 0.0D;
         if (eastOfRestriction) {
            offsetX -= random.nextInt(horizontalRange / 2);
         } else {
            offsetX += random.nextInt(horizontalRange / 2);
         }

         boolean southOfRestriction = mob.getBlockZ() > restriction.getZ()
               || mob.getBlockZ() == restriction.getZ() && mob.sectorPosition().subZ() > 0.0D;
         if (southOfRestriction) {
            offsetZ -= random.nextInt(horizontalRange / 2);
         } else {
            offsetZ += random.nextInt(horizontalRange / 2);
         }
      }

      return new BlockPos(WorldBounds.addBlockOffset(mob.getBlockX(), offsetX),
            mob.getBlockY() + offset.getY(), WorldBounds.addBlockOffset(mob.getBlockZ(), offsetZ));
   }
}