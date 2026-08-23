package net.minecraft.core;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;

public class SectionPos extends Vec3i {
   public static final int SECTION_BITS = 4;
   public static final int SECTION_SIZE = 16;
   public static final int SECTION_MASK = 15;
   public static final int SECTION_HALF_SIZE = 8;
   public static final int SECTION_MAX_INDEX = 15;
   private static final int PACKED_X_LENGTH = 22;
   private static final int PACKED_Y_LENGTH = 20;
   private static final int PACKED_Z_LENGTH = 22;
   private static final long PACKED_X_MASK = 4194303L;
   private static final long PACKED_Y_MASK = 1048575L;
   private static final long PACKED_Z_MASK = 4194303L;
   private static final int Y_OFFSET = 0;
   private static final int Z_OFFSET = 20;
   private static final int X_OFFSET = 42;
   private static final int RELATIVE_X_SHIFT = 8;
   private static final int RELATIVE_Y_SHIFT = 0;
   private static final int RELATIVE_Z_SHIFT = 4;

   SectionPos(long p_123162_, int p_123163_, long p_123164_) {
      super(p_123162_, p_123163_, p_123164_);
   }

   public static SectionPos of(long p_123174_, int p_123175_, long p_123176_) {
      return new SectionPos(p_123174_, p_123175_, p_123176_);
   }

   public static SectionPos of(BlockPos p_123200_) {
      return new SectionPos(blockToSectionCoord(p_123200_.getX()), (int) blockToSectionCoord(p_123200_.getY()), blockToSectionCoord(p_123200_.getZ()));
   }

   public static SectionPos of(ChunkPos p_123197_, int p_123198_) {
      return new SectionPos(p_123197_.x, p_123198_, p_123197_.z);
   }

   public static SectionPos of(EntityAccess p_235862_) {
      return of(p_235862_.blockPosition());
   }

   public static SectionPos of(Position p_235864_) {
      return new SectionPos(blockToSectionCoord(p_235864_.x()), blockToSectionCoord(p_235864_.y()), blockToSectionCoord(p_235864_.z()));
   }

   public static SectionPos bottomOf(ChunkAccess p_175563_) {
      return of(p_175563_.getPos(), p_175563_.getMinSection());
   }

   public static long posToSectionCoord(double p_175553_) {
      return blockToSectionCoord(Mth.floor(p_175553_));
   }

   public static long blockToSectionCoord(long p_123172_) {
      return p_123172_ >> 4L;
   }
   
   public static int blockToSectionCoord(int p_123172_) {
      return p_123172_ >> 4;
   }

   public static int blockToSectionCoord(double p_235866_) {
      return Mth.floor(p_235866_) >> 4;
   }

   public static int sectionRelative(long p_123208_) {
      return (int) (p_123208_ & 15);
   }
   
   public static int sectionRelative(int p_123208_) {
      return p_123208_ & 15;
   }

   public static SectionPos blockToSection(long packedBlockPos) {
      return new SectionPos(blockToSectionCoord(BlockPos.getX(packedBlockPos)), blockToSectionCoord(BlockPos.getY(packedBlockPos)), blockToSectionCoord(BlockPos.getZ(packedBlockPos)));
   }

   public static short sectionRelativePos(BlockPos p_123219_) {
	  long i = sectionRelative(p_123219_.getX());
      int j = sectionRelative(p_123219_.getY());
      long k = sectionRelative(p_123219_.getZ());
      return (short)(i << 8 | k << 4 | j << 0);
   }

   public static int sectionRelativeX(short p_123205_) {
      return p_123205_ >>> 8 & 15;
   }

   public static int sectionRelativeY(short p_123221_) {
      return p_123221_ >>> 0 & 15;
   }

   public static int sectionRelativeZ(short p_123228_) {
      return p_123228_ >>> 4 & 15;
   }

   public long relativeToBlockX(short p_123233_) {
      return this.minBlockX() + sectionRelativeX(p_123233_);
   }

   public int relativeToBlockY(short p_123238_) {
      return this.minBlockY() + sectionRelativeY(p_123238_);
   }

   public long relativeToBlockZ(short p_123243_) {
      return this.minBlockZ() + sectionRelativeZ(p_123243_);
   }

   public BlockPos relativeToBlockPos(short p_123246_) {
      return new BlockPos(this.relativeToBlockX(p_123246_), this.relativeToBlockY(p_123246_), this.relativeToBlockZ(p_123246_));
   }
   
   public static int sectionToBlockCoord(int p_123224_) {
      return p_123224_ << 4;
   }

   public static long sectionToBlockCoord(long p_123224_) {
      return p_123224_ << 4;
   }

   public static int sectionToBlockCoord(int p_175555_, int p_175556_) {
      return sectionToBlockCoord(p_175555_) + p_175556_;
   }
   
   public static long sectionToBlockCoord(long p_175555_, long p_175556_) {
      return sectionToBlockCoord(p_175555_) + p_175556_;
   }

   public long x() {
      return this.getX();
   }

   public int y() {
      return this.getY();
   }

   public long z() {
      return this.getZ();
   }

   public long minBlockX() {
      return sectionToBlockCoord(this.x());
   }

   public int minBlockY() {
      return sectionToBlockCoord(this.y());
   }

   public long minBlockZ() {
      return sectionToBlockCoord(this.z());
   }

   public long maxBlockX() {
      return sectionToBlockCoord(this.x(), 15);
   }

   public int maxBlockY() {
      return sectionToBlockCoord(this.y(), 15);
   }

   public long maxBlockZ() {
      return sectionToBlockCoord(this.z(), 15);
   }

   public static SectionPos getZeroNode(SectionPos sectionPos) {
      return new SectionPos(sectionPos.x(), 0, sectionPos.z());
   }

   public BlockPos origin() {
      return new BlockPos(sectionToBlockCoord(this.x()), sectionToBlockCoord(this.y()), sectionToBlockCoord(this.z()));
   }

   public BlockPos center() {
      int i = 8;
      return this.origin().offset(8, 8, 8);
   }

   public ChunkPos chunk() {
      return new ChunkPos(this.x(), this.z());
   }

   public SectionPos offset(long p_175571_, int p_175572_, long p_175573_) {
      return p_175571_ == 0 && p_175572_ == 0 && p_175573_ == 0 ? this : new SectionPos(this.x() + p_175571_, this.y() + p_175572_, this.z() + p_175573_);
   }

   public Stream<BlockPos> blocksInside() {
      return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
   }

   public static Stream<SectionPos> cube(SectionPos p_123202_, int p_123203_) {
      long i = p_123202_.x();
      int j = p_123202_.y();
      long k = p_123202_.z();
      return betweenClosedStream(i - p_123203_, j - p_123203_, k - p_123203_, i + p_123203_, j + p_123203_, k + p_123203_);
   }

   public static Stream<SectionPos> aroundChunk(ChunkPos p_175558_, int p_175559_, int p_175560_, int p_175561_) {
	   long i = p_175558_.x;
      long j = p_175558_.z;
      return betweenClosedStream(i - p_175559_, p_175560_, j - p_175559_, i + p_175559_, p_175561_ - 1, j + p_175559_);
   }

   public static Stream<SectionPos> betweenClosedStream(final long p_123178_, final int p_123179_, final long p_123180_, final long p_123181_, final int p_123182_, final long p_123183_) {
      return StreamSupport.stream(new Spliterators.AbstractSpliterator<SectionPos>((long)((p_123181_ - p_123178_ + 1) * (p_123182_ - p_123179_ + 1) * (p_123183_ - p_123180_ + 1)), 64) {
         final Cursor3D cursor = new Cursor3D(p_123178_, p_123179_, p_123180_, p_123181_, p_123182_, p_123183_);

         public boolean tryAdvance(Consumer<? super SectionPos> p_123271_) {
            if (this.cursor.advance()) {
               p_123271_.accept(new SectionPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
               return true;
            } else {
               return false;
            }
         }
      }, false);
   }

   public static void aroundAndAtBlockPos(BlockPos p_194643_, Consumer<SectionPos> p_194644_) {
      aroundAndAtBlockPos(p_194643_.getX(), p_194643_.getY(), p_194643_.getZ(), p_194644_);
   }

   public static void aroundAndAtBlockPos(long p_194640_, Consumer<SectionPos> p_194641_) {
      aroundAndAtBlockPos(BlockPos.getX(p_194640_), BlockPos.getY(p_194640_), BlockPos.getZ(p_194640_), p_194641_);
   }

   public static void aroundAndAtBlockPos(long p_194635_, int p_194636_, long p_194637_, Consumer<SectionPos> p_194638_) {
	  long i = blockToSectionCoord(p_194635_ - 1);
	  long j = blockToSectionCoord(p_194635_ + 1);
      int k = blockToSectionCoord(p_194636_ - 1);
      int l = blockToSectionCoord(p_194636_ + 1);
      long i1 = blockToSectionCoord(p_194637_ - 1);
      long j1 = blockToSectionCoord(p_194637_ + 1);
      if (i == j && k == l && i1 == j1) {
         p_194638_.accept(new SectionPos(i, k, i1));
      } else {
         for(long k1 = i; k1 <= j; ++k1) {
            for(int l1 = k; l1 <= l; ++l1) {
               for(long i2 = i1; i2 <= j1; ++i2) {
                  p_194638_.accept(new SectionPos(k1, l1, i2));
               }
            }
         }
      }

   }

	public SectionPos offset(Direction direction) {
		return new SectionPos(this.x() + direction.getStepX(), this.y() +  direction.getStepY(), this.z() + direction.getStepZ());
	}
}