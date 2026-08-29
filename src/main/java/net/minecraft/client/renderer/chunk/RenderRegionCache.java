package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
   private final Object2ObjectMap<ChunkPos, RenderRegionCache.ChunkInfo> chunkInfoCache = new Object2ObjectOpenHashMap<>();

   @Nullable
   public RenderChunkRegion createRegion(Level p_200466_, BlockPos p_200467_, BlockPos p_200468_, int p_200469_) {
      long i = WorldBounds.clampChunk(WorldBounds.addBlockOffset(p_200467_.getX(), -(long)p_200469_) >> 4);
      long j = WorldBounds.clampChunk(WorldBounds.addBlockOffset(p_200467_.getZ(), -(long)p_200469_) >> 4);
      long k = WorldBounds.clampChunk(WorldBounds.addBlockOffset(p_200468_.getX(), p_200469_) >> 4);
      long l = WorldBounds.clampChunk(WorldBounds.addBlockOffset(p_200468_.getZ(), p_200469_) >> 4);
      int xSize = (int)WorldBounds.signedDifference(k, i) + 1;
      int zSize = (int)WorldBounds.signedDifference(l, j) + 1;
      RenderRegionCache.ChunkInfo[][] arenderregioncache$chunkinfo = new RenderRegionCache.ChunkInfo[xSize][zSize];

      for(int xIndex = 0; xIndex < xSize; ++xIndex) {
         long chunkX = WorldBounds.addChunkOffset(i, xIndex);
         for(int zIndex = 0; zIndex < zSize; ++zIndex) {
            long chunkZ = WorldBounds.addChunkOffset(j, zIndex);
            LevelChunk levelchunk = WorldBounds.isValidChunk(chunkX, chunkZ) ? p_200466_.getChunk(chunkX, chunkZ) : null;
            arenderregioncache$chunkinfo[xIndex][zIndex] = new RenderRegionCache.ChunkInfo(levelchunk);
         }
      }

      if (isAllEmpty(p_200467_, p_200468_, i, j, arenderregioncache$chunkinfo)) {
         return null;
      } else {
         RenderChunk[][] arenderchunk = new RenderChunk[xSize][zSize];

         for(int xIndex = 0; xIndex < xSize; ++xIndex) {
            for(int zIndex = 0; zIndex < zSize; ++zIndex) {
               RenderRegionCache.ChunkInfo chunkinfo = arenderregioncache$chunkinfo[xIndex][zIndex];
               arenderchunk[xIndex][zIndex] = chunkinfo == null || chunkinfo.chunk() == null ? null : chunkinfo.renderChunk();
            }
         }

         return new RenderChunkRegion(p_200466_, i, j, arenderchunk);
      }
   }

   private static boolean isAllEmpty(BlockPos p_200471_, BlockPos p_200472_, long p_200473_, long p_200474_, RenderRegionCache.ChunkInfo[][] p_200475_) {
      long minChunkX = p_200471_.getX() >> 4;
      long minChunkZ = p_200471_.getZ() >> 4;
      long maxChunkX = p_200472_.getX() >> 4;
      long maxChunkZ = p_200472_.getZ() >> 4;

      for(long chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
         for(long chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
            double xOffset = WorldBounds.signedDifference(chunkX, p_200473_);
            double zOffset = WorldBounds.signedDifference(chunkZ, p_200474_);
            if (xOffset < 0.0D || xOffset >= (double)p_200475_.length || zOffset < 0.0D) {
               continue;
            }

            RenderRegionCache.ChunkInfo[] column = p_200475_[(int)xOffset];
            if (column == null || zOffset >= (double)column.length) {
               continue;
            }

            RenderRegionCache.ChunkInfo info = column[(int)zOffset];
            if (info != null && info.chunk() != null && !info.chunk().isYSpaceEmpty(p_200471_.getY(), p_200472_.getY())) {
               return false;
            }
         }
      }

      return true;
   }

   @OnlyIn(Dist.CLIENT)
   static final class ChunkInfo {
      private final LevelChunk chunk;
      @Nullable
      private RenderChunk renderChunk;

      ChunkInfo(LevelChunk p_200479_) {
         this.chunk = p_200479_;
      }

      public LevelChunk chunk() {
         return this.chunk;
      }

      public RenderChunk renderChunk() {
         if (this.renderChunk == null) {
            this.renderChunk = new RenderChunk(this.chunk);
         }

         return this.renderChunk;
      }
   }
}