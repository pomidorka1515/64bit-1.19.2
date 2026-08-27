package net.minecraft.client.renderer;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ViewArea {
   protected final LevelRenderer levelRenderer;
   protected final Level level;
   protected int chunkGridSizeY;
   protected int chunkGridSizeX;
   protected int chunkGridSizeZ;
   public ChunkRenderDispatcher.RenderChunk[] chunks;

   public ViewArea(ChunkRenderDispatcher p_110845_, Level p_110846_, int p_110847_, LevelRenderer p_110848_) {
      this.levelRenderer = p_110848_;
      this.level = p_110846_;
      this.setViewDistance(p_110847_);
      this.createChunks(p_110845_);
   }

   protected void createChunks(ChunkRenderDispatcher p_110865_) {
      if (!Minecraft.getInstance().isSameThread()) {
         throw new IllegalStateException("createChunks called from wrong thread: " + Thread.currentThread().getName());
      } else {
         int i = this.chunkGridSizeX * this.chunkGridSizeY * this.chunkGridSizeZ;
         this.chunks = new ChunkRenderDispatcher.RenderChunk[i];

         for(int j = 0; j < this.chunkGridSizeX; ++j) {
            for(int k = 0; k < this.chunkGridSizeY; ++k) {
               for(int l = 0; l < this.chunkGridSizeZ; ++l) {
                  int i1 = this.getChunkIndex(j, k, l);
                  this.chunks[i1] = p_110865_.new RenderChunk(i1, j * 16, k * 16, l * 16);
               }
            }
         }

      }
   }

   public void releaseAllBuffers() {
      for(ChunkRenderDispatcher.RenderChunk chunkrenderdispatcher$renderchunk : this.chunks) {
         chunkrenderdispatcher$renderchunk.releaseBuffers();
      }

   }

   private int getChunkIndex(long p_110856_, int p_110857_, long p_110858_) {
      long x = Math.floorMod(p_110856_, (long)this.chunkGridSizeX);
      long z = Math.floorMod(p_110858_, (long)this.chunkGridSizeZ);
      long index = (z * (long)this.chunkGridSizeY + (long)p_110857_) * (long)this.chunkGridSizeX + x;
      return (int)index;
   }

   protected void setViewDistance(int p_110854_) {
      int i = p_110854_ * 2 + 1;
      this.chunkGridSizeX = i;
      this.chunkGridSizeY = this.level.getSectionsCount();
      this.chunkGridSizeZ = i;
   }

   public void repositionCamera(SectorVec3 exactCamera) {
      long camBlockX = exactCamera.blockX();
      long camBlockZ = exactCamera.blockZ();
      long l = (long)this.chunkGridSizeX * 16L;
      long i1 = camBlockX - 8L - l / 2L;

      for(int k = 0; k < this.chunkGridSizeX; ++k) {
         long j1 = i1 + Math.floorMod((long)k * 16L - i1, l);

         for(int k1 = 0; k1 < this.chunkGridSizeZ; ++k1) {
            long l1 = (long)this.chunkGridSizeZ * 16L;
            long i2 = camBlockZ - 8L - l1 / 2L;
            long j2 = i2 + Math.floorMod((long)k1 * 16L - i2, l1);

            for(int k2 = 0; k2 < this.chunkGridSizeY; ++k2) {
               int l2 = this.level.getMinBuildHeight() + k2 * 16;
               ChunkRenderDispatcher.RenderChunk chunk = this.chunks[this.getChunkIndex((long)k, k2, (long)k1)];
               BlockPos blockpos = chunk.getOrigin();
               if (j1 != blockpos.getX() || l2 != blockpos.getY() || j2 != blockpos.getZ()) {
                  chunk.setOrigin(j1, l2, j2);
               }
            }
         }
      }
   }

   public void repositionCamera(double x, double z) {
      this.repositionCamera(SectorVec3.fromApproximate(x, 0.0D, z));
   }

   public void setDirty(long p_110860_, int p_110861_, long p_110862_, boolean p_110863_) {
      long i = Math.floorMod(p_110860_, (long)this.chunkGridSizeX);
      int j = Math.floorMod(p_110861_ - this.level.getMinSection(), this.chunkGridSizeY);
      long k = Math.floorMod(p_110862_, (long)this.chunkGridSizeZ);
      ChunkRenderDispatcher.RenderChunk chunkrenderdispatcher$renderchunk = this.chunks[this.getChunkIndex(i, j, k)];
      chunkrenderdispatcher$renderchunk.setDirty(p_110863_);
   }

   @Nullable
   protected ChunkRenderDispatcher.RenderChunk getRenderChunkAt(BlockPos p_110867_) {
      long i = Math.floorDiv((long)p_110867_.getX(), 16L);
      int j = Mth.intFloorDiv(p_110867_.getY() - this.level.getMinBuildHeight(), 16);
      long k = Math.floorDiv((long)p_110867_.getZ(), 16L);
      if (j >= 0 && j < this.chunkGridSizeY) {
         i = Math.floorMod(i, (long)this.chunkGridSizeX);
         k = Math.floorMod(k, (long)this.chunkGridSizeZ);
         return this.chunks[this.getChunkIndex(i, j, k)];
      } else {
         return null;
      }
   }
}