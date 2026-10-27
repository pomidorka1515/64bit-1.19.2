package net.minecraft.client.renderer;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ViewArea {
    protected final LevelRenderer levelRenderer;
    protected final Level level;
    protected int chunkGridSizeY;
    protected int chunkGridSizeX;
    protected int chunkGridSizeZ;
    public ChunkRenderDispatcher.RenderChunk[] chunks;

    public ViewArea(ChunkRenderDispatcher pChunkRenderDispatcher, Level pLevel, int pViewDistance, LevelRenderer pLevelRenderer) {
        this.levelRenderer = pLevelRenderer;
        this.level = pLevel;
        this.setViewDistance(pViewDistance);
        this.createChunks(pChunkRenderDispatcher);
    }

    protected void createChunks(ChunkRenderDispatcher pRenderChunkFactory) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createChunks called from wrong thread: " + Thread.currentThread().getName());
        } else {
            int i = this.chunkGridSizeX * this.chunkGridSizeY * this.chunkGridSizeZ;
            this.chunks = new ChunkRenderDispatcher.RenderChunk[i];

            for (int j = 0; j < this.chunkGridSizeX; ++j) {
                for (int k = 0; k < this.chunkGridSizeY; ++k) {
                    for (int l = 0; l < this.chunkGridSizeZ; ++l) {
                        int i1 = this.getChunkIndex(j, k, l);
                        this.chunks[i1] = pRenderChunkFactory.new RenderChunk(i1, (long) j * 16L, k * 16, (long) l * 16L);
                    }
                }
            }
        }
    }

    public void releaseAllBuffers() {
        for (ChunkRenderDispatcher.RenderChunk chunkrenderdispatcher$renderchunk : this.chunks) {
            chunkrenderdispatcher$renderchunk.releaseBuffers();
        }
    }

    private int getChunkIndex(long pX, int pY, long pZ) {
        return this.getChunkIndex(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    private int getChunkIndex(Int1088 pX, int pY, Int1088 pZ) {
        int x = floorMod(pX, this.chunkGridSizeX);
        int z = floorMod(pZ, this.chunkGridSizeZ);
        return (z * this.chunkGridSizeY + pY) * this.chunkGridSizeX + x;
    }

    /** Periodic local index computed from the full coordinate. */
    static int floorMod(Int1088 coordinate, int period) {
        return Math.floorMod(coordinate.remainder(period), period);
    }

    protected void setViewDistance(int pRenderDistanceChunks) {
        int i = pRenderDistanceChunks * 2 + 1;
        this.chunkGridSizeX = i;
        this.chunkGridSizeY = this.level.getSectionsCount();
        this.chunkGridSizeZ = i;
    }

    static Int1088 firstChunkInView(Int1088 centerChunk, int gridSize) {
        long span = (long) gridSize - 1L;
        Int1088 first = WorldBounds.addChunkOffset(centerChunk, -(long) gridSize / 2L);
        Int1088 lastFirst = WorldBounds.MAX_CHUNK.subtract(Int1088.fromLong(span));
        return first.compareTo(lastFirst) > 0 ? lastFirst : first;
    }

    public void repositionCamera(SectorVec3 exactCamera) {
        Int1088 firstChunkX = firstChunkInView(exactCamera.blockX().shiftRight(4), this.chunkGridSizeX);
        Int1088 firstChunkZ = firstChunkInView(exactCamera.blockZ().shiftRight(4), this.chunkGridSizeZ);

        for (int xIndex = 0; xIndex < this.chunkGridSizeX; ++xIndex) {
            Int1088 chunkX = WorldBounds.addChunkOffset(firstChunkX, xIndex);
            Int1088 blockX = WorldBounds.chunkToBlock(chunkX);

            for (int zIndex = 0; zIndex < this.chunkGridSizeZ; ++zIndex) {
                Int1088 chunkZ = WorldBounds.addChunkOffset(firstChunkZ, zIndex);
                Int1088 blockZ = WorldBounds.chunkToBlock(chunkZ);

                for (int yIndex = 0; yIndex < this.chunkGridSizeY; ++yIndex) {
                    int blockY = this.level.getMinBuildHeight() + yIndex * 16;
                    ChunkRenderDispatcher.RenderChunk chunk = this.chunks[this.getChunkIndex(chunkX, yIndex, chunkZ)];
                    BlockPos origin = chunk.getOrigin();
                    if (!blockX.equals(origin.getX()) || blockY != origin.getY() || !blockZ.equals(origin.getZ())) {
                        chunk.setOrigin(blockX, blockY, blockZ);
                    }
                }
            }
        }
    }

    public void repositionCamera(double x, double z) {
        this.repositionCamera(SectorVec3.fromApproximate(x, 0.0D, z));
    }

    public void setDirty(Int1088 pSectionX, int pSectionY, Int1088 pSectionZ, boolean pReRenderOnMainThread) {
        int j = pSectionY - this.level.getMinSection();
        if (j < 0 || j >= this.chunkGridSizeY || !WorldBounds.isValidChunk(pSectionX, pSectionZ))
            return;

        ChunkRenderDispatcher.RenderChunk chunkrenderdispatcher$renderchunk = this.chunks[this.getChunkIndex(pSectionX, j, pSectionZ)];
        BlockPos origin = chunkrenderdispatcher$renderchunk.getOrigin();
        if (origin.getX().equals(WorldBounds.chunkToBlock(pSectionX)) && origin.getY() == this.level.getMinBuildHeight() + j * 16
                && origin.getZ().equals(WorldBounds.chunkToBlock(pSectionZ))) {
            chunkrenderdispatcher$renderchunk.setDirty(pReRenderOnMainThread);
        }
    }

    /** Uses exact grid lookups only when they cost no more than scanning the view. */
    boolean trySetDirtyRange(Int1088 minX, long minY, Int1088 minZ, Int1088 maxX, long maxY, Int1088 maxZ,
            boolean reRenderOnMainThread) {
        minY = Math.max(minY, this.level.getMinSection());
        maxY = Math.min(maxY, (long) this.level.getMinSection() + this.chunkGridSizeY - 1L);
        return forEachDirtySection(minX, minY, minZ, maxX, maxY, maxZ, this.chunks.length,
                (x, y, z) -> this.setDirty(x, y, z, reRenderOnMainThread));
    }

    static boolean forEachDirtySection(Int1088 minX, long minY, Int1088 minZ, Int1088 maxX, long maxY, Int1088 maxZ,
            int maxLookups, DirtySectionConsumer consumer) {
        if (minY > maxY)
            return true;

        long width = WorldBounds.signedDifferenceAsLong(maxX, minX);
        long depth = WorldBounds.signedDifferenceAsLong(maxZ, minZ);
        long budget = maxLookups / (maxY - minY + 1L);
        // Compare before adding or multiplying: a world-sized range must not wrap.
        if (width < 0L || depth < 0L || width >= budget || depth >= budget
                || width + 1L > budget / (depth + 1L))
            return false;

        for (long xOffset = 0; xOffset <= width; ++xOffset) {
            Int1088 x = minX.addSaturated(Int1088.fromLong(xOffset));
            for (long zOffset = 0; zOffset <= depth; ++zOffset) {
                Int1088 z = minZ.addSaturated(Int1088.fromLong(zOffset));
                for (long y = minY; y <= maxY; ++y)
                    consumer.accept(x, (int) y, z);
            }
        }
        return true;
    }

    @FunctionalInterface
    interface DirtySectionConsumer {
        void accept(Int1088 x, int y, Int1088 z);
    }

    @Nullable
    protected ChunkRenderDispatcher.RenderChunk getRenderChunkAt(BlockPos pPos) {
        Int1088 i = pPos.getX().shiftRight(4);
        int j = Mth.intFloorDiv(pPos.getY() - this.level.getMinBuildHeight(), 16);
        Int1088 k = pPos.getZ().shiftRight(4);
        if (j < 0 || j >= this.chunkGridSizeY || !WorldBounds.isValidChunk(i, k))
            return null;

        ChunkRenderDispatcher.RenderChunk chunkrenderdispatcher$renderchunk = this.chunks[this.getChunkIndex(i, j, k)];
        BlockPos origin = chunkrenderdispatcher$renderchunk.getOrigin();
        return origin.getX().equals(WorldBounds.chunkToBlock(i)) && origin.getY() == this.level.getMinBuildHeight() + j * 16
                        && origin.getZ().equals(WorldBounds.chunkToBlock(k))
                ? chunkrenderdispatcher$renderchunk
                : null;
    }
}
