package net.minecraft.world.level;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class ChunkPos {
    /** The first and last chunk that can contain a signed-long block coordinate. */
    public static final long MIN_CHUNK_COORDINATE = WorldBounds.MIN_CHUNK;
    public static final long MAX_CHUNK_COORDINATE = WorldBounds.MAX_CHUNK;

    public static final ChunkPos INVALID_CHUNK_POS = null;
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    public static final int REGION_SIZE = 32;
    public static final int REGION_MAX_INDEX = 31;
    public final long x;
    public final long z;

    public ChunkPos(long pX, long pY) {
        // Neighbor/range code routinely probes one chunk beyond the legal edge.
        // Clamp the value here so that probing cannot crash the tick thread; the
        // chunk source performs the separate validity check before it loads data.
        this.x = WorldBounds.clampChunk(pX);
        this.z = WorldBounds.clampChunk(pY);
    }

    public ChunkPos(BlockPos pPos) {
        this(SectionPos.blockToSectionCoord(pPos.getX()), SectionPos.blockToSectionCoord(pPos.getZ()));
    }

    public ChunkPos(long pPackedPos) {
        this((int) pPackedPos, (int) (pPackedPos >> 32));
    }

    public static ChunkPos minFromRegion(long p_220338_, long p_220339_) {
        return new ChunkPos(WorldBounds.regionToChunk(p_220338_, 0L), WorldBounds.regionToChunk(p_220339_, 0L));
    }

    public static ChunkPos maxFromRegion(long p_220341_, long p_220342_) {
        return new ChunkPos(WorldBounds.regionToChunk(p_220341_, 31L), WorldBounds.regionToChunk(p_220342_, 31L));
    }

    public static int getX(long pChunkAsLong) {
        return (int) (pChunkAsLong & 4294967295L);
    }

    public static int getZ(long pChunkAsLong) {
        return (int) (pChunkAsLong >>> 32 & 4294967295L);
    }

    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(long p_220344_, long p_220345_) {
        int i = (int) (1664525 * p_220344_ + 1013904223);
        int j = (int) (1664525 * (p_220345_ ^ -559038737) + 1013904223);
        return i ^ j;
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkpos = (ChunkPos) pOther;
            return this.x == chunkpos.x && this.z == chunkpos.z;
        }
    }

    public long getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public long getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public long getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public long getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public long getMaxBlockX() {
        return this.getBlockX(15);
    }

    public long getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public long getRegionX() {
        return this.x >> 5;
    }

    public long getRegionZ() {
        return this.z >> 5;
    }

    public int getRegionLocalX() {
        return (int) (this.x & 31);
    }

    public int getRegionLocalZ() {
        return (int) (this.z & 31);
    }

    public BlockPos getBlockAt(int pXSection, int pY, int pZSection) {
        return new BlockPos(this.getBlockX(pXSection), pY, this.getBlockZ(pZSection));
    }

    public long getBlockX(long pX) {
        return SectionPos.sectionToBlockCoord(this.x, pX);
    }

    public long getBlockZ(long pZ) {
        return SectionPos.sectionToBlockCoord(this.z, pZ);
    }

    public BlockPos getMiddleBlockPosition(int pY) {
        return new BlockPos(this.getMiddleBlockX(), pY, this.getMiddleBlockZ());
    }

    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public long getChessboardDistance(ChunkPos pChunkPos) {
        return Math.max(distanceLong(this.x, pChunkPos.x), distanceLong(this.z, pChunkPos.z));
    }

    private static long distanceLong(long first, long second) {
        double distance = WorldBounds.distance(first, second);
        return distance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) distance;
    }

    private static long distancePlusOne(long first, long second) {
        double distance = WorldBounds.distance(first, second);
        return distance >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : (long) distance + 1L;
    }

    private static long safeProduct(long first, long second) {
        if (first <= 0L || second <= 0L)
            return 0L;
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos pCenter, int pRadius) {
        return rangeClosed(new ChunkPos(WorldBounds.addChunkOffset(pCenter.x, -(long) pRadius), WorldBounds.addChunkOffset(pCenter.z, -(long) pRadius)), new ChunkPos(WorldBounds.addChunkOffset(pCenter.x, (long) pRadius), WorldBounds.addChunkOffset(pCenter.z, (long) pRadius)));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos pStart, final ChunkPos pEnd) {
        long i = distancePlusOne(pStart.x, pEnd.x);
        long j = distancePlusOne(pStart.z, pEnd.z);
        final int k = pStart.x < pEnd.x ? 1 : -1;
        final int l = pStart.z < pEnd.z ? 1 : -1;
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>(safeProduct(i, j), 64) {
            @Nullable
            private ChunkPos pos;

         public boolean tryAdvance(Consumer<? super ChunkPos> p_45630_) {
             if (this.pos == null) {
                 this.pos = pStart;
             } else {
                 long i1 = this.pos.x;
                 long j1 = this.pos.z;
                 if (i1 == pEnd.x) {
                     if (j1 == pEnd.z) {
                         return false;
                     }

                     this.pos = new ChunkPos(pStart.x, WorldBounds.addChunkOffset(j1, l));
                 } else {
                     this.pos = new ChunkPos(WorldBounds.addChunkOffset(i1, k), j1);
                 }
             }

             p_45630_.accept(this.pos);
             return true;
         }
        }, false);
    }
}
