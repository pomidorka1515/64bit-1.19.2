package net.minecraft.world.level.chunk;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.Int128;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

public abstract class ChunkSource implements LightChunkGetter, AutoCloseable {
    @Nullable
    public LevelChunk getChunk(long pChunkX, long pChunkZ, boolean pLoad) {
        return (LevelChunk) this.getChunk(pChunkX, pChunkZ, ChunkStatus.FULL, pLoad);
    }

    @Nullable
    public LevelChunk getChunkNow(long pChunkX, long pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, false);
    }

    @Nullable
    public LevelChunk getChunkNow(Int128 pChunkX, Int128 pChunkZ) {
        return (LevelChunk) this.getChunk(pChunkX, pChunkZ, ChunkStatus.FULL, false);
    }

    @Nullable
    public BlockGetter getChunkForLighting(long pChunkX, long pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.EMPTY, false);
    }

    public boolean hasChunk(long pChunkX, long pChunkZ) {
        return this.hasChunk(Int128.fromLong(pChunkX), Int128.fromLong(pChunkZ));
    }

    public boolean hasChunk(Int128 pChunkX, Int128 pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ, ChunkStatus.FULL, false) != null;
    }

    @Nullable
    public ChunkAccess getChunk(long pChunkX, long pChunkZ, ChunkStatus pRequiredStatus, boolean pLoad) {
        return this.getChunk(Int128.fromLong(pChunkX), Int128.fromLong(pChunkZ), pRequiredStatus, pLoad);
    }

    @Nullable
    public abstract ChunkAccess getChunk(Int128 pChunkX, Int128 pChunkZ, ChunkStatus pRequiredStatus, boolean pLoad);

    public abstract void tick(BooleanSupplier pHasTimeLeft, boolean pTickChunks);

    public abstract String gatherStats();

    public abstract int getLoadedChunksCount();

    public void close() throws IOException {
    }

    public abstract LevelLightEngine getLightEngine();

    public void setSpawnSettings(boolean pHostile, boolean pPeaceful) {
    }

    public void updateChunkForced(ChunkPos pPos, boolean pAdd) {
    }
}
