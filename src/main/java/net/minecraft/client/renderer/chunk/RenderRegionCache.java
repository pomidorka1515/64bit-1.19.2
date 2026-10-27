package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Map;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    private final Int1088Map<Int1088Map<ChunkInfo>> chunkInfoCache = new Int1088Map<>();

    private ChunkInfo getChunkInfo(Level level, Int1088 chunkX, Int1088 chunkZ) {
        Int1088Map<ChunkInfo> column = this.chunkInfoCache.get(chunkX);
        if (column == null) {
            column = new Int1088Map<>();
            this.chunkInfoCache.put(chunkX, column);
        }
        ChunkInfo info = column.get(chunkZ);
        if (info == null) {
            info = new ChunkInfo(level.getChunk(chunkX, chunkZ));
            column.put(chunkZ, info);
        }
        return info;
    }

    static int regionSize(Int1088 min, Int1088 max) {
        long difference = WorldBounds.signedDifferenceAsLong(max, min);
        if (difference < 0 || difference >= WorldBounds.MAX_SAFE_LOCAL_SPAN) {
            throw new IllegalArgumentException("Render region must be a bounded ascending local range");
        }
        return (int) difference + 1;
    }

    @Nullable
    public RenderChunkRegion createRegion(Level p_200466_, BlockPos p_200467_, BlockPos p_200468_, int p_200469_) {
        if (p_200469_ < 0 || p_200467_.getX().compareTo(p_200468_.getX()) > 0
                || p_200467_.getZ().compareTo(p_200468_.getZ()) > 0) {
            throw new IllegalArgumentException("Invalid render region bounds");
        }
        Int1088 i = WorldBounds.addBlockOffset(p_200467_.getX(), -(long) p_200469_).shiftRight(4);
        Int1088 j = WorldBounds.addBlockOffset(p_200467_.getZ(), -(long) p_200469_).shiftRight(4);
        Int1088 k = WorldBounds.addBlockOffset(p_200468_.getX(), p_200469_).shiftRight(4);
        Int1088 l = WorldBounds.addBlockOffset(p_200468_.getZ(), p_200469_).shiftRight(4);
        int xSize = regionSize(i, k);
        int zSize = regionSize(j, l);
        if ((long) xSize * zSize > WorldBounds.MAX_SAFE_LOCAL_SPAN) {
            throw new IllegalArgumentException("Render region snapshot is too large");
        }
        RenderRegionCache.ChunkInfo[][] arenderregioncache$chunkinfo = new RenderRegionCache.ChunkInfo[xSize][zSize];

        for (int xIndex = 0; xIndex < xSize; ++xIndex) {
            Int1088 chunkX = WorldBounds.addChunkOffset(i, xIndex);
            for (int zIndex = 0; zIndex < zSize; ++zIndex) {
                Int1088 chunkZ = WorldBounds.addChunkOffset(j, zIndex);
                arenderregioncache$chunkinfo[xIndex][zIndex] = this.getChunkInfo(p_200466_, chunkX, chunkZ);
            }
        }

        if (isAllEmpty(p_200467_, p_200468_, i, j, arenderregioncache$chunkinfo)) {
            return null;
        } else {
            RenderChunk[][] arenderchunk = new RenderChunk[xSize][zSize];

            for (int xIndex = 0; xIndex < xSize; ++xIndex) {
                for (int zIndex = 0; zIndex < zSize; ++zIndex) {
                    RenderRegionCache.ChunkInfo chunkinfo = arenderregioncache$chunkinfo[xIndex][zIndex];
                    arenderchunk[xIndex][zIndex] = chunkinfo == null || chunkinfo.chunk() == null ? null : chunkinfo.renderChunk();
                }
            }

            return new RenderChunkRegion(p_200466_, i, j, arenderchunk);
        }
    }

    private static boolean isAllEmpty(BlockPos p_200471_, BlockPos p_200472_, Int1088 p_200473_, Int1088 p_200474_, RenderRegionCache.ChunkInfo[][] p_200475_) {
        int firstX = RenderChunkRegion.localIndex(p_200471_.getX().shiftRight(4), p_200473_, p_200475_.length);
        int lastX = RenderChunkRegion.localIndex(p_200472_.getX().shiftRight(4), p_200473_, p_200475_.length);
        for (int x = firstX; x >= 0 && x <= lastX; ++x) {
            RenderRegionCache.ChunkInfo[] column = p_200475_[x];
            int firstZ = RenderChunkRegion.localIndex(p_200471_.getZ().shiftRight(4), p_200474_, column.length);
            int lastZ = RenderChunkRegion.localIndex(p_200472_.getZ().shiftRight(4), p_200474_, column.length);
            for (int z = firstZ; z >= 0 && z <= lastZ; ++z) {
                RenderRegionCache.ChunkInfo info = column[z];
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

        ChunkInfo(LevelChunk pChunk) {
            this.chunk = pChunk;
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
