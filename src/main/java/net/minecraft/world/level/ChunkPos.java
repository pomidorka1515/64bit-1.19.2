package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;

public class ChunkPos {
    public static final Codec<ChunkPos> CODEC = Codec.LONG_STREAM.comapFlatMap(stream -> {
        long[] words = stream.limit(2 * Int1088.LIMBS + 1).toArray();
        return words.length == 2 * Int1088.LIMBS
                ? DataResult.success(new ChunkPos(Int1088.fromLimbs(words, 0, Int1088.LIMBS), Int1088.fromLimbs(words, Int1088.LIMBS, Int1088.LIMBS)))
                : DataResult.error("ChunkPos requires exactly 34 limbs");
    }, pos -> {
        long[] words = new long[2 * Int1088.LIMBS];
        pos.x().copyFixed(words, 0);
        pos.z().copyFixed(words, Int1088.LIMBS);
        return LongStream.of(words);
    });
    public static final Int1088 MIN_CHUNK_COORDINATE = WorldBounds.MIN_CHUNK;
    public static final Int1088 MAX_CHUNK_COORDINATE = WorldBounds.MAX_CHUNK;
    public static final ChunkPos INVALID_CHUNK_POS = null;
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    public static final int REGION_SIZE = 32;
    public static final int REGION_MAX_INDEX = 31;
    private final Int1088 x, z;
    // A null value selects an inline signed-long coordinate, not a truncated
    // mirror. Wide axes keep their complete immutable value instead.
    private final long narrowX, narrowZ;
    private final int wideHash;

    public ChunkPos(long x, long z) {
        this.x = this.z = null;
        this.narrowX = x;
        this.narrowZ = z;
        this.wideHash = 0;
    }
    public ChunkPos(Int1088 x, Int1088 z) {
        x = WorldBounds.clampChunk(x);
        z = WorldBounds.clampChunk(z);
        this.x = x.fitsLong() ? null : x;
        this.z = z.fitsLong() ? null : z;
        this.narrowX = this.x == null ? x.lowLimb() : 0;
        this.narrowZ = this.z == null ? z.lowLimb() : 0;
        this.wideHash = this.x == null && this.z == null ? 0 : hash(x, z);
    }
    public ChunkPos(BlockPos pos) { this(pos.getX().shiftRight(4), pos.getZ().shiftRight(4)); }
    public Int1088 x() { return this.x == null ? Int1088.fromLong(this.narrowX) : this.x; }
    public Int1088 z() { return this.z == null ? Int1088.fromLong(this.narrowZ) : this.z; }
    public long xLimb(int index) {
        java.util.Objects.checkIndex(index, Int1088.LIMBS);
        return this.x == null ? (index == 0 ? this.narrowX : this.narrowX >> 63) : this.x.limb(index);
    }
    public long zLimb(int index) {
        java.util.Objects.checkIndex(index, Int1088.LIMBS);
        return this.z == null ? (index == 0 ? this.narrowZ : this.narrowZ >> 63) : this.z.limb(index);
    }
    public int xLimbCount() { return this.x == null ? 1 : this.x.limbCount(); }
    public int zLimbCount() { return this.z == null ? 1 : this.z.limbCount(); }
    public boolean xFitsLong() { return this.x == null; }
    public boolean zFitsLong() { return this.z == null; }
    public long xLow() { return this.x == null ? this.narrowX : this.x.lowLimb(); }
    public long zLow() { return this.z == null ? this.narrowZ : this.z.lowLimb(); }
    public static ChunkPos minFromRegion(long x, long z) { return minFromRegion(Int1088.fromLong(x), Int1088.fromLong(z)); }
    public static ChunkPos minFromRegion(Int1088 x, Int1088 z) { return new ChunkPos(WorldBounds.regionToChunk(x, 0), WorldBounds.regionToChunk(z, 0)); }
    public static ChunkPos maxFromRegion(long x, long z) { return maxFromRegion(Int1088.fromLong(x), Int1088.fromLong(z)); }
    public static ChunkPos maxFromRegion(Int1088 x, Int1088 z) { return new ChunkPos(WorldBounds.regionToChunk(x, 31), WorldBounds.regionToChunk(z, 31)); }
    public static int hash(Int1088 x, Int1088 z) {
        return x.fitsLong() && z.fitsLong() ? hash(x.lowLimb(), z.lowLimb())
                : x.hashXZ() ^ Integer.rotateLeft(z.hashXZ(), 16);
    }
    public static int hash(long x, long z) {
        // Both complete coordinates fit a long; their upper limbs are determined
        // by the sign bits. Mix all 64 bits, not just the local chunk indices.
        int hx = 1664525 * (int) x + 1013904223 + Integer.rotateLeft((int) (x >>> 32), 7);
        int hz = 1664525 * ((int) z ^ -559038737) + 1013904223 + Integer.rotateLeft((int) (z >>> 32), 7);
        return hx ^ Integer.rotateLeft(hz, 16);
    }
    @Override public int hashCode() { return this.x == null && this.z == null ? hash(this.narrowX, this.narrowZ) : this.wideHash; }
    public boolean equals(Int1088 x, Int1088 z) {
        if (x == null || z == null) return false;
        return (this.x == null ? x.fitsLong() && this.narrowX == x.lowLimb() : this.x.equals(x))
                && (this.z == null ? z.fitsLong() && this.narrowZ == z.lowLimb() : this.z.equals(z));
    }
    public boolean equals(long x, long z) { return this.x == null && this.z == null && this.narrowX == x && this.narrowZ == z; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChunkPos)) return false;
        ChunkPos pos = (ChunkPos) other;
        return this.narrowX == pos.narrowX && this.narrowZ == pos.narrowZ
                && java.util.Objects.equals(this.x, pos.x) && java.util.Objects.equals(this.z, pos.z);
    }
    public Int1088 getMiddleBlockX() { return getBlockX(8); }
    public Int1088 getMiddleBlockZ() { return getBlockZ(8); }
    public Int1088 getMinBlockX() { return WorldBounds.chunkToBlock(x()); }
    public Int1088 getMinBlockZ() { return WorldBounds.chunkToBlock(z()); }
    public Int1088 getMaxBlockX() { return getBlockX(15); }
    public Int1088 getMaxBlockZ() { return getBlockZ(15); }
    public Int1088 getRegionX() { return this.x == null ? Int1088.fromLong(this.narrowX >> 5) : this.x.shiftRight(5); }
    public Int1088 getRegionZ() { return this.z == null ? Int1088.fromLong(this.narrowZ >> 5) : this.z.shiftRight(5); }
    public int getRegionLocalX() { return (int) (xLow() & 31); }
    public int getRegionLocalZ() { return (int) (zLow() & 31); }
    public BlockPos getBlockAt(int x, int y, int z) { return new BlockPos(getBlockX(x), y, getBlockZ(z)); }
    public Int1088 getBlockX(long x) { return WorldBounds.addBlockOffset(getMinBlockX(), x); }
    public Int1088 getBlockZ(long z) { return WorldBounds.addBlockOffset(getMinBlockZ(), z); }
    public Int1088 getBlockX(int x) { return getBlockX((long) x); }
    public Int1088 getBlockZ(int z) { return getBlockZ((long) z); }
    public BlockPos getMiddleBlockPosition(int y) { return new BlockPos(getMiddleBlockX(), y, getMiddleBlockZ()); }
    public BlockPos getWorldPosition() { return new BlockPos(getMinBlockX(), 0, getMinBlockZ()); }
    @Override public String toString() { return "[" + x() + ", " + z() + "]"; }
    public long getChessboardDistance(ChunkPos other) { return Math.max(Int1088Math.distance(x(), other.x()), Int1088Math.distance(z(), other.z())); }
    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(new ChunkPos(WorldBounds.addChunkOffset(center.x(), -(long) radius), WorldBounds.addChunkOffset(center.z(), -(long) radius)),
                 new ChunkPos(WorldBounds.addChunkOffset(center.x(), radius), WorldBounds.addChunkOffset(center.z(), radius)));
    }
    public static Stream<ChunkPos> rangeClosed(ChunkPos start, ChunkPos end) {
        RangeCursor cursor = rangeClosedCursor(start, end);
        long size = Int1088Math.safeProduct(Int1088Math.distancePlusOne(start.x(), end.x()), Int1088Math.distancePlusOne(start.z(), end.z()));
        int characteristics = java.util.Spliterator.ORDERED | (size == Long.MAX_VALUE ? 0 : java.util.Spliterator.SIZED);
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>(size, characteristics) {
            public boolean tryAdvance(Consumer<? super ChunkPos> consumer) {
                if (!cursor.advance()) return false;
                consumer.accept(cursor.toChunkPos());
                return true;
            }
        }, false);
    }
    public static RangeCursor rangeClosedCursor(ChunkPos center, int radius) { return new RangeCursor().reset(center, radius); }
    public static RangeCursor rangeClosedCursor(ChunkPos start, ChunkPos end) { return new RangeCursor().reset(start, end); }
    public static final class RangeCursor {
        private Int1088 startX, endX, endZ;
        private final long[] x = new long[Int1088.LIMBS];
        private final long[] z = new long[Int1088.LIMBS];
        private int stepX, stepZ;
        private boolean first, finished = true;
        public RangeCursor reset(ChunkPos center, int radius) {
            return reset(new ChunkPos(WorldBounds.addChunkOffset(center.x(), -(long) radius), WorldBounds.addChunkOffset(center.z(), -(long) radius)),
                     new ChunkPos(WorldBounds.addChunkOffset(center.x(), radius), WorldBounds.addChunkOffset(center.z(), radius)));
        }
        public RangeCursor reset(ChunkPos start, ChunkPos end) {
            this.startX = start.x(); start.x().copyFixed(this.x, 0); start.z().copyFixed(this.z, 0); this.endX = end.x(); this.endZ = end.z();
            this.stepX = start.x().compareTo(end.x()) < 0 ? 1 : -1;
            this.stepZ = start.z().compareTo(end.z()) < 0 ? 1 : -1;
            this.first = true; this.finished = false; return this;
        }
        public boolean advance() {
            if (this.finished) return false;
            if (this.first) { this.first = false; return true; }
            if (same(this.x, this.endX)) {
                if (same(this.z, this.endZ)) { this.finished = true; return false; }
                this.startX.copyFixed(this.x, 0); step(this.z, this.stepZ);
            } else step(this.x, this.stepX);
            return true;
        }
        private static boolean same(long[] words, Int1088 value) {
            for (int i = 0; i < Int1088.LIMBS; ++i) if (words[i] != value.limb(i)) return false;
            return true;
        }
        private static void step(long[] words, int direction) {
            for (int i = 0; i < words.length; ++i) {
                long previous = words[i];
                words[i] += direction;
                if (direction > 0 ? words[i] != 0 : previous != 0) break;
            }
        }
        public Int1088 x() { return Int1088.fromFixedLimbs(this.x); }
        public Int1088 z() { return Int1088.fromFixedLimbs(this.z); }
        public long xLimb(int index) { return this.x[index]; }
        public long zLimb(int index) { return this.z[index]; }
        public Int1088 getRegionX() { return x().shiftRight(5); }
        public Int1088 getRegionZ() { return z().shiftRight(5); }
        public long getChessboardDistance(ChunkPos other) { return Math.max(Int1088Math.distance(x(), other.x()), Int1088Math.distance(z(), other.z())); }
        public ChunkPos toChunkPos() { return new ChunkPos(x(), z()); }
    }
}
