package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
   private final Object2ObjectMap<ChunkPos, RenderRegionCache.ChunkInfo> chunkInfoCache = new Object2ObjectOpenHashMap<>();

   @Nullable
   public RenderChunkRegion createRegion(Level p_200466_, BlockPos p_200467_, BlockPos p_200468_, int p_200469_) {
      long i = SectionPos.blockToSectionCoord(p_200467_.getX() - p_200469_);
      long j = SectionPos.blockToSectionCoord(p_200467_.getZ() - p_200469_);
      long k = SectionPos.blockToSectionCoord(p_200468_.getX() + p_200469_);
      long l = SectionPos.blockToSectionCoord(p_200468_.getZ() + p_200469_);
      RenderRegionCache.ChunkInfo[][] arenderregioncache$chunkinfo = new RenderRegionCache.ChunkInfo[(int) (k - i + 1)][(int) (l - j + 1)];

      for(long i1 = i; i1 <= k; ++i1) {
         for(long j1 = j; j1 <= l; ++j1) {
            arenderregioncache$chunkinfo[(int) (i1 - i)][(int) (j1 - j)] = this.chunkInfoCache.computeIfAbsent(new ChunkPos(i1, j1), (ChunkPos p_200464_) -> {
               return new RenderRegionCache.ChunkInfo(p_200466_.getChunk(p_200464_.x, p_200464_.z));
            });
         }
      }

      if (isAllEmpty(p_200467_, p_200468_, i, j, arenderregioncache$chunkinfo)) {
         return null;
      } else {
         RenderChunk[][] arenderchunk = new RenderChunk[(int) (k - i + 1)][(int) (l - j + 1)];

         for(long l1 = i; l1 <= k; ++l1) {
            for(long k1 = j; k1 <= l; ++k1) {
               arenderchunk[(int) (l1 - i)][(int) (k1 - j)] = arenderregioncache$chunkinfo[(int) (l1 - i)][(int) (k1 - j)].renderChunk();
            }
         }

         return new RenderChunkRegion(p_200466_, i, j, arenderchunk);
      }
   }

   private static boolean isAllEmpty(BlockPos p_200471_, BlockPos p_200472_, long p_200473_, long p_200474_, RenderRegionCache.ChunkInfo[][] p_200475_) {
	  long i = SectionPos.blockToSectionCoord(p_200471_.getX());
	  long j = SectionPos.blockToSectionCoord(p_200471_.getZ());
	  long k = SectionPos.blockToSectionCoord(p_200472_.getX());
	  long l = SectionPos.blockToSectionCoord(p_200472_.getZ());

      for(long i1 = i; i1 <= k; ++i1) {
         for(long j1 = j; j1 <= l; ++j1) {
            LevelChunk levelchunk = p_200475_[(int) (i1 - p_200473_)][(int) (j1 - p_200474_)].chunk();
            if (!levelchunk.isYSpaceEmpty(p_200471_.getY(), p_200472_.getY())) {
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