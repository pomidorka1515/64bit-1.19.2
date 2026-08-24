package net.minecraft.server.level;

import com.google.common.base.Objects;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint<ChunkPos> {
   protected ChunkTracker(int p_140701_, int p_140702_, int p_140703_) {
      super(p_140701_, p_140702_, p_140703_);
   }

   protected boolean isSource(ChunkPos p_140705_) {
      return p_140705_ == ChunkPos.INVALID_CHUNK_POS;
   }

   protected void checkNeighborsAfterUpdate(ChunkPos p_140707_, int p_140708_, boolean p_140709_) {
      ChunkPos chunkpos = p_140707_;
      long i = chunkpos.x;
      long j = chunkpos.z;

      for(int k = -1; k <= 1; ++k) {
         for(int l = -1; l <= 1; ++l) {
            ChunkPos i1 = new ChunkPos(i + k, j + l);
            if (i1 != p_140707_) {
               this.checkNeighbor(p_140707_, i1, p_140708_, p_140709_);
            }
         }
      }

   }

   protected int getComputedLevel(ChunkPos p_140711_, ChunkPos p_140712_, int p_140713_) {
      int i = p_140713_;
      ChunkPos chunkpos = p_140711_;
      long j = chunkpos.x;
      long k = chunkpos.z;

      for(int l = -1; l <= 1; ++l) {
         for(int i1 = -1; i1 <= 1; ++i1) {
        	ChunkPos j1 = new ChunkPos(j + l, k + i1);
            if (Objects.equal(j1, p_140711_)) {
               j1 = ChunkPos.INVALID_CHUNK_POS;
            }

            if (!Objects.equal(j1, p_140712_)) {
               int k1 = this.computeLevelFromNeighbor(j1, p_140711_, this.getLevel(j1));
               if (i > k1) {
                  i = k1;
               }

               if (i == 0) {
                  return i;
               }
            }
         }
      }

      return i;
   }

   protected int computeLevelFromNeighbor(ChunkPos p_140720_, ChunkPos p_140721_, int p_140722_) {
      return p_140720_ == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(p_140721_) : p_140722_ + 1;
   }

   protected abstract int getLevelFromSource(ChunkPos p_140714_);

   public void update(ChunkPos p_140716_, int p_140717_, boolean p_140718_) {
      this.checkEdge(ChunkPos.INVALID_CHUNK_POS, p_140716_, p_140717_, p_140718_);
   }
}