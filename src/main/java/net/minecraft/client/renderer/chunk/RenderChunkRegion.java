package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderChunkRegion implements BlockAndTintGetter {
    /** Lowest X/Z section coordinates represented by {@link #chunks}. */
    private final Int1088 minChunkX;
    private final Int1088 minChunkZ;
    protected final RenderChunk[][] chunks;
    protected final Level level;

    RenderChunkRegion(Level pLevel, Int1088 pCenterX, Int1088 pCenterZ, RenderChunk[][] pChunks) {
        this.level = pLevel;
        this.minChunkX = pCenterX;
        this.minChunkZ = pCenterZ;
        this.chunks = pChunks;
    }

    public BlockState getBlockState(BlockPos pPos) {
        RenderChunk renderchunk = this.getChunk(pPos);
        return renderchunk == null ? Blocks.AIR.defaultBlockState() : renderchunk.getBlockState(pPos);
    }

    public FluidState getFluidState(BlockPos pPos) {
        RenderChunk renderchunk = this.getChunk(pPos);
        return renderchunk == null ? Fluids.EMPTY.defaultFluidState() : renderchunk.getBlockState(pPos).getFluidState();
    }

    public float getShade(Direction pDirection, boolean pShade) {
        return this.level.getShade(pDirection, pShade);
    }

    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pPos) {
        RenderChunk renderchunk = this.getChunk(pPos);
        return renderchunk == null ? null : renderchunk.getBlockEntity(pPos);
    }

    @Nullable
    private RenderChunk getChunk(BlockPos pos) {
        int i = localBlockIndex(pos, true, this.minChunkX, this.chunks.length);
        if (i < 0 || this.chunks[i] == null) {
            return null;
        }

        int j = localBlockIndex(pos, false, this.minChunkZ, this.chunks[i].length);
        return j < 0 ? null : this.chunks[i][j];
    }

    static int localIndex(Int1088 section, Int1088 origin, int size) {
        long offset = WorldBounds.signedDifferenceAsLong(section, origin);
        return offset >= 0L && offset < size ? (int) offset : -1;
    }

    static int localBlockIndex(BlockPos block, boolean x, Int1088 origin, int size) {
        long first = ((x ? block.xLimb(0) : block.zLimb(0)) >>> 4)
                | ((x ? block.xLimb(1) : block.zLimb(1)) << 60);
        long low = first - origin.lowLimb();
        if (low < 0 || low >= size) return -1;
        long borrow = Long.compareUnsigned(first, origin.lowLimb()) < 0 ? 1 : 0;
        int length = Math.min(Int1088.LIMBS,
                Math.max(x ? block.xLimbCount() : block.zLimbCount(), origin.limbCount()) + 1);
        for (int i = 1; i < length; ++i) {
            long word = x ? block.xLimb(i) : block.zLimb(i);
            long high = i + 1 < Int1088.LIMBS ? (x ? block.xLimb(i + 1) : block.zLimb(i + 1)) : word >> 63;
            long a = (word >>> 4) | (high << 60);
            long b = origin.limb(i);
            long difference = a - b;
            long result = difference - borrow;
            borrow = Long.compareUnsigned(a, b) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
            if (result != 0 || i == length - 1 && a < 0 && b >= 0) return -1;
        }
        return (int) low;
    }

    /** Shift and subtract limb by limb, without allocating a section coordinate per block lookup. */
    static int localBlockIndex(Int1088 block, Int1088 origin, int size) {
        if (block.fitsLong() && origin.fitsLong()) {
            long section = block.lowLimb() >> 4;
            long base = origin.lowLimb();
            long difference = section - base;
            if (((section ^ base) & (section ^ difference)) < 0) return -1;
            return difference >= 0 && difference < size ? (int) difference : -1;
        }
        long first = (block.limb(0) >>> 4) | (block.limb(1) << 60);
        long low = first - origin.limb(0);
        if (low < 0 || low >= size) return -1;
        long borrow = Long.compareUnsigned(first, origin.limb(0)) < 0 ? 1 : 0;
        int length = Math.min(Int1088.LIMBS, Math.max(block.limbCount(), origin.limbCount()) + 1);
        for (int i = 1; i < length; ++i) {
            long a = i == Int1088.LIMBS - 1 ? block.limb(i) >> 4
                    : (block.limb(i) >>> 4) | (block.limb(i + 1) << 60);
            long b = origin.limb(i);
            long difference = a - b;
            long result = difference - borrow;
            borrow = Long.compareUnsigned(a, b) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
            if (result != 0) return -1;
            if (i == length - 1 && a < 0 && b >= 0) return -1;
        }
        return (int) low;
    }

    public int getBlockTint(BlockPos pPos, ColorResolver pColorResolver) {
        return this.level.getBlockTint(pPos, pColorResolver);
    }

    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    public int getHeight() {
        return this.level.getHeight();
    }
}
