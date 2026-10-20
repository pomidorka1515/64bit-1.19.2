package net.minecraft.world.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
    protected final Int128 centerX;
    protected final Int128 centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;

    public PathNavigationRegion(Level pLevel, BlockPos pCenterPos, BlockPos pOffsetPos) {
        this.level = pLevel;
        this.centerX = SectionPos.blockToSectionCoord(pCenterPos.getX());
        this.centerZ = SectionPos.blockToSectionCoord(pCenterPos.getZ());
        Int128 i = SectionPos.blockToSectionCoord(pOffsetPos.getX());
        Int128 j = SectionPos.blockToSectionCoord(pOffsetPos.getZ());
        int width = snapshotSize(this.centerX, i);
        int depth = snapshotSize(this.centerZ, j);
        if ((long) width * depth > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Pathfinding snapshot exceeds local volume budget");
        this.chunks = new ChunkAccess[width][depth];
        ChunkSource chunksource = pLevel.getChunkSource();
        this.allEmpty = true;

        for (int x = 0; x < width; ++x) {
            Int128 chunkX = WorldBounds.addChunkOffset(this.centerX, x);
            for (int z = 0; z < depth; ++z) {
                Int128 chunkZ = WorldBounds.addChunkOffset(this.centerZ, z);
                this.chunks[x][z] = chunksource.getChunkNow(chunkX, chunkZ);
            }
        }

        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < depth; ++z) {
                ChunkAccess chunkaccess = this.chunks[x][z];
                if (chunkaccess != null && !chunkaccess.isYSpaceEmpty(pCenterPos.getY(), pOffsetPos.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    /** Bounds allocation by local span, independently of either coordinate's high word. */
    private static int snapshotSize(Int128 first, Int128 last) {
        if (first.compareTo(last) > 0)
            throw new IllegalArgumentException("Reversed pathfinding snapshot range");
        long span = WorldBounds.signedDifferenceAsLong(last, first);
        if (span > (WorldBounds.MAX_SAFE_LOCAL_SPAN >> 4))
            throw new IllegalArgumentException("Pathfinding snapshot exceeds safe local span");
        return Math.toIntExact(span + 1L);
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
    private ChunkAccess getChunk(Int128 pX, Int128 pZ) {
        long i = WorldBounds.signedDifferenceAsLong(pX, this.centerX);
        long j = WorldBounds.signedDifferenceAsLong(pZ, this.centerZ);
        return i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[(int) i].length
                ? this.chunks[(int) i][(int) j]
                : null;
    }

    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Nullable
    public BlockGetter getChunkForCollisions(long pChunkX, long pChunkZ) {
        return this.getChunk(Int128.fromLong(pChunkX), Int128.fromLong(pChunkZ));
    }

    @Nullable
    public BlockGetter getChunkForCollisions(Int128 pChunkX, Int128 pChunkZ) {
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
