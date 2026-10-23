package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class WorldCarver<C extends CarverConfiguration> {
    public static final WorldCarver<CaveCarverConfiguration> CAVE = register("cave", new CaveWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CaveCarverConfiguration> NETHER_CAVE = register("nether_cave", new NetherWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CanyonCarverConfiguration> CANYON = register("canyon", new CanyonWorldCarver(CanyonCarverConfiguration.CODEC));
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected static final FluidState WATER = Fluids.WATER.defaultFluidState();
    protected static final FluidState LAVA = Fluids.LAVA.defaultFluidState();
    protected Set<Fluid> liquids = ImmutableSet.of(Fluids.WATER);
    private final Codec<ConfiguredWorldCarver<C>> configuredCodec;

    private static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(String pKey, F pCarver) {
        return Registry.register(Registry.CARVER, pKey, pCarver);
    }

    public WorldCarver(Codec<C> pCodec) {
        this.configuredCodec = pCodec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config).codec();
    }

    public ConfiguredWorldCarver<C> configured(C p_65064_) {
        return new ConfiguredWorldCarver<>(this, p_65064_);
    }

    public Codec<ConfiguredWorldCarver<C>> configuredCodec() {
        return this.configuredCodec;
    }

    public int getRange() {
        return 4;
    }

    protected static boolean usesLocalFrame(ChunkPos pos) {
        return pos.getMinBlockX().hi() != (pos.getMinBlockX().lo() >> 63) || pos.getMinBlockZ().hi() != (pos.getMinBlockZ().lo() >> 63);
    }

    protected static double startCoordinate(ChunkPos source, ChunkPos target, int offset, boolean x) {
        if (!usesLocalFrame(target)) return (double) (x ? source.getBlockX(offset).lo() : source.getBlockZ(offset).lo());
        return (x ? net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.difference(source.xHi(), source.xLo(), target.xHi(), target.xLo())
                : net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.difference(source.zHi(), source.zLo(), target.zHi(), target.zLo())) * 16.0D + offset;
    }

    protected boolean carveEllipsoid(CarvingContext pContext, C pConfig, ChunkAccess pChunk, Function<BlockPos, Holder<Biome>> pBiomeAccessor, Aquifer pAquifer, double pX, double pY, double pZ, double pHorizontalRadius, double pVerticalRadius, CarvingMask pCarvingMask, WorldCarver.CarveSkipChecker pSkipChecker) {
        ChunkPos chunkpos = pChunk.getPos();
        boolean local = usesLocalFrame(chunkpos);
        double d0 = local ? 8.0D : (double) chunkpos.getMiddleBlockX().lo();
        double d1 = local ? 8.0D : (double) chunkpos.getMiddleBlockZ().lo();
        double d2 = 16.0D + pHorizontalRadius * 2.0D;
        if (!(Math.abs(pX - d0) > d2) && !(Math.abs(pZ - d1) > d2)) {
            long i = local ? 0L : chunkpos.getMinBlockX().lo();
            long j = local ? 0L : chunkpos.getMinBlockZ().lo();
            long k = Math.max(WorldBounds.subtractBlockOffset(WorldBounds.subtractBlockOffset(Mth.lfloor(pX - pHorizontalRadius), i), 1L), 0);
            long l = Math.min(WorldBounds.subtractBlockOffset(Mth.lfloor(pX + pHorizontalRadius), i), 15);
            int i1 = Math.max(Mth.floor(pY - pVerticalRadius) - 1, pContext.getMinGenY() + 1);
            int j1 = pChunk.isUpgrading() ? 0 : 7;
            int k1 = Math.min(Mth.floor(pY + pVerticalRadius) + 1, pContext.getMinGenY() + pContext.getGenDepth() - 1 - j1);
            long l1 = Math.max(WorldBounds.subtractBlockOffset(WorldBounds.subtractBlockOffset(Mth.lfloor(pZ - pHorizontalRadius), j), 1L), 0);
            long i2 = Math.min(WorldBounds.subtractBlockOffset(Mth.lfloor(pZ + pHorizontalRadius), j), 15);
            if (!WorldBounds.isAscendingBlockRange(k, l) || !WorldBounds.isAscendingBlockRange(l1, i2)) {
                return false;
            }

            boolean flag = false;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

            for (long j2 = k;; j2 = WorldBounds.addBlockOffset(j2, 1L)) {
                long k2 = local ? j2 : chunkpos.getBlockX(j2).lo();
                double d3 = ((double) k2 + 0.5D - pX) / pHorizontalRadius;

                for (long l2 = l1;; l2 = WorldBounds.addBlockOffset(l2, 1L)) {
                    long i3 = local ? l2 : chunkpos.getBlockZ(l2).lo();
                    double d4 = ((double) i3 + 0.5D - pZ) / pHorizontalRadius;
                    if (!(d3 * d3 + d4 * d4 >= 1.0D)) {
                        MutableBoolean mutableboolean = new MutableBoolean(false);

                        for (int j3 = k1; j3 > i1; --j3) {
                            double d5 = ((double) j3 - 0.5D - pY) / pVerticalRadius;
                            if (!pSkipChecker.shouldSkip(pContext, d3, d5, d4, j3) && (!pCarvingMask.get((int) j2, j3, (int) l2) || isDebugEnabled(pConfig))) {
                                pCarvingMask.set(j2, j3, l2);
                                blockpos$mutableblockpos.set(chunkpos.getBlockX(j2), j3, chunkpos.getBlockZ(l2));
                                flag |= this.carveBlock(pContext, pConfig, pChunk, pBiomeAccessor, pCarvingMask, blockpos$mutableblockpos, blockpos$mutableblockpos1, pAquifer, mutableboolean);
                            }
                        }
                    }

                    if (!WorldBounds.canAdvanceBlock(l2, i2)) {
                        break;
                    }
                }

                if (!WorldBounds.canAdvanceBlock(j2, l)) {
                    break;
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    protected boolean carveBlock(CarvingContext pContext, C pConfig, ChunkAccess pChunk, Function<BlockPos, Holder<Biome>> pBiomeGetter, CarvingMask pCarvingMask, BlockPos.MutableBlockPos pPos, BlockPos.MutableBlockPos pCheckPos, Aquifer pAquifer, MutableBoolean pReachedSurface) {
        BlockState blockstate = pChunk.getBlockState(pPos);
        if (blockstate.is(Blocks.GRASS_BLOCK) || blockstate.is(Blocks.MYCELIUM)) {
            pReachedSurface.setTrue();
        }

        if (!this.canReplaceBlock(pConfig, blockstate) && !isDebugEnabled(pConfig)) {
            return false;
        } else {
            BlockState blockstate1 = this.getCarveState(pContext, pConfig, pPos, pAquifer);
            if (blockstate1 == null) {
                return false;
            } else {
                pChunk.setBlockState(pPos, blockstate1, false);
                if (pAquifer.shouldScheduleFluidUpdate() && !blockstate1.getFluidState().isEmpty()) {
                    pChunk.markPosForPostprocessing(pPos);
                }

                if (pReachedSurface.isTrue()) {
                    pCheckPos.setWithOffset(pPos, Direction.DOWN);
                    if (pChunk.getBlockState(pCheckPos).is(Blocks.DIRT)) {
                        pContext.topMaterial(pBiomeGetter, pChunk, pCheckPos, !blockstate1.getFluidState().isEmpty()).ifPresent((p_190743_) -> {
                            pChunk.setBlockState(pCheckPos, p_190743_, false);
                            if (!p_190743_.getFluidState().isEmpty()) {
                                pChunk.markPosForPostprocessing(pCheckPos);
                            }
                        });
                    }
                }

                return true;
            }
        }
    }

    @Nullable
    private BlockState getCarveState(CarvingContext pContext, C pConfig, BlockPos pPos, Aquifer pAquifer) {
        if (pPos.getY() <= pConfig.lavaLevel.resolveY(pContext)) {
            return LAVA.createLegacyBlock();
        } else {
            BlockState blockstate = pAquifer.computeSubstance(new DensityFunction.SinglePointContext(pPos.getXHi(), pPos.getXLo(), pPos.getY(), pPos.getZHi(), pPos.getZLo()), 0.0D);
            if (blockstate == null) {
                return isDebugEnabled(pConfig) ? pConfig.debugSettings.getBarrierState() : null;
            } else {
                return isDebugEnabled(pConfig) ? getDebugState(pConfig, blockstate) : blockstate;
            }
        }
    }

    private static BlockState getDebugState(CarverConfiguration pConfig, BlockState pState) {
        if (pState.is(Blocks.AIR)) {
            return pConfig.debugSettings.getAirState();
        } else if (pState.is(Blocks.WATER)) {
            BlockState blockstate = pConfig.debugSettings.getWaterState();
            return blockstate.hasProperty(BlockStateProperties.WATERLOGGED) ? blockstate.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(true)) : blockstate;
        } else {
            return pState.is(Blocks.LAVA) ? pConfig.debugSettings.getLavaState() : pState;
        }
    }

    public abstract boolean carve(CarvingContext pContext, C pConfig, ChunkAccess pChunk, Function<BlockPos, Holder<Biome>> pBiomeAccessor, RandomSource pRandom, Aquifer pAquifer, ChunkPos pChunkPos, CarvingMask pCarvingMask);

    public abstract boolean isStartChunk(C pConfig, RandomSource pRandom);

    protected boolean canReplaceBlock(C pConfig, BlockState pState) {
        return pState.is(pConfig.replaceable);
    }

    protected static boolean canReach(ChunkPos pChunkPos, double pX, double pZ, int pBranchIndex, int pBranchCount, float pWidth) {
        double d0 = usesLocalFrame(pChunkPos) ? 8.0D : (double) pChunkPos.getMiddleBlockX().lo();
        double d1 = usesLocalFrame(pChunkPos) ? 8.0D : (double) pChunkPos.getMiddleBlockZ().lo();
        double d2 = pX - d0;
        double d3 = pZ - d1;
        double d4 = (double) (pBranchCount - pBranchIndex);
        double d5 = (double) (pWidth + 2.0F + 16.0F);
        return d2 * d2 + d3 * d3 - d4 * d4 <= d5 * d5;
    }

    private static boolean isDebugEnabled(CarverConfiguration pConfig) {
        return pConfig.debugSettings.isDebugMode();
    }

    public interface CarveSkipChecker {
        boolean shouldSkip(CarvingContext pContext, double pRelativeX, double pRelativeY, double pRelativeZ, int pY);
    }
}
