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

    public static BlockPos generateRandomDirection(RandomSource pRandom, int pHorizontalDistance, int pVerticalDistance) {
        int i = pRandom.nextInt(2 * pHorizontalDistance + 1) - pHorizontalDistance;
        int j = pRandom.nextInt(2 * pVerticalDistance + 1) - pVerticalDistance;
        int k = pRandom.nextInt(2 * pHorizontalDistance + 1) - pHorizontalDistance;
        return new BlockPos(i, j, k);
    }

    @Nullable
    public static BlockPos generateRandomDirectionWithinRadians(RandomSource pRandom, int pMaxHorizontalDifference, int pYRange, int pY, double pX, double pZ, double pMaxAngleDelta) {
        double d0 = Mth.atan2(pZ, pX) - (double) ((float) Math.PI / 2F);
        double d1 = d0 + (double) (2.0F * pRandom.nextFloat() - 1.0F) * pMaxAngleDelta;
        double d2 = Math.sqrt(pRandom.nextDouble()) * (double) Mth.SQRT_OF_TWO * (double) pMaxHorizontalDifference;
        double d3 = -d2 * Math.sin(d1);
        double d4 = d2 * Math.cos(d1);
        if (!(Math.abs(d3) > (double) pMaxHorizontalDifference) && !(Math.abs(d4) > (double) pMaxHorizontalDifference)) {
            int i = pRandom.nextInt(2 * pYRange + 1) - pYRange + pY;
            return new BlockPos(d3, (double) i, d4);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pPos, int pMaxY, Predicate<BlockPos> pPosPredicate) {
        if (!pPosPredicate.test(pPos)) {
            return pPos;
        } else {
            BlockPos blockpos;
            for (blockpos = pPos.above(); blockpos.getY() < pMaxY && pPosPredicate.test(blockpos); blockpos = blockpos.above()) {
            }

            return blockpos;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pPos, int pAboveSolidAmount, int pMaxY, Predicate<BlockPos> pPosPredicate) {
        if (pAboveSolidAmount < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + pAboveSolidAmount + ", expected >= 0");
        } else if (!pPosPredicate.test(pPos)) {
            return pPos;
        } else {
            BlockPos blockpos;
            for (blockpos = pPos.above(); blockpos.getY() < pMaxY && pPosPredicate.test(blockpos); blockpos = blockpos.above()) {
            }

            BlockPos blockpos1;
            BlockPos blockpos2;
            for (blockpos1 = blockpos; blockpos1.getY() < pMaxY && blockpos1.getY() - blockpos.getY() < pAboveSolidAmount; blockpos1 = blockpos2) {
                blockpos2 = blockpos1.above();
                if (pPosPredicate.test(blockpos2)) {
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

        return best == null ? null : SectorVec3.fromBlockAndFraction(best.getX(), 0.5D, (double) best.getY(), best.getZ(), 0.5D);
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
                offsetX = WorldBounds.subtractBlockOffset(offsetX, random.nextInt(horizontalRange / 2));
            } else {
                offsetX = WorldBounds.addBlockOffset(offsetX, random.nextInt(horizontalRange / 2));
            }

            boolean southOfRestriction = mob.getBlockZ() > restriction.getZ()
                    || mob.getBlockZ() == restriction.getZ() && mob.sectorPosition().subZ() > 0.0D;
            if (southOfRestriction) {
                offsetZ = WorldBounds.subtractBlockOffset(offsetZ, random.nextInt(horizontalRange / 2));
            } else {
                offsetZ = WorldBounds.addBlockOffset(offsetZ, random.nextInt(horizontalRange / 2));
            }
        }

        return new BlockPos(WorldBounds.addBlockOffset(mob.getBlockX(), offsetX),
                mob.getBlockY() + offset.getY(), WorldBounds.addBlockOffset(mob.getBlockZ(), offsetZ));
    }
}
