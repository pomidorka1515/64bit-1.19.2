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

    public PathNavigationRegion(Level pLevel, BlockPos pCenterPos, BlockPos pOffsetPos) {
        this.level = pLevel;
        this.centerX = SectionPos.blockToSectionCoord(pCenterPos.getX());
        this.centerZ = SectionPos.blockToSectionCoord(pCenterPos.getZ());
        long i = SectionPos.blockToSectionCoord(pOffsetPos.getX());
        long j = SectionPos.blockToSectionCoord(pOffsetPos.getZ());
        this.chunks = new ChunkAccess[Math.toIntExact(WorldBounds.inclusiveBlockSpan(this.centerX, i))][Math.toIntExact(WorldBounds.inclusiveBlockSpan(this.centerZ, j))];
        ChunkSource chunksource = pLevel.getChunkSource();
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
                if (chunkaccess != null && !chunkaccess.isYSpaceEmpty(pCenterPos.getY(), pOffsetPos.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    @Nullable
    private ChunkAccess getChunk(BlockPos pPos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pPos.getX()), SectionPos.blockToSectionCoord(pPos.getZ()));
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
    private ChunkAccess getChunk(long pX, long pZ) {
        long i = WorldBounds.subtractBlockOffset(pX, this.centerX);
        long j = WorldBounds.subtractBlockOffset(pZ, this.centerZ);
        return i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[(int) i].length
                ? this.chunks[(int) i][(int) j]
                : null;
    }

    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Nullable
    public BlockGetter getChunkForCollisions(long pChunkX, long pChunkZ) {
        return this.getChunk(pChunkX, pChunkZ);
    }

    public List<VoxelShape> getEntityCollisions(@Nullable Entity pEntity, AABB pCollisionBox) {
        return List.of();
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pPos) {
        ChunkAccess chunkaccess = this.getChunk(pPos);
        return chunkaccess == null ? null : chunkaccess.getBlockEntity(pPos);
    }

    public BlockState getBlockState(BlockPos pPos) {
        if (this.isOutsideBuildHeight(pPos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(pPos);
            return chunkaccess == null ? Blocks.VOID_AIR.defaultBlockState() : chunkaccess.getBlockState(pPos);
        }
    }

    public FluidState getFluidState(BlockPos pPos) {
        if (this.isOutsideBuildHeight(pPos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            ChunkAccess chunkaccess = this.getChunk(pPos);
            return chunkaccess == null ? Fluids.EMPTY.defaultFluidState() : chunkaccess.getFluidState(pPos);
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
