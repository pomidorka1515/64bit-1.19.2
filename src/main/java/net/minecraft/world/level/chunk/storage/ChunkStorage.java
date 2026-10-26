package net.minecraft.world.level.chunk.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public class ChunkStorage implements AutoCloseable {
    private final IOWorker worker;

    public ChunkStorage(Path pRegionFolder, boolean pSync) {
        this.worker = new IOWorker(pRegionFolder, pSync, "chunk");
    }

    public boolean isOldChunkAround(ChunkPos p_223452_, int p_223453_) {
        return this.worker.isOldChunkAround(p_223452_, p_223453_);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos pChunkPos) {
        return this.worker.loadAsync(pChunkPos);
    }

    public void write(ChunkPos pChunkPos, CompoundTag pChunkData) {
        this.worker.store(pChunkPos, pChunkData);
    }

    public void flushWorker() {
        this.worker.synchronize(true).join();
    }

    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }
}
