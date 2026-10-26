package net.minecraft.client.renderer;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
        return this.getChunkIndex(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    private int getChunkIndex(Int128 pX, int pY, Int128 pZ) {
        int x = floorMod(pX, this.chunkGridSizeX);
        int z = floorMod(pZ, this.chunkGridSizeZ);
        return (z * this.chunkGridSizeY + pY) * this.chunkGridSizeX + x;
    }

    /** Periodic local index, folding both halves without projecting the world coordinate. */
    static int floorMod(Int128 coordinate, int period) {
        long remainder = Math.floorMod(coordinate.hi(), (long) period);
        remainder = ((remainder << 32) + (coordinate.lo() >>> 32)) % period;
        return (int) (((remainder << 32) + (coordinate.lo() & 0xFFFFFFFFL)) % period);
    }

    protected void setViewDistance(int pRenderDistanceChunks) {
        int i = pRenderDistanceChunks * 2 + 1;
        this.chunkGridSizeX = i;
        this.chunkGridSizeY = this.level.getSectionsCount();
        this.chunkGridSizeZ = i;
    }

    static Int128 firstChunkInView(Int128 centerChunk, int gridSize) {
        long span = (long) gridSize - 1L;
        Int128 first = WorldBounds.addChunkOffset(centerChunk, -(long) gridSize / 2L);
        Int128 lastFirst = WorldBounds.MAX_CHUNK.subtract(Int128.fromLong(span));
        return first.compareTo(lastFirst) > 0 ? lastFirst : first;
    }

    public void repositionCamera(SectorVec3 exactCamera) {
        Int128 firstChunkX = firstChunkInView(exactCamera.blockX().shiftRight(4), this.chunkGridSizeX);
        Int128 firstChunkZ = firstChunkInView(exactCamera.blockZ().shiftRight(4), this.chunkGridSizeZ);

        for (int xIndex = 0; xIndex < this.chunkGridSizeX; ++xIndex) {
            Int128 chunkX = WorldBounds.addChunkOffset(firstChunkX, xIndex);
            Int128 blockX = WorldBounds.chunkToBlock(chunkX);

            for (int zIndex = 0; zIndex < this.chunkGridSizeZ; ++zIndex) {
                Int128 chunkZ = WorldBounds.addChunkOffset(firstChunkZ, zIndex);
                Int128 blockZ = WorldBounds.chunkToBlock(chunkZ);

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

    public void setDirty(Int128 pSectionX, int pSectionY, Int128 pSectionZ, boolean pReRenderOnMainThread) {
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
    boolean trySetDirtyRange(Int128 minX, long minY, Int128 minZ, Int128 maxX, long maxY, Int128 maxZ,
            boolean reRenderOnMainThread) {
        minY = Math.max(minY, this.level.getMinSection());
        maxY = Math.min(maxY, (long) this.level.getMinSection() + this.chunkGridSizeY - 1L);
        return forEachDirtySection(minX, minY, minZ, maxX, maxY, maxZ, this.chunks.length,
                (x, y, z) -> this.setDirty(x, y, z, reRenderOnMainThread));
    }

    static boolean forEachDirtySection(Int128 minX, long minY, Int128 minZ, Int128 maxX, long maxY, Int128 maxZ,
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
            Int128 x = minX.add(Int128.fromLong(xOffset));
            for (long zOffset = 0; zOffset <= depth; ++zOffset) {
                Int128 z = minZ.add(Int128.fromLong(zOffset));
                for (long y = minY; y <= maxY; ++y)
                    consumer.accept(x, (int) y, z);
            }
        }
        return true;
    }

    @FunctionalInterface
    interface DirtySectionConsumer {
        void accept(Int128 x, int y, Int128 z);
    }

    @Nullable
    protected ChunkRenderDispatcher.RenderChunk getRenderChunkAt(BlockPos pPos) {
        Int128 i = pPos.getX().shiftRight(4);
        int j = Mth.intFloorDiv(pPos.getY() - this.level.getMinBuildHeight(), 16);
        Int128 k = pPos.getZ().shiftRight(4);
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
