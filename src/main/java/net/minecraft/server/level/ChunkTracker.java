package net.minecraft.server.level;

import com.google.common.base.Objects;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint<ChunkPos> {
    protected ChunkTracker(int pFirstQueuedLevel, int pWidth, int pHeight) {
        super(pFirstQueuedLevel, pWidth, pHeight);
    }

    protected boolean isSource(ChunkPos pPos) {
        return pPos == ChunkPos.INVALID_CHUNK_POS;
    }

    protected void checkNeighborsAfterUpdate(ChunkPos pPos, int pLevel, boolean pIsDecreasing) {
        ChunkPos chunkpos = pPos;
        long i = chunkpos.x;
        long j = chunkpos.z;

        for (int k = -1; k <= 1; ++k) {
            for (int l = -1; l <= 1; ++l) {
                Long x = WorldBounds.tryAddChunkOffset(i, k);
                Long z = WorldBounds.tryAddChunkOffset(j, l);
                if (x != null && z != null) {
                    ChunkPos i1 = new ChunkPos(x, z);
                    if (!i1.equals(pPos)) {
                        this.checkNeighbor(pPos, i1, pLevel, pIsDecreasing);
                    }
                }
            }
        }
    }

    protected int getComputedLevel(ChunkPos pPos, ChunkPos pExcludedSourcePos, int pLevel) {
        int i = pLevel;
        ChunkPos chunkpos = pPos;
        long j = chunkpos.x;
        long k = chunkpos.z;

        for (int l = -1; l <= 1; ++l) {
            for (int i1 = -1; i1 <= 1; ++i1) {
                Long x = WorldBounds.tryAddChunkOffset(j, l);
                Long z = WorldBounds.tryAddChunkOffset(k, i1);
                if (x == null || z == null) {
                    continue;
                }
                ChunkPos j1 = new ChunkPos(x, z);
                if (Objects.equal(j1, pPos)) {
                    j1 = ChunkPos.INVALID_CHUNK_POS;
                }

                if (!Objects.equal(j1, pExcludedSourcePos)) {
                    int k1 = this.computeLevelFromNeighbor(j1, pPos, this.getLevel(j1));
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

    protected int computeLevelFromNeighbor(ChunkPos pStartPos, ChunkPos pEndPos, int pStartLevel) {
        return pStartPos == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(pEndPos) : pStartLevel + 1;
    }

    protected abstract int getLevelFromSource(ChunkPos pPos);

    public void update(ChunkPos pPos, int pLevel, boolean pIsDecreasing) {
        this.checkEdge(ChunkPos.INVALID_CHUNK_POS, pPos, pLevel, pIsDecreasing);
    }
}
