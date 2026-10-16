package net.minecraft.core;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
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

    SectionPos(long pX, int pY, long pZ) {
        super(pX, pY, pZ);
    }

    public static SectionPos of(long pChunkX, int pChunkY, long pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(BlockPos pPos) {
        return new SectionPos(blockToSectionCoord(pPos.getX()), (int) blockToSectionCoord(pPos.getY()), blockToSectionCoord(pPos.getZ()));
    }

    public static SectionPos of(ChunkPos pChunkPos, int pY) {
        return new SectionPos(pChunkPos.x, pY, pChunkPos.z);
    }

    public static SectionPos of(EntityAccess pEntity) {
        return of(pEntity.blockPosition());
    }

    public static SectionPos of(Position pPosition) {
        return new SectionPos(blockToSectionCoord(pPosition.x()), blockToSectionCoord(pPosition.y()), blockToSectionCoord(pPosition.z()));
    }

    public static SectionPos bottomOf(ChunkAccess pChunk) {
        return of(pChunk.getPos(), pChunk.getMinSection());
    }

    public static long posToSectionCoord(double pPos) {
        return blockToSectionCoord(Mth.lfloor(WorldBounds.clampAbsoluteDouble(pPos)));
    }

    public static long blockToSectionCoord(long pBlockCoord) {
        return pBlockCoord >> 4L;
    }

    public static int blockToSectionCoord(int pBlockCoord) {
        return pBlockCoord >> 4;
    }

    public static int blockToSectionCoord(double pCoord) {
        return Mth.floor(pCoord) >> 4;
    }

    public static int sectionRelative(long pRel) {
        return (int) (pRel & 15);
    }

    public static int sectionRelative(int pRel) {
        return pRel & 15;
    }

    public static SectionPos blockToSection(long packedBlockPos) {
        return new SectionPos(blockToSectionCoord(BlockPos.getX(packedBlockPos)), blockToSectionCoord(BlockPos.getY(packedBlockPos)), blockToSectionCoord(BlockPos.getZ(packedBlockPos)));
    }

    public static short sectionRelativePos(BlockPos pPos) {
        long i = sectionRelative(pPos.getX());
        int j = sectionRelative(pPos.getY());
        long k = sectionRelative(pPos.getZ());
        return (short) (i << 8 | k << 4 | j << 0);
    }

    public static int sectionRelativeX(short pX) {
        return pX >>> 8 & 15;
    }

    public static int sectionRelativeY(short pY) {
        return pY >>> 0 & 15;
    }

    public static int sectionRelativeZ(short pZ) {
        return pZ >>> 4 & 15;
    }

    public long relativeToBlockX(short pX) {
        return WorldBounds.addBlockOffset(this.minBlockX(), sectionRelativeX(pX));
    }

    public int relativeToBlockY(short pY) {
        return this.minBlockY() + sectionRelativeY(pY);
    }

    public long relativeToBlockZ(short pZ) {
        return WorldBounds.addBlockOffset(this.minBlockZ(), sectionRelativeZ(pZ));
    }

    public BlockPos relativeToBlockPos(short pPos) {
        return new BlockPos(this.relativeToBlockX(pPos), this.relativeToBlockY(pPos), this.relativeToBlockZ(pPos));
    }

    public static int sectionToBlockCoord(int pSectionCoord) {
        return pSectionCoord << 4;
    }

    public static long sectionToBlockCoord(long pSectionCoord) {
        return WorldBounds.chunkToBlock(pSectionCoord);
    }

    public static int sectionToBlockCoord(int pPos, int pOffset) {
        return sectionToBlockCoord(pPos) + pOffset;
    }

    public static long sectionToBlockCoord(long pPos, long pOffset) {
        return WorldBounds.addBlockOffset(sectionToBlockCoord(pPos), pOffset);
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

    public SectionPos offset(long pDx, int pDy, long pDz) {
        return pDx == 0 && pDy == 0 && pDz == 0 ? this : new SectionPos(WorldBounds.addChunkOffset(this.x(), pDx), this.y() + pDy, WorldBounds.addChunkOffset(this.z(), pDz));
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(SectionPos pCenter, int pRadius) {
        long i = pCenter.x();
        int j = pCenter.y();
        long k = pCenter.z();
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pRadius), j - pRadius, WorldBounds.addChunkOffset(k, -(long) pRadius),
                WorldBounds.addChunkOffset(i, pRadius), j + pRadius, WorldBounds.addChunkOffset(k, pRadius));
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos pChunkPos, int pX, int pY, int pZ) {
        long i = pChunkPos.x;
        long j = pChunkPos.z;
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pX), pY, WorldBounds.addChunkOffset(j, -(long) pX),
                WorldBounds.addChunkOffset(i, pX), pZ - 1, WorldBounds.addChunkOffset(j, pX));
    }

    public static Stream<SectionPos> betweenClosedStream(final long pX1, final int pY1, final long pZ1, final long pX2, final int pY2, final long pZ2) {
        long size = pX1 > pX2 || pY1 > pY2 || pZ1 > pZ2 ? 0L
                : Math.multiplyExact(Math.multiplyExact(Math.addExact(Math.subtractExact(pX2, pX1), 1L), (long) pY2 - pY1 + 1L), Math.addExact(Math.subtractExact(pZ2, pZ1), 1L));
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<SectionPos>(size, 64) {
            final Cursor3D cursor = new Cursor3D(pX1, pY1, pZ1, pX2, pY2, pZ2);

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

    public static void aroundAndAtBlockPos(BlockPos pPos, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(pPos.getX(), pPos.getY(), pPos.getZ(), pConsumer);
    }

    public static void aroundAndAtBlockPos(long pPos, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(BlockPos.getX(pPos), BlockPos.getY(pPos), BlockPos.getZ(pPos), pConsumer);
    }

    public static void aroundAndAtBlockPos(long pX, int pY, long pZ, Consumer<SectionPos> pConsumer) {
        long i = blockToSectionCoord(WorldBounds.addBlockOffset(pX, -1L));
        long j = blockToSectionCoord(WorldBounds.addBlockOffset(pX, 1L));
        int k = blockToSectionCoord(pY - 1);
        int l = blockToSectionCoord(pY + 1);
        long i1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, -1L));
        long j1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, 1L));
        if (i == j && k == l && i1 == j1) {
            pConsumer.accept(new SectionPos(i, k, i1));
        } else {
            for (long k1 = i;; k1 = WorldBounds.addChunkOffset(k1, 1L)) {
                for (int l1 = k; l1 <= l; ++l1) {
                    for (long i2 = i1;; i2 = WorldBounds.addChunkOffset(i2, 1L)) {
                        pConsumer.accept(new SectionPos(k1, l1, i2));
                        if (i2 == j1)
                            break;
                    }
                }
                if (k1 == j)
                    break;
            }
        }
    }

    public SectionPos offset(Direction direction) {
        return this.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }
}
