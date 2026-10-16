package net.minecraft.world.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PathNavigationRegion implements BlockGetter, CollisionGetter {
    protected final long centerX;
    protected final long centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;

    public PathNavigationRegion(Level p_47164_, BlockPos p_47165_, BlockPos p_47166_) {
        this.level = p_47164_;
        this.centerX = SectionPos.blockToSectionCoord(p_47165_.getX());
        this.centerZ = SectionPos.blockToSectionCoord(p_47165_.getZ());
        long i = SectionPos.blockToSectionCoord(p_47166_.getX());
        long j = SectionPos.blockToSectionCoord(p_47166_.getZ());
        this.chunks = new ChunkAccess[Math.toIntExact(WorldBounds.inclusiveBlockSpan(this.centerX, i))][Math.toIntExact(WorldBounds.inclusiveBlockSpan(this.centerZ, j))];
        ChunkSource chunksource = p_47164_.getChunkSource();
        this.allEmpty = true;

        boolean moreX = this.centerX <= i;
        for (long k = this.centerX; moreX; moreX = k < i, k = WorldBounds.addChunkOffset(k, 1L)) {
            boolean moreZ = this.centerZ <= j;
            for (long l = this.centerZ; moreZ; moreZ = l < j, l = WorldBounds.addChunkOffset(l, 1L)) {
                this.chunks[(int) (k - this.centerX)][(int) (l - this.centerZ)] = chunksource.getChunkNow(k, l);
            }
        }

        moreX = this.centerX <= i;
        for (long i1 = this.centerX; moreX; moreX = i1 < i, i1 = WorldBounds.addChunkOffset(i1, 1L)) {
            boolean moreZ = this.centerZ <= j;
            for (long j1 = this.centerZ; moreZ; moreZ = j1 < j, j1 = WorldBounds.addChunkOffset(j1, 1L)) {
                ChunkAccess chunkaccess = this.chunks[(int) (i1 - this.centerX)][(int) (j1 - this.centerZ)];
                if (chunkaccess != null && !chunkaccess.isYSpaceEmpty(p_47165_.getY(), p_47166_.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    @Nullable
    private ChunkAccess getChunk(BlockPos p_47186_) {
        return this.getChunk(SectionPos.blockToSectionCoord(p_47186_.getX()), SectionPos.blockToSectionCoord(p_47186_.getZ()));
    }

    /**
    * The region is a snapshot of the chunks available when the search begins.
    * Missing snapshot entries have the same void-air/empty-fluid result as
    * the usual empty-chunk result, but constructing an EmptyLevelChunk for every
    * path-node probe allocates a complete 24-section LevelChunk. A path search
    * can inspect a missing edge chunk thousands of times, making those
    * throwaway chunks a severe server-tick spike.
    */
    @Nullable
    private ChunkAccess getChunk(long p_47168_, long p_47169_) {
        long i = WorldBounds.subtractBlockOffset(p_47168_, this.centerX);
        long j = WorldBounds.subtractBlockOffset(p_47169_, this.centerZ);
        return i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[(int) i].length
                ? this.chunks[(int) i][(int) j]
                : null;
    }

    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Nullable
    public BlockGetter getChunkForCollisions(long p_47173_, long p_47174_) {
        return this.getChunk(p_47173_, p_47174_);
    }

    public List<VoxelShape> getEntityCollisions(@Nullable Entity p_186557_, AABB p_186558_) {
        return List.of();
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos p_47180_) {
        ChunkAccess chunkaccess = this.getChunk(p_47180_);
        return chunkaccess == null ? null : chunkaccess.getBlockEntity(p_47180_);
    }

    public BlockState getBlockState(BlockPos p_47188_) {
        if (this.isOutsideBuildHeight(p_47188_)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(p_47188_);
            return chunkaccess == null ? Blocks.VOID_AIR.defaultBlockState() : chunkaccess.getBlockState(p_47188_);
        }
    }

    public FluidState getFluidState(BlockPos p_47171_) {
        if (this.isOutsideBuildHeight(p_47171_)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(p_47171_);
            return chunkaccess == null ? Fluids.EMPTY.defaultFluidState() : chunkaccess.getFluidState(p_47171_);
        }
    }

    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    public int getHeight() {
        return this.level.getHeight();
    }

    public ProfilerFiller getProfiler() {
        return this.level.getProfiler();
    }
}
