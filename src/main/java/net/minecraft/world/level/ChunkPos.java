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

   public ChunkPos(long p_45582_, long p_45583_) {
      // Neighbor/range code routinely probes one chunk beyond the legal edge.
      // Clamp the value here so that probing cannot crash the tick thread; the
      // chunk source performs the separate validity check before it loads data.
      this.x = WorldBounds.clampChunk(p_45582_);
      this.z = WorldBounds.clampChunk(p_45583_);
   }

   public ChunkPos(BlockPos p_45587_) {
      this(SectionPos.blockToSectionCoord(p_45587_.getX()), SectionPos.blockToSectionCoord(p_45587_.getZ()));
   }

   public ChunkPos(long p_45585_) {
      this((int)p_45585_, (int)(p_45585_ >> 32));
   }

   public static ChunkPos minFromRegion(long p_220338_, long p_220339_) {
      return new ChunkPos(WorldBounds.regionToChunk(p_220338_, 0L), WorldBounds.regionToChunk(p_220339_, 0L));
   }

   public static ChunkPos maxFromRegion(long p_220341_, long p_220342_) {
      return new ChunkPos(WorldBounds.regionToChunk(p_220341_, 31L), WorldBounds.regionToChunk(p_220342_, 31L));
   }

   public static int getX(long p_45593_) {
      return (int)(p_45593_ & 4294967295L);
   }

   public static int getZ(long p_45603_) {
      return (int)(p_45603_ >>> 32 & 4294967295L);
   }

   public int hashCode() {
      return hash(this.x, this.z);
   }

   public static int hash(long p_220344_, long p_220345_) {
      int i = (int) (1664525 * p_220344_ + 1013904223);
      int j = (int) (1664525 * (p_220345_ ^ -559038737) + 1013904223);
      return i ^ j;
   }

   public boolean equals(Object p_45607_) {
      if (this == p_45607_) {
         return true;
      } else if (!(p_45607_ instanceof ChunkPos)) {
         return false;
      } else {
         ChunkPos chunkpos = (ChunkPos)p_45607_;
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

   public BlockPos getBlockAt(int p_151385_, int p_151386_, int p_151387_) {
      return new BlockPos(this.getBlockX(p_151385_), p_151386_, this.getBlockZ(p_151387_));
   }

   public long getBlockX(long p_151383_) {
      return SectionPos.sectionToBlockCoord(this.x, p_151383_);
   }

   public long getBlockZ(long p_151392_) {
      return SectionPos.sectionToBlockCoord(this.z, p_151392_);
   }

   public BlockPos getMiddleBlockPosition(int p_151395_) {
      return new BlockPos(this.getMiddleBlockX(), p_151395_, this.getMiddleBlockZ());
   }

   public String toString() {
      return "[" + this.x + ", " + this.z + "]";
   }

   public BlockPos getWorldPosition() {
      return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
   }

   public long getChessboardDistance(ChunkPos p_45595_) {
      return Math.max(distanceLong(this.x, p_45595_.x), distanceLong(this.z, p_45595_.z));
   }

   private static long distanceLong(long first, long second) {
      double distance = WorldBounds.distance(first, second);
      return distance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long)distance;
   }

   private static long distancePlusOne(long first, long second) {
      double distance = WorldBounds.distance(first, second);
      return distance >= (double)Long.MAX_VALUE ? Long.MAX_VALUE : (long)distance + 1L;
   }

   private static long safeProduct(long first, long second) {
      if (first <= 0L || second <= 0L) return 0L;
      return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
   }

   public static Stream<ChunkPos> rangeClosed(ChunkPos p_45597_, int p_45598_) {
      return rangeClosed(new ChunkPos(WorldBounds.addChunkOffset(p_45597_.x, -(long)p_45598_), WorldBounds.addChunkOffset(p_45597_.z, -(long)p_45598_)), new ChunkPos(WorldBounds.addChunkOffset(p_45597_.x, (long)p_45598_), WorldBounds.addChunkOffset(p_45597_.z, (long)p_45598_)));
   }

   public static Stream<ChunkPos> rangeClosed(final ChunkPos p_45600_, final ChunkPos p_45601_) {
      long i = distancePlusOne(p_45600_.x, p_45601_.x);
      long j = distancePlusOne(p_45600_.z, p_45601_.z);
      final int k = p_45600_.x < p_45601_.x ? 1 : -1;
      final int l = p_45600_.z < p_45601_.z ? 1 : -1;
      return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>(safeProduct(i, j), 64) {
         @Nullable
         private ChunkPos pos;

         public boolean tryAdvance(Consumer<? super ChunkPos> p_45630_) {
            if (this.pos == null) {
               this.pos = p_45600_;
            } else {
               long i1 = this.pos.x;
               long j1 = this.pos.z;
               if (i1 == p_45601_.x) {
                  if (j1 == p_45601_.z) {
                     return false;
                  }

                  this.pos = new ChunkPos(p_45600_.x, WorldBounds.addChunkOffset(j1, l));
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