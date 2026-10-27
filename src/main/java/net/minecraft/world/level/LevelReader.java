package net.minecraft.world.level;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public interface LevelReader extends BlockAndTintGetter, CollisionGetter, BiomeManager.NoiseBiomeSource {
    @Nullable
    default ChunkAccess getChunk(long pX, long pZ, ChunkStatus pRequiredStatus, boolean pNonnull) {
        return this.getChunk(net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ), pRequiredStatus, pNonnull);
    }

    @Nullable
    ChunkAccess getChunk(net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ, ChunkStatus pRequiredStatus, boolean pNonnull);

    /** @deprecated */
    @Deprecated
    default boolean hasChunk(long pChunkX, long pChunkZ) {
        return this.hasChunk(net.minecraft.core.Int1088.fromLong(pChunkX), net.minecraft.core.Int1088.fromLong(pChunkZ));
    }

    boolean hasChunk(net.minecraft.core.Int1088 pChunkX, net.minecraft.core.Int1088 pChunkZ);

    int getHeight(Heightmap.Types pHeightmapType, long pX, long pZ);

    default int getHeight(Heightmap.Types pHeightmapType, Int1088 pX, Int1088 pZ) {
        Int1088 chunkX = SectionPos.blockToSectionCoord(pX);
        Int1088 chunkZ = SectionPos.blockToSectionCoord(pZ);
        int height = this.getMinBuildHeight();
        if (this.hasChunk(chunkX, chunkZ)) {
            ChunkAccess chunk = this.getChunk(chunkX, chunkZ, ChunkStatus.HEIGHTMAPS, true);
            if (chunk != null)
                height = chunk.getHeight(pHeightmapType, (int) (pX.lowLimb() & 15L), (int) (pZ.lowLimb() & 15L)) + 1;
        }
        return height;
    }

    int getSkyDarken();

    BiomeManager getBiomeManager();

    default Holder<Biome> getBiome(BlockPos pPos) {
        return this.getBiomeManager().getBiome(pPos);
    }

    default Stream<BlockState> getBlockStatesIfLoaded(AABB pAabb) {
        int i = Mth.floor(pAabb.minX);
        int j = Mth.floor(pAabb.maxX);
        int k = Mth.floor(pAabb.minY);
        int l = Mth.floor(pAabb.maxY);
        int i1 = Mth.floor(pAabb.minZ);
        int j1 = Mth.floor(pAabb.maxZ);
        return this.hasChunksAt(i, k, i1, j, l, j1) ? this.getBlockStates(pAabb) : Stream.empty();
    }

    default int getBlockTint(BlockPos pBlockPos, ColorResolver pColorResolver) {
        // ColorResolver's legacy double arguments are visual sampling hints only.
        return pColorResolver.getColor(this.getBiome(pBlockPos).value(),
                pBlockPos.getX().toDoubleSaturated(), pBlockPos.getZ().toDoubleSaturated());
    }

    default Holder<Biome> getNoiseBiome(long pX, int pY, long pZ) {
        return this.getNoiseBiome(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    default Holder<Biome> getNoiseBiome(Int1088 pX, int pY, Int1088 pZ) {
        Int1088 sectionX = pX.shiftRight(2);
        Int1088 sectionZ = pZ.shiftRight(2);
        ChunkAccess chunkaccess = this.getChunk(sectionX, sectionZ, ChunkStatus.BIOMES, false);
        // Resolve exact chunk identity before projecting palette/noise sampling coordinates.
        return chunkaccess != null ? chunkaccess.getNoiseBiome(pX.lowLimb(), pY, pZ.lowLimb()) : this.getUncachedNoiseBiome(pX, pY, pZ);
    }

    default Holder<Biome> getUncachedNoiseBiome(long pX, int pY, long pZ) {
        return this.getUncachedNoiseBiome(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    Holder<Biome> getUncachedNoiseBiome(Int1088 pX, int pY, Int1088 pZ);

    boolean isClientSide();

    /** @deprecated */
    @Deprecated
    int getSeaLevel();

    DimensionType dimensionType();

    default int getMinBuildHeight() {
        return this.dimensionType().minY();
    }

    default int getHeight() {
        return this.dimensionType().height();
    }

    default BlockPos getHeightmapPos(Heightmap.Types pHeightmapType, BlockPos pPos) {
        // Generation regions can read primed heightmaps before HEIGHTMAPS status.
        return new BlockPos(pPos.getX(), this.getHeight(pHeightmapType, pPos.getX(), pPos.getZ()), pPos.getZ());
    }

    default boolean isEmptyBlock(BlockPos pPos) {
        return this.getBlockState(pPos).isAir();
    }

    default boolean canSeeSkyFromBelowWater(BlockPos pPos) {
        if (pPos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pPos);
        } else {
            BlockPos blockpos = new BlockPos(pPos.getX(), this.getSeaLevel(), pPos.getZ());
            if (!this.canSeeSky(blockpos)) {
                return false;
            } else {
                for (BlockPos blockpos1 = blockpos.below(); blockpos1.getY() > pPos.getY(); blockpos1 = blockpos1.below()) {
                    BlockState blockstate = this.getBlockState(blockpos1);
                    if (blockstate.getLightBlock(this, blockpos1) > 0 && !blockstate.getMaterial().isLiquid()) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    default float getPathfindingCostFromLightLevels(BlockPos p_220420_) {
        return this.getLightLevelDependentMagicValue(p_220420_) - 0.5F;
    }

    /** @deprecated */
    @Deprecated
    default float getLightLevelDependentMagicValue(BlockPos p_220418_) {
        float f = (float) this.getMaxLocalRawBrightness(p_220418_) / 15.0F;
        float f1 = f / (4.0F - 3.0F * f);
        return Mth.lerp(this.dimensionType().ambientLight(), f1, 1.0F);
    }

    default int getDirectSignal(BlockPos pPos, Direction pDirection) {
        return this.getBlockState(pPos).getDirectSignal(this, pPos, pDirection);
    }

    default ChunkAccess getChunk(BlockPos pPos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pPos.getX()), SectionPos.blockToSectionCoord(pPos.getZ()));
    }

    default ChunkAccess getChunk(long pChunkX, long pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(net.minecraft.core.Int1088 pChunkX, net.minecraft.core.Int1088 pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(net.minecraft.core.Int1088 pChunkX, net.minecraft.core.Int1088 pChunkZ, ChunkStatus pRequiredStatus) {
        return this.getChunk(pChunkX, pChunkZ, pRequiredStatus, true);
    }

    default ChunkAccess getChunk(long pChunkX, long pChunkZ, ChunkStatus pRequiredStatus) {
        return this.getChunk(pChunkX, pChunkZ, pRequiredStatus, true);
    }

    @Nullable
    default BlockGetter getChunkForCollisions(long pChunkX, long pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.EMPTY, false);
    }

    @Nullable
    default BlockGetter getChunkForCollisions(Int1088 pChunkX, Int1088 pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.EMPTY, false);
    }

    default boolean isWaterAt(BlockPos pPos) {
        return this.getFluidState(pPos).is(FluidTags.WATER);
    }

    default boolean containsAnyLiquid(AABB pBb) {
        net.minecraft.world.phys.SectorAABB exactBox = pBb.getSectorBounds();
        if (exactBox != null) {
            for (BlockPos pos : BlockPos.betweenClosed(exactBox.minBlockXForRange(), exactBox.minBlockYForRange(), exactBox.minBlockZForRange(),
                    exactBox.maxBlockXForRangeInclusive(), exactBox.maxBlockYExclusive() - 1, exactBox.maxBlockZForRangeInclusive())) {
                if (!this.getFluidState(pos).isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        int i = Mth.floor(pBb.minX);
        int j = Mth.ceil(pBb.maxX);
        int k = Mth.floor(pBb.minY);
        int l = Mth.ceil(pBb.maxY);
        int i1 = Mth.floor(pBb.minZ);
        int j1 = Mth.ceil(pBb.maxZ);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    BlockState blockstate = this.getBlockState(blockpos$mutableblockpos.set(k1, l1, i2));
                    if (!blockstate.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    default int getMaxLocalRawBrightness(BlockPos pPos) {
        return this.getMaxLocalRawBrightness(pPos, this.getSkyDarken());
    }

    default int getMaxLocalRawBrightness(BlockPos pPos, int pAmount) {
        return this.getRawBrightness(pPos, pAmount);
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(long pX, long pZ) {
        return this.hasChunkAt(Int1088.fromLong(pX), Int1088.fromLong(pZ));
    }

    default boolean hasChunkAt(Int1088 pX, Int1088 pZ) {
        return this.hasChunk(SectionPos.blockToSectionCoord(pX), SectionPos.blockToSectionCoord(pZ));
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(BlockPos pPos) {
        return this.hasChunkAt(pPos.getX(), pPos.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(BlockPos pFrom, BlockPos pTo) {
        return this.hasChunksAt(pFrom.getX(), pFrom.getY(), pFrom.getZ(), pTo.getX(), pTo.getY(), pTo.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(long pFromX, int pFromY, long pFromZ, long pToX, int pToY, long pToZ) {
        return this.hasChunksAt(Int1088.fromLong(pFromX), pFromY, Int1088.fromLong(pFromZ),
                Int1088.fromLong(pToX), pToY, Int1088.fromLong(pToZ));
    }

    default boolean hasChunksAt(Int1088 pFromX, int pFromY, Int1088 pFromZ, Int1088 pToX, int pToY, Int1088 pToZ) {
        return pToY >= this.getMinBuildHeight() && pFromY < this.getMaxBuildHeight() ? this.hasChunksAt(pFromX, pFromZ, pToX, pToZ) : false;
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(long pFromX, long pFromZ, long pToX, long pToZ) {
        return this.hasChunksAt(Int1088.fromLong(pFromX), Int1088.fromLong(pFromZ),
                Int1088.fromLong(pToX), Int1088.fromLong(pToZ));
    }

    default boolean hasChunksAt(Int1088 pFromX, Int1088 pFromZ, Int1088 pToX, Int1088 pToZ) {
        if (WorldBounds.localSpanExceeds(pFromX, pToX) || WorldBounds.localSpanExceeds(pFromZ, pToZ))
            return false;
        Int1088 firstX = SectionPos.blockToSectionCoord(pFromX.compareTo(pToX) <= 0 ? pFromX : pToX);
        Int1088 lastX = SectionPos.blockToSectionCoord(pFromX.compareTo(pToX) <= 0 ? pToX : pFromX);
        Int1088 firstZ = SectionPos.blockToSectionCoord(pFromZ.compareTo(pToZ) <= 0 ? pFromZ : pToZ);
        Int1088 lastZ = SectionPos.blockToSectionCoord(pFromZ.compareTo(pToZ) <= 0 ? pToZ : pFromZ);
        long width = WorldBounds.signedDifferenceAsLong(lastX, firstX) + 1L;
        long depth = WorldBounds.signedDifferenceAsLong(lastZ, firstZ) + 1L;
        if (width > Integer.MAX_VALUE / depth)
            return false;
        for (Int1088 x = firstX;; x = WorldBounds.addChunkOffset(x, 1L)) {
            for (Int1088 z = firstZ;; z = WorldBounds.addChunkOffset(z, 1L)) {
                if (!this.hasChunk(x, z))
                    return false;
                if (z.equals(lastZ))
                    break;
            }
            if (x.equals(lastX))
                break;
        }
        return true;
    }
}
