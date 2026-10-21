package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int128;
import net.minecraft.core.Int128Math;
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
    /** Lowest X/Z section coordinates represented by {@link #chunks}, kept raw so reads never allocate. */
    private final long minChunkXHi;
    private final long minChunkXLo;
    private final long minChunkZHi;
    private final long minChunkZLo;
    protected final RenderChunk[][] chunks;
    protected final Level level;

    RenderChunkRegion(Level pLevel, Int128 pCenterX, Int128 pCenterZ, RenderChunk[][] pChunks) {
        this.level = pLevel;
        this.minChunkXHi = pCenterX.hi();
        this.minChunkXLo = pCenterX.lo();
        this.minChunkZHi = pCenterZ.hi();
        this.minChunkZLo = pCenterZ.lo();
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
        int i = this.localIndexOf(pos.xHi(), pos.xLo(), this.minChunkXHi, this.minChunkXLo, this.chunks.length);
        if (i < 0 || this.chunks[i] == null) {
            return null;
        }

        int j = this.localIndexOf(pos.zHi(), pos.zLo(), this.minChunkZHi, this.minChunkZLo, this.chunks[i].length);
        return j < 0 ? null : this.chunks[i][j];
    }

    private int localIndexOf(long blockHi, long blockLo, long originChunkHi, long originChunkLo, int size) {
        return localIndex(Int128Math.rightShiftHi(blockHi, 4), Int128Math.rightShiftLo(blockHi, blockLo, 4),
                originChunkHi, originChunkLo, size);
    }

    static int localIndex(Int128 section, Int128 origin, int size) {
        return localIndex(section.hi(), section.lo(), origin.hi(), origin.lo(), size);
    }

    static int localIndex(long sectionHi, long sectionLo, long originHi, long originLo, int size) {
        // Subtract before conversion, and bounds-check before narrowing to an array index.
        // The region is only a few chunks wide, so the true signed difference always
        // fits a long exactly; the saturation sentinels of
        // {@link Int128Math#signedDifferenceAsLong} fall outside the accept range.
        long offset = Int128Math.signedDifferenceAsLong(sectionHi, sectionLo, originHi, originLo);
        return offset >= 0L && offset < size ? (int) offset : -1;
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
