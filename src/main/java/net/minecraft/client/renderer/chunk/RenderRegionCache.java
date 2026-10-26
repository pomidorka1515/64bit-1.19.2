package net.minecraft.client.renderer.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.Int128Map;
import net.minecraft.core.Int128Math;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    private final Int128Map<Int128Map<ChunkInfo>> chunkInfoCache = new Int128Map<>();

    private ChunkInfo getChunkInfo(Level level, Int128 chunkX, Int128 chunkZ) {
        Int128Map<ChunkInfo> column = this.chunkInfoCache.get(chunkX.hi(), chunkX.lo());
        if (column == null) {
            column = new Int128Map<>();
            this.chunkInfoCache.put(chunkX.hi(), chunkX.lo(), column);
        }
        ChunkInfo info = column.get(chunkZ.hi(), chunkZ.lo());
        if (info == null) {
            info = new ChunkInfo(level.getChunk(chunkX, chunkZ));
            column.put(chunkZ.hi(), chunkZ.lo(), info);
        }
        return info;
    }

    static int regionSize(Int128 min, Int128 max) {
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
        Int128 i = WorldBounds.addBlockOffset(p_200467_.getX(), -(long) p_200469_).shiftRight(4);
        Int128 j = WorldBounds.addBlockOffset(p_200467_.getZ(), -(long) p_200469_).shiftRight(4);
        Int128 k = WorldBounds.addBlockOffset(p_200468_.getX(), p_200469_).shiftRight(4);
        Int128 l = WorldBounds.addBlockOffset(p_200468_.getZ(), p_200469_).shiftRight(4);
        int xSize = regionSize(i, k);
        int zSize = regionSize(j, l);
        if ((long) xSize * zSize > WorldBounds.MAX_SAFE_LOCAL_SPAN) {
            throw new IllegalArgumentException("Render region snapshot is too large");
        }
        RenderRegionCache.ChunkInfo[][] arenderregioncache$chunkinfo = new RenderRegionCache.ChunkInfo[xSize][zSize];

        for (int xIndex = 0; xIndex < xSize; ++xIndex) {
            Int128 chunkX = WorldBounds.addChunkOffset(i, xIndex);
            for (int zIndex = 0; zIndex < zSize; ++zIndex) {
                Int128 chunkZ = WorldBounds.addChunkOffset(j, zIndex);
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

    private static boolean isAllEmpty(BlockPos p_200471_, BlockPos p_200472_, Int128 p_200473_, Int128 p_200474_, RenderRegionCache.ChunkInfo[][] p_200475_) {
        int firstX = RenderChunkRegion.localIndex(Int128Math.rightShiftHi(p_200471_.xHi(), 4), Int128Math.rightShiftLo(p_200471_.xHi(), p_200471_.xLo(), 4), p_200473_.hi(), p_200473_.lo(), p_200475_.length);
        int lastX = RenderChunkRegion.localIndex(Int128Math.rightShiftHi(p_200472_.xHi(), 4), Int128Math.rightShiftLo(p_200472_.xHi(), p_200472_.xLo(), 4), p_200473_.hi(), p_200473_.lo(), p_200475_.length);
        for (int x = firstX; x >= 0 && x <= lastX; ++x) {
            RenderRegionCache.ChunkInfo[] column = p_200475_[x];
            int firstZ = RenderChunkRegion.localIndex(Int128Math.rightShiftHi(p_200471_.zHi(), 4), Int128Math.rightShiftLo(p_200471_.zHi(), p_200471_.zLo(), 4), p_200474_.hi(), p_200474_.lo(), column.length);
            int lastZ = RenderChunkRegion.localIndex(Int128Math.rightShiftHi(p_200472_.zHi(), 4), Int128Math.rightShiftLo(p_200472_.zHi(), p_200472_.zLo(), 4), p_200474_.hi(), p_200474_.lo(), column.length);
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
