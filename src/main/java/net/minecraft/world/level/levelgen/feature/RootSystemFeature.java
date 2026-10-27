package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;

public class RootSystemFeature extends Feature<RootSystemConfiguration> {
    public RootSystemFeature(Codec<RootSystemConfiguration> pCodec) {
        super(pCodec);
    }

    public boolean place(FeaturePlaceContext<RootSystemConfiguration> pContext) {
        WorldGenLevel worldgenlevel = pContext.level();
        BlockPos blockpos = pContext.origin();
        if (!worldgenlevel.getBlockState(blockpos).isAir()) {
            return false;
        } else {
            RandomSource randomsource = pContext.random();
            BlockPos blockpos1 = pContext.origin();
            RootSystemConfiguration rootsystemconfiguration = pContext.config();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos1.mutable();
            if (placeDirtAndTree(worldgenlevel, pContext.chunkGenerator(), rootsystemconfiguration, randomsource, blockpos$mutableblockpos, blockpos1)) {
                placeRoots(worldgenlevel, rootsystemconfiguration, randomsource, blockpos1, blockpos$mutableblockpos);
            }

            return true;
        }
    }

    private static boolean spaceForTree(WorldGenLevel pLevel, RootSystemConfiguration pConfig, BlockPos pPos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pPos.mutable();

        for (int i = 1; i <= pConfig.requiredVerticalSpaceForTree; ++i) {
            blockpos$mutableblockpos.move(Direction.UP);
            BlockState blockstate = pLevel.getBlockState(blockpos$mutableblockpos);
            if (!isAllowedTreeSpace(blockstate, i, pConfig.allowedVerticalWaterForTree)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAllowedTreeSpace(BlockState pState, int pY, int pAllowedVerticalWater) {
        if (pState.isAir()) {
            return true;
        } else {
            int i = pY + 1;
            return i <= pAllowedVerticalWater && pState.getFluidState().is(FluidTags.WATER);
        }
    }

    private static boolean placeDirtAndTree(WorldGenLevel pLevel, ChunkGenerator pChunkGenerator, RootSystemConfiguration pConfig, RandomSource pRandom, BlockPos.MutableBlockPos pMutablePos, BlockPos pBasePos) {
        for (int i = 0; i < pConfig.rootColumnMaxHeight; ++i) {
            pMutablePos.move(Direction.UP);
            if (pConfig.allowedTreePosition.test(pLevel, pMutablePos) && spaceForTree(pLevel, pConfig, pMutablePos)) {
                BlockPos blockpos = pMutablePos.below();
                if (pLevel.getFluidState(blockpos).is(FluidTags.LAVA) || !pLevel.getBlockState(blockpos).getMaterial().isSolid()) {
                    return false;
                }

                if (pConfig.treeFeature.value().place(pLevel, pChunkGenerator, pRandom, pMutablePos)) {
                    placeDirt(pBasePos, pBasePos.getY() + i, pLevel, pConfig, pRandom);
                    return true;
                }
            }
        }

        return false;
    }

    private static void placeDirt(BlockPos p_225223_, int p_225224_, WorldGenLevel p_225225_, RootSystemConfiguration p_225226_, RandomSource p_225227_) {
        Int1088 i = p_225223_.getX();
        Int1088 j = p_225223_.getZ();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_225223_.mutable();

        for (int k = p_225223_.getY(); k < p_225224_; ++k) {
            placeRootedDirt(p_225225_, p_225226_, p_225227_, i, j, blockpos$mutableblockpos.set(i, k, j));
        }
    }

    private static void placeRootedDirt(WorldGenLevel pLevel, RootSystemConfiguration pConfig, RandomSource pRandom, Int1088 pX, Int1088 pZ, BlockPos.MutableBlockPos pPos) {
        int i = pConfig.rootRadius;
        Predicate<BlockState> predicate = (p_204762_) -> {
            return p_204762_.is(pConfig.rootReplaceable);
        };

        for (int j = 0; j < pConfig.rootPlacementAttempts; ++j) {
            pPos.setWithOffset(pPos, pRandom.nextInt(i) - pRandom.nextInt(i), 0, pRandom.nextInt(i) - pRandom.nextInt(i));
            if (predicate.test(pLevel.getBlockState(pPos))) {
                pLevel.setBlock(pPos, pConfig.rootStateProvider.getState(pRandom, pPos), 2);
            }

            pPos.setX(pX);
            pPos.setZ(pZ);
        }
    }

    private static void placeRoots(WorldGenLevel pLevel, RootSystemConfiguration pConfig, RandomSource pRandom, BlockPos pBasePos, BlockPos.MutableBlockPos pMutablePos) {
        int i = pConfig.hangingRootRadius;
        int j = pConfig.hangingRootsVerticalSpan;

        for (int k = 0; k < pConfig.hangingRootPlacementAttempts; ++k) {
            pMutablePos.setWithOffset(pBasePos, pRandom.nextInt(i) - pRandom.nextInt(i), pRandom.nextInt(j) - pRandom.nextInt(j), pRandom.nextInt(i) - pRandom.nextInt(i));
            if (pLevel.isEmptyBlock(pMutablePos)) {
                BlockState blockstate = pConfig.hangingRootStateProvider.getState(pRandom, pMutablePos);
                if (blockstate.canSurvive(pLevel, pMutablePos) && pLevel.getBlockState(pMutablePos.above()).isFaceSturdy(pLevel, pMutablePos, Direction.DOWN)) {
                    pLevel.setBlock(pMutablePos, blockstate, 2);
                }
            }
        }
    }
}
