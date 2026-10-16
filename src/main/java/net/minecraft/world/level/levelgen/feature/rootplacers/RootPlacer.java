package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public abstract class RootPlacer {
    public static final Codec<RootPlacer> CODEC = Registry.ROOT_PLACER_TYPES.byNameCodec().dispatch(RootPlacer::type, RootPlacerType::codec);
    protected final IntProvider trunkOffsetY;
    protected final BlockStateProvider rootProvider;
    protected final Optional<AboveRootPlacement> aboveRootPlacement;

    protected static <P extends RootPlacer> Products.P3<RecordCodecBuilder.Mu<P>, IntProvider, BlockStateProvider, Optional<AboveRootPlacement>> rootPlacerParts(RecordCodecBuilder.Instance<P> p_225886_) {
        return p_225886_.group(IntProvider.CODEC.fieldOf("trunk_offset_y").forGetter((p_225897_) -> {
            return p_225897_.trunkOffsetY;
        }),
                BlockStateProvider.CODEC.fieldOf("root_provider").forGetter((p_225895_) -> {
                    return p_225895_.rootProvider;
                }),
                AboveRootPlacement.CODEC.optionalFieldOf("above_root_placement").forGetter((p_225888_) -> {
                    return p_225888_.aboveRootPlacement;
                }));
    }

    public RootPlacer(IntProvider pTrunkOffset, BlockStateProvider pRootProvider, Optional<AboveRootPlacement> pAboveRootPlacement) {
        this.trunkOffsetY = pTrunkOffset;
        this.rootProvider = pRootProvider;
        this.aboveRootPlacement = pAboveRootPlacement;
    }

    protected abstract RootPlacerType<?> type();

    public abstract boolean placeRoots(LevelSimulatedReader p_225879_, BiConsumer<BlockPos, BlockState> p_225880_, RandomSource p_225881_, BlockPos p_225882_, BlockPos p_225883_, TreeConfiguration p_225884_);

    protected boolean canPlaceRoot(LevelSimulatedReader pLevel, BlockPos pPos) {
        return TreeFeature.validTreePos(pLevel, pPos);
    }

    protected void placeRoot(LevelSimulatedReader p_225874_, BiConsumer<BlockPos, BlockState> p_225875_, RandomSource p_225876_, BlockPos p_225877_, TreeConfiguration p_225878_) {
        if (this.canPlaceRoot(p_225874_, p_225877_)) {
            p_225875_.accept(p_225877_, this.getPotentiallyWaterloggedState(p_225874_, p_225877_, this.rootProvider.getState(p_225876_, p_225877_)));
            if (this.aboveRootPlacement.isPresent()) {
                AboveRootPlacement aboverootplacement = this.aboveRootPlacement.get();
                BlockPos blockpos = p_225877_.above();
                if (p_225876_.nextFloat() < aboverootplacement.aboveRootPlacementChance() && p_225874_.isStateAtPosition(blockpos, BlockBehaviour.BlockStateBase::isAir)) {
                    p_225875_.accept(blockpos, this.getPotentiallyWaterloggedState(p_225874_, blockpos, aboverootplacement.aboveRootProvider().getState(p_225876_, blockpos)));
                }
            }
        }
    }

    protected BlockState getPotentiallyWaterloggedState(LevelSimulatedReader pLevel, BlockPos pPos, BlockState pState) {
        if (pState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean flag = pLevel.isFluidAtPosition(pPos, (p_225890_) -> {
                return p_225890_.is(FluidTags.WATER);
            });
            return pState.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(flag));
        } else {
            return pState;
        }
    }

    public BlockPos getTrunkOrigin(BlockPos pPos, RandomSource pRandom) {
        return pPos.above(this.trunkOffsetY.sample(pRandom));
    }
}
