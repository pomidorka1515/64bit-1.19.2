package net.minecraft.world.level.chunk;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

public abstract class ChunkSource implements LightChunkGetter, AutoCloseable {
   @Nullable
   public LevelChunk getChunk(long p_62228_, long p_62229_, boolean p_62230_) {
      return (LevelChunk)this.getChunk(p_62228_, p_62229_, ChunkStatus.FULL, p_62230_);
   }

   @Nullable
   public LevelChunk getChunkNow(long p_62221_, long p_62222_) {
      return this.getChunk(p_62221_, p_62222_, false);
   }

   @Nullable
   public BlockGetter getChunkForLighting(long p_62241_, long p_62242_) {
      return this.getChunk(p_62241_, p_62242_, ChunkStatus.EMPTY, false);
   }

   public boolean hasChunk(long p_62238_, long p_62239_) {
      return this.getChunk(p_62238_, p_62239_, ChunkStatus.FULL, false) != null;
   }

   @Nullable
   public abstract ChunkAccess getChunk(long p_62223_, long p_62224_, ChunkStatus p_62225_, boolean p_62226_);

   public abstract void tick(BooleanSupplier p_202162_, boolean p_202163_);

   public abstract String gatherStats();

   public abstract int getLoadedChunksCount();

   public void close() throws IOException {
   }

   public abstract LevelLightEngine getLightEngine();

   public void setSpawnSettings(boolean p_62236_, boolean p_62237_) {
   }

   public void updateChunkForced(ChunkPos p_62233_, boolean p_62234_) {
   }
}