package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {
    public static final Codec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.create((p_70136_) -> {
        return trunkPlacerParts(p_70136_).apply(p_70136_, FancyTrunkPlacer::new);
    });
    private static final double TRUNK_HEIGHT_SCALE = 0.618D;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382D;
    private static final double BRANCH_SLOPE = 0.381D;
    private static final double BRANCH_LENGTH_MAGIC = 0.328D;

    public FancyTrunkPlacer(int p_70094_, int p_70095_, int p_70096_) {
        super(p_70094_, p_70095_, p_70096_);
    }

    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
    }

    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader pLevel, BiConsumer<BlockPos, BlockState> pBlockSetter, RandomSource pRandom, int pFreeTreeHeight, BlockPos pPos, TreeConfiguration pConfig) {
        int i = 5;
        int j = pFreeTreeHeight + 2;
        int k = Mth.floor((double) j * 0.618D);
        setDirtAt(pLevel, pBlockSetter, pRandom, pPos.below(), pConfig);
        double d0 = 1.0D;
        int l = Math.min(1, Mth.floor(1.382D + Math.pow(1.0D * (double) j / 13.0D, 2.0D)));
        int i1 = pPos.getY() + k;
        int j1 = j - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();
        list.add(new FancyTrunkPlacer.FoliageCoords(pPos.above(j1), i1));

        for (; j1 >= 0; --j1) {
            float f = treeShape(j, j1);
            if (!(f < 0.0F)) {
                for (int k1 = 0; k1 < l; ++k1) {
                    double d1 = 1.0D;
                    double d2 = 1.0D * (double) f * ((double) pRandom.nextFloat() + 0.328D);
                    double d3 = (double) (pRandom.nextFloat() * 2.0F) * Math.PI;
                    double d4 = d2 * Math.sin(d3) + 0.5D;
                    double d5 = d2 * Math.cos(d3) + 0.5D;
                    BlockPos blockpos = offsetLocal(pPos, d4, (double) (j1 - 1), d5);
                    BlockPos blockpos1 = blockpos.above(5);
                    if (this.makeLimb(pLevel, pBlockSetter, pRandom, blockpos, blockpos1, false, pConfig)) {
                        double l1 = WorldBounds.signedDifference(pPos.getX(), blockpos.getX());
                        double i2 = WorldBounds.signedDifference(pPos.getZ(), blockpos.getZ());
                        double d6 = (double) blockpos.getY() - Math.sqrt(l1 * l1 + i2 * i2) * 0.381D;
                        int j2 = d6 > (double) i1 ? i1 : (int) d6;
                        BlockPos blockpos2 = new BlockPos(pPos.getX(), j2, pPos.getZ());
                        if (this.makeLimb(pLevel, pBlockSetter, pRandom, blockpos2, blockpos, false, pConfig)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockpos, blockpos2.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(pLevel, pBlockSetter, pRandom, pPos, pPos.above(k), true, pConfig);
        this.makeBranches(pLevel, pBlockSetter, pRandom, j, pPos, list, pConfig);
        List<FoliagePlacer.FoliageAttachment> list1 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer$foliagecoords : list) {
            if (this.trimBranches(j, fancytrunkplacer$foliagecoords.getBranchBase() - pPos.getY())) {
                list1.add(fancytrunkplacer$foliagecoords.attachment);
            }
        }

        return list1;
    }

    private boolean makeLimb(LevelSimulatedReader p_226108_, BiConsumer<BlockPos, BlockState> p_226109_, RandomSource p_226110_, BlockPos p_226111_, BlockPos p_226112_, boolean p_226113_, TreeConfiguration p_226114_) {
        if (!p_226113_ && Objects.equals(p_226111_, p_226112_)) {
            return true;
        } else {
            BlockPos blockpos = p_226112_.offset(-p_226111_.getX(), -p_226111_.getY(), -p_226111_.getZ());
            long i = this.getSteps(blockpos);
            float f = (float) blockpos.getX() / (float) i;
            float f1 = (float) blockpos.getY() / (float) i;
            float f2 = (float) blockpos.getZ() / (float) i;

            for (int j = 0; j <= i; ++j) {
                BlockPos blockpos1 = offsetLocal(p_226111_, (double) (0.5F + (float) j * f), (double) (0.5F + (float) j * f1), (double) (0.5F + (float) j * f2));
                if (p_226113_) {
                    this.placeLog(p_226108_, p_226109_, p_226110_, blockpos1, p_226114_, (p_161826_) -> {
                        return p_161826_.setValue(RotatedPillarBlock.AXIS, this.getLogAxis(p_226111_, blockpos1));
                    });
                } else if (!this.isFree(p_226108_, blockpos1)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static BlockPos offsetLocal(BlockPos origin, double dx, double dy, double dz) {
        if (FarlandsMode.usesPreciseCoordinates()) {
            return new BlockPos(WorldBounds.addDoubleBlockOffset(origin.getX(), dx), Mth.floor((double) origin.getY() + dy), WorldBounds.addDoubleBlockOffset(origin.getZ(), dz));
        } else {
            return origin.offset(dx, dy, dz);
        }
    }

    private long getSteps(BlockPos pPos) {
        long i = Math.abs(pPos.getX());
        long j = Math.abs(pPos.getY());
        long k = Math.abs(pPos.getZ());
        return Math.max(i, Math.max(j, k));
    }

    private Direction.Axis getLogAxis(BlockPos pPos, BlockPos pOtherPos) {
        Direction.Axis direction$axis = Direction.Axis.Y;
        double i = WorldBounds.distance(pOtherPos.getX(), pPos.getX());
        double j = WorldBounds.distance(pOtherPos.getZ(), pPos.getZ());
        double k = Math.max(i, j);
        if (k > 0.0D) {
            if (i == k) {
                direction$axis = Direction.Axis.X;
            } else {
                direction$axis = Direction.Axis.Z;
            }
        }

        return direction$axis;
    }

    private boolean trimBranches(int p_70099_, int p_70100_) {
        return (double) p_70100_ >= (double) p_70099_ * 0.2D;
    }

    private void makeBranches(LevelSimulatedReader p_226100_, BiConsumer<BlockPos, BlockState> p_226101_, RandomSource p_226102_, int p_226103_, BlockPos p_226104_, List<FancyTrunkPlacer.FoliageCoords> p_226105_, TreeConfiguration p_226106_) {
        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer$foliagecoords : p_226105_) {
            int i = fancytrunkplacer$foliagecoords.getBranchBase();
            BlockPos blockpos = new BlockPos(p_226104_.getX(), i, p_226104_.getZ());
            if (!blockpos.equals(fancytrunkplacer$foliagecoords.attachment.pos()) && this.trimBranches(p_226103_, i - p_226104_.getY())) {
                this.makeLimb(p_226100_, p_226101_, p_226102_, blockpos, fancytrunkplacer$foliagecoords.attachment.pos(), true, p_226106_);
            }
        }
    }

    private static float treeShape(int p_70133_, int p_70134_) {
        if ((float) p_70134_ < (float) p_70133_ * 0.3F) {
            return -1.0F;
        } else {
            float f = (float) p_70133_ / 2.0F;
            float f1 = f - (float) p_70134_;
            float f2 = Mth.sqrt(f * f - f1 * f1);
            if (f1 == 0.0F) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }

            return f2 * 0.5F;
        }
    }

    static class FoliageCoords {
        final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos pAttachmentPos, int pBranchBase) {
            this.attachment = new FoliagePlacer.FoliageAttachment(pAttachmentPos, 0, false);
            this.branchBase = pBranchBase;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
