package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private final long minChunkX;
    private final long minChunkZ;
    protected final RenderChunk[][] chunks;
    protected final Level level;

    RenderChunkRegion(Level pLevel, long pCenterX, long pCenterZ, RenderChunk[][] pChunks) {
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
        long sectionX = pos.getX() >> 4;
        long sectionZ = pos.getZ() >> 4;
        // Keep the 64-bit differences intact until the bounds test.  Casting a
        // far-away section offset first could wrap it into this small array.
        double xOffset = WorldBounds.signedDifference(sectionX, this.minChunkX);
        double zOffset = WorldBounds.signedDifference(sectionZ, this.minChunkZ);
        if (xOffset < 0.0D || xOffset >= (double) this.chunks.length || zOffset < 0.0D) {
            return null;
        }

        int i = (int) xOffset;
        if (this.chunks[i] == null || zOffset >= (double) this.chunks[i].length) {
            return null;
        }

        return this.chunks[i][(int) zOffset];
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
