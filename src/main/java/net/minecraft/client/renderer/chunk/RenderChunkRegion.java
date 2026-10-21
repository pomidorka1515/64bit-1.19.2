package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int128;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
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
    private final Int128 minChunkX;
    private final Int128 minChunkZ;
    protected final RenderChunk[][] chunks;
    protected final Level level;

    RenderChunkRegion(Level pLevel, Int128 pCenterX, Int128 pCenterZ, RenderChunk[][] pChunks) {
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
        int i = localIndex(pos.getX().shiftRight(4), this.minChunkX, this.chunks.length);
        if (i < 0 || this.chunks[i] == null) {
            return null;
        }

        int j = localIndex(pos.getZ().shiftRight(4), this.minChunkZ, this.chunks[i].length);
        return j < 0 ? null : this.chunks[i][j];
    }

    static int localIndex(Int128 section, Int128 origin, int size) {
        // Subtract before conversion, and bounds-check before narrowing to an array index.
        double offset = WorldBounds.signedDifference(section, origin);
        return offset >= 0.0D && offset < size ? (int) offset : -1;
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
