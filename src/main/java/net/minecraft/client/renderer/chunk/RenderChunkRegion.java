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

   RenderChunkRegion(Level p_200456_, long p_200457_, long p_200458_, RenderChunk[][] p_200459_) {
      this.level = p_200456_;
      this.minChunkX = p_200457_;
      this.minChunkZ = p_200458_;
      this.chunks = p_200459_;
   }

   public BlockState getBlockState(BlockPos p_112947_) {
      RenderChunk renderchunk = this.getChunk(p_112947_);
      return renderchunk == null ? Blocks.AIR.defaultBlockState() : renderchunk.getBlockState(p_112947_);
   }

   public FluidState getFluidState(BlockPos p_112943_) {
      RenderChunk renderchunk = this.getChunk(p_112943_);
      return renderchunk == null ? Fluids.EMPTY.defaultFluidState() : renderchunk.getBlockState(p_112943_).getFluidState();
   }

   public float getShade(Direction p_112940_, boolean p_112941_) {
      return this.level.getShade(p_112940_, p_112941_);
   }

   public LevelLightEngine getLightEngine() {
      return this.level.getLightEngine();
   }

   @Nullable
   public BlockEntity getBlockEntity(BlockPos p_112945_) {
      RenderChunk renderchunk = this.getChunk(p_112945_);
      return renderchunk == null ? null : renderchunk.getBlockEntity(p_112945_);
   }

   @Nullable
   private RenderChunk getChunk(BlockPos pos) {
      long sectionX = pos.getX() >> 4;
      long sectionZ = pos.getZ() >> 4;
      // Keep the 64-bit differences intact until the bounds test.  Casting a
      // far-away section offset first could wrap it into this small array.
      double xOffset = WorldBounds.signedDifference(sectionX, this.minChunkX);
      double zOffset = WorldBounds.signedDifference(sectionZ, this.minChunkZ);
      if (xOffset < 0.0D || xOffset >= (double)this.chunks.length || zOffset < 0.0D) {
         return null;
      }

      int i = (int)xOffset;
      if (this.chunks[i] == null || zOffset >= (double)this.chunks[i].length) {
         return null;
      }

      return this.chunks[i][(int)zOffset];
   }

   public int getBlockTint(BlockPos p_112937_, ColorResolver p_112938_) {
      return this.level.getBlockTint(p_112937_, p_112938_);
   }

   public int getMinBuildHeight() {
      return this.level.getMinBuildHeight();
   }

   public int getHeight() {
      return this.level.getHeight();
   }
}