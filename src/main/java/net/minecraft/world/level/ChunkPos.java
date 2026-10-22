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
import net.minecraft.core.Int128Math;

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
    private final long xHi, xLo, zHi, zLo;

    public ChunkPos(long pX, long pY) {
        // Every signed-long chunk coordinate lies inside the signed-128 block domain.
        this.xHi = pX >> 63;
        this.xLo = pX;
        this.zHi = pY >> 63;
        this.zLo = pY;
    }

    public ChunkPos(Int128 pX, Int128 pZ) {
        // Neighbor/range probes saturate at the chunks containing the block-domain edges.
        Int128 x = WorldBounds.clampChunk(pX);
        Int128 z = WorldBounds.clampChunk(pZ);
        this.xHi = x.hi();
        this.xLo = x.lo();
        this.zHi = z.hi();
        this.zLo = z.lo();
    }

    public ChunkPos(BlockPos pPos) {
        this(pPos.xHi() >> 4, Int128Math.rightShiftLo(pPos.xHi(), pPos.xLo(), 4),
                pPos.zHi() >> 4, Int128Math.rightShiftLo(pPos.zHi(), pPos.zLo(), 4));
    }

    // Internal callers have already proved that both coordinates are in the chunk domain.
    private ChunkPos(long xHi, long xLo, long zHi, long zLo) {
        this.xHi = xHi;
        this.xLo = xLo;
        this.zHi = zHi;
        this.zLo = zLo;
    }

    /** Materializes a value object; use the limb accessors for hot comparisons/arithmetic. */
    public Int128 x() {
        return Int128.of(this.xHi, this.xLo);
    }

    public Int128 z() {
        return Int128.of(this.zHi, this.zLo);
    }

    public long xHi() {
        return this.xHi;
    }

    public long xLo() {
        return this.xLo;
    }

    public long zHi() {
        return this.zHi;
    }

    public long zLo() {
        return this.zLo;
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
        return hash(this.xHi, this.xLo, this.zHi, this.zLo);
    }

    public static int hash(long p_220344_, long p_220345_) {
        return hash(p_220344_ >> 63, p_220344_, p_220345_ >> 63, p_220345_);
    }

    public static int hash(Int128 pX, Int128 pZ) {
        return hash(pX.hi(), pX.lo(), pZ.hi(), pZ.lo());
    }

    private static int hash(long xHi, long xLo, long zHi, long zLo) {
        // Fold every limb, not just the low 32 bits used by the old world key.
        // Independent odd multipliers keep adjacent X/Z windows well distributed.
        int x = 1664525 * (int) xLo + 1013904223
                + Integer.rotateLeft((int) (xLo >>> 32), 7);
        int z = 1664525 * ((int) zLo ^ -559038737) + 1013904223
                + Integer.rotateLeft((int) (zLo >>> 32), 7);
        return x ^ Integer.rotateLeft(z, 16) ^ Integer.rotateLeft(Long.hashCode(xHi) * 0x9e3779b9, 11)
                ^ Integer.rotateLeft(Long.hashCode(zHi) * 0x85ebca6b, 23);
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkpos = (ChunkPos) pOther;
            return this.xHi == chunkpos.xHi && this.xLo == chunkpos.xLo
                    && this.zHi == chunkpos.zHi && this.zLo == chunkpos.zLo;
        }
    }

    public Int128 getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public Int128 getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public Int128 getMinBlockX() {
        return Int128.of(Int128Math.leftShiftHi(this.xHi, this.xLo, 4), this.xLo << 4);
    }

    public Int128 getMinBlockZ() {
        return Int128.of(Int128Math.leftShiftHi(this.zHi, this.zLo, 4), this.zLo << 4);
    }

    public Int128 getMaxBlockX() {
        return this.getBlockX(15);
    }

    public Int128 getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public Int128 getRegionX() {
        return Int128.of(this.xHi >> 5, Int128Math.rightShiftLo(this.xHi, this.xLo, 5));
    }

    public Int128 getRegionZ() {
        return Int128.of(this.zHi >> 5, Int128Math.rightShiftLo(this.zHi, this.zLo, 5));
    }

    public int getRegionLocalX() {
        return (int) (this.xLo & 31L);
    }

    public int getRegionLocalZ() {
        return (int) (this.zLo & 31L);
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
        return "[" + this.x() + ", " + this.z() + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(Int128Math.leftShiftHi(this.xHi, this.xLo, 4), this.xLo << 4, 0,
                Int128Math.leftShiftHi(this.zHi, this.zLo, 4), this.zLo << 4);
    }

    public long getChessboardDistance(ChunkPos pChunkPos) {
        return Math.max(distanceLong(this.xHi, this.xLo, pChunkPos.xHi, pChunkPos.xLo),
                distanceLong(this.zHi, this.zLo, pChunkPos.zHi, pChunkPos.zLo));
    }

    private static long distanceLong(long aHi, long aLo, long bHi, long bLo) {
        double distance = Int128Math.distance(aHi, aLo, bHi, bLo);
        return distance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) distance;
    }

    private static long distancePlusOne(long aHi, long aLo, long bHi, long bLo) {
        double distance = Int128Math.distance(aHi, aLo, bHi, bLo);
        return distance >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : (long) distance + 1L;
    }

    private static long safeProduct(long first, long second) {
        if (first <= 0L || second <= 0L)
            return 0L;
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos pCenter, int pRadius) {
        return rangeClosed(new ChunkPos(WorldBounds.addChunkOffset(pCenter.x(), -(long) pRadius), WorldBounds.addChunkOffset(pCenter.z(), -(long) pRadius)), new ChunkPos(WorldBounds.addChunkOffset(pCenter.x(), (long) pRadius), WorldBounds.addChunkOffset(pCenter.z(), (long) pRadius)));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos pStart, final ChunkPos pEnd) {
        long i = distancePlusOne(pStart.xHi, pStart.xLo, pEnd.xHi, pEnd.xLo);
        long j = distancePlusOne(pStart.zHi, pStart.zLo, pEnd.zHi, pEnd.zLo);
        final int k = Int128Math.compare(pStart.xHi, pStart.xLo, pEnd.xHi, pEnd.xLo) < 0 ? 1 : -1;
        final int l = Int128Math.compare(pStart.zHi, pStart.zLo, pEnd.zHi, pEnd.zLo) < 0 ? 1 : -1;
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>(safeProduct(i, j), 64) {
            @Nullable
            private ChunkPos pos;

            public boolean tryAdvance(Consumer<? super ChunkPos> p_45630_) {
                if (this.pos == null) {
                    this.pos = pStart;
                } else {
                    if (this.pos.xHi == pEnd.xHi && this.pos.xLo == pEnd.xLo) {
                        if (this.pos.zHi == pEnd.zHi && this.pos.zLo == pEnd.zLo) {
                            return false;
                        }

                        long zLo = this.pos.zLo + l;
                        long zHi = this.pos.zHi + (l > 0 ? Int128Math.addCarry(this.pos.zLo, l, zLo)
                                : -Int128Math.subtractBorrow(this.pos.zLo, 1L));
                        this.pos = new ChunkPos(pStart.xHi, pStart.xLo, zHi, zLo);
                    } else {
                        long xLo = this.pos.xLo + k;
                        long xHi = this.pos.xHi + (k > 0 ? Int128Math.addCarry(this.pos.xLo, k, xLo)
                                : -Int128Math.subtractBorrow(this.pos.xLo, 1L));
                        this.pos = new ChunkPos(xHi, xLo, this.pos.zHi, this.pos.zLo);
                    }
                }

                p_45630_.accept(this.pos);
                return true;
            }
        }, false);
    }
}
