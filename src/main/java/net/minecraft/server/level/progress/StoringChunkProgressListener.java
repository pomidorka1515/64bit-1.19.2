package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.ChunkStatus;

public class StoringChunkProgressListener implements ChunkProgressListener {
    private final LoggerChunkProgressListener delegate;
    private final ChunkPosMap<ChunkStatus> statuses;
    private ChunkPos spawnPos = new ChunkPos(0, 0);
    private final int fullDiameter;
    private final int radius;
    private final int diameter;
    private boolean started;
    private boolean cachedStatusValid;
    private int cachedX, cachedZ;
    @Nullable private ChunkStatus cachedStatus;

    public StoringChunkProgressListener(int pRadius) {
        this.delegate = new LoggerChunkProgressListener(pRadius);
        this.fullDiameter = pRadius * 2 + 1;
        this.radius = pRadius + ChunkStatus.maxDistance();
        this.diameter = this.radius * 2 + 1;
        this.statuses = new ChunkPosMap<>();
    }

    public void updateSpawnPos(ChunkPos pCenter) {
        if (this.started) {
            this.delegate.updateSpawnPos(pCenter);
            this.spawnPos = pCenter;
            this.cachedStatusValid = false;
        }
    }

    public void onStatusChange(ChunkPos pChunkPosition, @Nullable ChunkStatus pNewStatus) {
        if (this.started) {
            this.cachedStatusValid = false;
            this.delegate.onStatusChange(pChunkPosition, pNewStatus);
            if (pNewStatus == null) {
                this.statuses.remove(pChunkPosition);
            } else {
                this.statuses.put(pChunkPosition, pNewStatus);
            }
        }
    }

    public void start() {
        this.started = true;
        this.statuses.clear();
        this.cachedStatusValid = false;
        this.delegate.start();
    }

    public void stop() {
        this.started = false;
        this.delegate.stop();
    }

    public int getFullDiameter() {
        return this.fullDiameter;
    }

    public int getDiameter() {
        return this.diameter;
    }

    public int getProgress() {
        return this.delegate.getProgress();
    }

    @Nullable
    public ChunkStatus getStatus(int pX, int pZ) {
        if (this.cachedStatusValid && this.cachedX == pX && this.cachedZ == pZ) {
            return this.cachedStatus;
        }
        long dx = (long) pX - this.radius;
        long dz = (long) pZ - this.radius;
        ChunkStatus status = this.statuses.get(WorldBounds.addChunkOffset(this.spawnPos.x(), dx),
                WorldBounds.addChunkOffset(this.spawnPos.z(), dz));
        this.cachedX = pX;
        this.cachedZ = pZ;
        this.cachedStatus = status;
        this.cachedStatusValid = true;
        return status;
    }
}
