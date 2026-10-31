package net.minecraft.client.renderer;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
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
    private volatile GridIndex gridIndex;

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
        return ((int) Math.floorMod(pZ, this.chunkGridSizeZ) * this.chunkGridSizeY + pY)
                * this.chunkGridSizeX + (int) Math.floorMod(pX, this.chunkGridSizeX);
    }

    private int getChunkIndex(Int1088 pX, int pY, Int1088 pZ) {
        GridIndex grid = this.gridIndex;
        int x = grid == null ? floorMod(pX, this.chunkGridSizeX) : grid.x.sectionSlot(pX);
        int z = grid == null ? floorMod(pZ, this.chunkGridSizeZ) : grid.z.sectionSlot(pZ);
        return (z * this.chunkGridSizeY + pY) * this.chunkGridSizeX + x;
    }

    /** Periodic local index computed from the full coordinate. */
    static int floorMod(Int1088 coordinate, int period) {
        return Math.floorMod(coordinate.remainder(period), period);
    }

    /** Immutable view-window identity table, safe to read from rebuild workers. */
    static final class AxisIndex {
        final Int1088[] sections;
        final Int1088[] blocks;
        private final int firstSlot;

        AxisIndex(Int1088 first, int size) {
            this.sections = new Int1088[size];
            this.blocks = new Int1088[size];
            this.firstSlot = floorMod(first, size);
            for (int i = 0; i < size; ++i) {
                this.sections[i] = WorldBounds.addChunkOffset(first, i);
                this.blocks[i] = WorldBounds.chunkToBlock(this.sections[i]);
            }
        }

        int slot(int offset) {
            int slot = this.firstSlot + offset;
            return slot >= this.sections.length ? slot - this.sections.length : slot;
        }

        int sectionSlot(Int1088 section) {
            long offset = section.lowLimb() - this.sections[0].lowLimb();
            return offset >= 0 && offset < this.sections.length && this.sections[(int) offset].equals(section)
                    ? this.slot((int) offset) : floorMod(section, this.sections.length);
        }

        int blockOffset(BlockPos pos, boolean x) {
            long low = x ? pos.xLimb(0) : pos.zLimb(0);
            long high = x ? pos.xLimb(1) : pos.zLimb(1);
            // Low-word subtraction is only a candidate: the full-width match
            // rejects distant aliases, even if every low bit happens to agree.
            long offset = ((low >>> 4) | (high << 60)) - this.sections[0].lowLimb();
            return offset >= 0 && offset < this.sections.length
                    && SectionPos.matchesBlockCoordinate(this.sections[(int) offset], pos, x)
                    ? (int) offset : -1;
        }
    }

    private record GridIndex(AxisIndex x, AxisIndex z) {}

    protected void setViewDistance(int pRenderDistanceChunks) {
        this.gridIndex = null;
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
        GridIndex previous = this.gridIndex;
        if (previous != null && previous.x.sections[0].equals(firstChunkX)
                && previous.z.sections[0].equals(firstChunkZ)) return;
        AxisIndex xAxis = previous != null && previous.x.sections[0].equals(firstChunkX)
                ? previous.x : new AxisIndex(firstChunkX, this.chunkGridSizeX);
        AxisIndex zAxis = previous != null && previous.z.sections[0].equals(firstChunkZ)
                ? previous.z : new AxisIndex(firstChunkZ, this.chunkGridSizeZ);
        int minBuildHeight = this.level.getMinBuildHeight();

        for (int xIndex = 0; xIndex < this.chunkGridSizeX; ++xIndex) {
            Int1088 blockX = xAxis.blocks[xIndex];
            int xSlot = xAxis.slot(xIndex);

            for (int zIndex = 0; zIndex < this.chunkGridSizeZ; ++zIndex) {
                Int1088 blockZ = zAxis.blocks[zIndex];
                int columnIndex = zAxis.slot(zIndex) * this.chunkGridSizeY * this.chunkGridSizeX + xSlot;

                for (int yIndex = 0; yIndex < this.chunkGridSizeY; ++yIndex) {
                    int blockY = minBuildHeight + yIndex * 16;
                    int index = columnIndex + yIndex * this.chunkGridSizeX;
                    ChunkRenderDispatcher.RenderChunk chunk = this.chunks[index];
                    BlockPos origin = chunk.getOrigin();
                    if (!blockX.equals(origin.getX()) || blockY != origin.getY() || !blockZ.equals(origin.getZ())) {
                        chunk.setOrigin(blockX, blockY, blockZ);
                    }
                }
            }
        }
        this.gridIndex = new GridIndex(xAxis, zAxis);
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
        GridIndex grid = this.gridIndex;
        if (grid != null) {
            int y = Mth.intFloorDiv(pPos.getY() - this.level.getMinBuildHeight(), 16);
            if (y < 0 || y >= this.chunkGridSizeY) return null;
            int x = grid.x.blockOffset(pPos, true);
            int z = grid.z.blockOffset(pPos, false);
            if (x >= 0 && z >= 0) {
                ChunkRenderDispatcher.RenderChunk chunk = this.chunks[
                        (grid.z.slot(z) * this.chunkGridSizeY + y) * this.chunkGridSizeX + grid.x.slot(x)];
                BlockPos origin = chunk.getOrigin();
                // Origins can move while a worker holds an older immutable index.
                return origin.getX().equals(grid.x.blocks[x]) && origin.getZ().equals(grid.z.blocks[z])
                        && origin.getY() == this.level.getMinBuildHeight() + y * 16 ? chunk : null;
            }
            // During repositioning, newly moved chunks can already be available
            // outside this older published window. Preserve the exact fallback.
        }
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
