package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.core.Int128Math;
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
        }
    }

    public void onStatusChange(ChunkPos pChunkPosition, @Nullable ChunkStatus pNewStatus) {
        if (this.started) {
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
        long dx = (long) pX - this.radius;
        long dz = (long) pZ - this.radius;
        long xLo = this.spawnPos.xLo() + dx;
        long xHi = this.spawnPos.xHi() + (dx >> 63) + Int128Math.addCarry(this.spawnPos.xLo(), dx, xLo);
        long zLo = this.spawnPos.zLo() + dz;
        long zHi = this.spawnPos.zHi() + (dz >> 63) + Int128Math.addCarry(this.spawnPos.zLo(), dz, zLo);
        // Match ChunkPos's chunk-domain saturation, without materializing offset keys.
        // Both chunk limits end on a complete low-limb interval, so high limbs suffice.
        if (xHi < WorldBounds.MIN_CHUNK.hi()) {
            xHi = WorldBounds.MIN_CHUNK.hi();
            xLo = WorldBounds.MIN_CHUNK.lo();
        } else if (xHi > WorldBounds.MAX_CHUNK.hi()) {
            xHi = WorldBounds.MAX_CHUNK.hi();
            xLo = WorldBounds.MAX_CHUNK.lo();
        }
        if (zHi < WorldBounds.MIN_CHUNK.hi()) {
            zHi = WorldBounds.MIN_CHUNK.hi();
            zLo = WorldBounds.MIN_CHUNK.lo();
        } else if (zHi > WorldBounds.MAX_CHUNK.hi()) {
            zHi = WorldBounds.MAX_CHUNK.hi();
            zLo = WorldBounds.MAX_CHUNK.lo();
        }
        return this.statuses.get(xHi, xLo, zHi, zLo);
    }
}
