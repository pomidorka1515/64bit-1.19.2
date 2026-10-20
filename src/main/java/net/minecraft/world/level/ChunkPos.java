package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;

public class ChunkPos {
    /** Exactly four longs [xHi, xLo, zHi, zLo]; legacy two-long positions are rejected. */
    public static final Codec<ChunkPos> CODEC = Codec.LONG_STREAM.comapFlatMap(stream -> {
        long[] coordinates = stream.limit(5L).toArray();
        return coordinates.length == 4
            ? DataResult.success(new ChunkPos(Int128.of(coordinates[0], coordinates[1]), Int128.of(coordinates[2], coordinates[3])))
            : DataResult.error("ChunkPos requires exactly 4 longs [xHi, xLo, zHi, zLo]");
    }, value -> LongStream.of(value.xHi(), value.xLo(), value.zHi(), value.zLo()));

    /** The first and last chunk that can contain a signed 128-bit block coordinate. */
    public static final Int128 MIN_CHUNK_COORDINATE = WorldBounds.MIN_CHUNK;
    public static final Int128 MAX_CHUNK_COORDINATE = WorldBounds.MAX_CHUNK;

    public static final ChunkPos INVALID_CHUNK_POS = null;
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    public static final int REGION_SIZE = 32;
    public static final int REGION_MAX_INDEX = 31;
    private final Int128 x;
    private final Int128 z;

    public ChunkPos(long pX, long pY) {
        this(Int128.fromLong(pX), Int128.fromLong(pY));
    }

    public ChunkPos(Int128 pX, Int128 pZ) {
        // Neighbor/range probes saturate at the chunks containing the block-domain edges.
        this.x = WorldBounds.clampChunk(pX);
        this.z = WorldBounds.clampChunk(pZ);
    }

    public ChunkPos(BlockPos pPos) {
        this(pPos.getX().shiftRight(4), pPos.getZ().shiftRight(4));
    }

    public Int128 x() {
        return this.x;
    }

    public Int128 z() {
        return this.z;
    }

    public long xHi() {
        return this.x.hi();
    }

    public long xLo() {
        return this.x.lo();
    }

    public long zHi() {
        return this.z.hi();
    }

    public long zLo() {
        return this.z.lo();
    }

    public static ChunkPos minFromRegion(long p_220338_, long p_220339_) {
        return minFromRegion(Int128.fromLong(p_220338_), Int128.fromLong(p_220339_));
    }

    public static ChunkPos minFromRegion(Int128 p_220338_, Int128 p_220339_) {
        return new ChunkPos(WorldBounds.regionToChunk(p_220338_, 0L), WorldBounds.regionToChunk(p_220339_, 0L));
    }

    public static ChunkPos maxFromRegion(long p_220341_, long p_220342_) {
        return maxFromRegion(Int128.fromLong(p_220341_), Int128.fromLong(p_220342_));
    }

    public static ChunkPos maxFromRegion(Int128 p_220341_, Int128 p_220342_) {
        return new ChunkPos(WorldBounds.regionToChunk(p_220341_, 31L), WorldBounds.regionToChunk(p_220342_, 31L));
    }

    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(long p_220344_, long p_220345_) {
        return hash(Int128.fromLong(p_220344_), Int128.fromLong(p_220345_));
    }

    public static int hash(Int128 pX, Int128 pZ) {
        return Long.hashCode(31L * Int128.hash(pX.hi(), pX.lo()) + Int128.hash(pZ.hi(), pZ.lo()));
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkpos = (ChunkPos) pOther;
            return this.x.equals(chunkpos.x) && this.z.equals(chunkpos.z);
        }
    }

    public Int128 getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public Int128 getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public Int128 getMinBlockX() {
        return WorldBounds.chunkToBlock(this.x);
    }

    public Int128 getMinBlockZ() {
        return WorldBounds.chunkToBlock(this.z);
    }

    public Int128 getMaxBlockX() {
        return this.getBlockX(15);
    }

    public Int128 getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public Int128 getRegionX() {
        return this.x.shiftRight(5);
    }

    public Int128 getRegionZ() {
        return this.z.shiftRight(5);
    }

    public int getRegionLocalX() {
        return (int) (this.x.lo() & 31L);
    }

    public int getRegionLocalZ() {
        return (int) (this.z.lo() & 31L);
    }

    public BlockPos getBlockAt(int pXSection, int pY, int pZSection) {
        return new BlockPos(this.getBlockX(pXSection), pY, this.getBlockZ(pZSection));
    }

    public Int128 getBlockX(long pX) {
        return WorldBounds.addBlockOffset(this.getMinBlockX(), pX);
    }

    public Int128 getBlockZ(long pZ) {
        return WorldBounds.addBlockOffset(this.getMinBlockZ(), pZ);
    }

    public Int128 getBlockX(int pX) {
        return this.getBlockX((long) pX);
    }

    public Int128 getBlockZ(int pZ) {
        return this.getBlockZ((long) pZ);
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

    private static long distanceLong(Int128 first, Int128 second) {
        double distance = WorldBounds.distance(first, second);
        return distance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) distance;
    }

    private static long distancePlusOne(Int128 first, Int128 second) {
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
        final int k = pStart.x.compareTo(pEnd.x) < 0 ? 1 : -1;
        final int l = pStart.z.compareTo(pEnd.z) < 0 ? 1 : -1;
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>(safeProduct(i, j), 64) {
            @Nullable
            private ChunkPos pos;

            public boolean tryAdvance(Consumer<? super ChunkPos> p_45630_) {
                if (this.pos == null) {
                    this.pos = pStart;
                } else {
                    Int128 i1 = this.pos.x;
                    Int128 j1 = this.pos.z;
                    if (i1.equals(pEnd.x)) {
                        if (j1.equals(pEnd.z)) {
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
