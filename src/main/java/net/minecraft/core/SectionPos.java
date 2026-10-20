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
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;
    private final Int128 x;
    private final int y;
    private final Int128 z;

    SectionPos(long pX, int pY, long pZ) {
        this(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    SectionPos(Int128 pX, int pY, Int128 pZ) {
        super(WorldBounds.clampChunk(pX), pY, WorldBounds.clampChunk(pZ));
        this.x = super.getX();
        this.y = pY;
        this.z = super.getZ();
    }

    public static SectionPos of(Int128 pChunkX, int pChunkY, Int128 pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(long pChunkX, int pChunkY, long pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(BlockPos pPos) {
        return new SectionPos(blockToSectionCoord(pPos.getX()), blockToSectionCoord(pPos.getY()), blockToSectionCoord(pPos.getZ()));
    }

    public static SectionPos of(ChunkPos pChunkPos, int pY) {
        return new SectionPos(pChunkPos.x(), pY, pChunkPos.z());
    }

    public static SectionPos of(EntityAccess pEntity) {
        return of(pEntity.blockPosition());
    }

    public static SectionPos of(Position pPosition) {
        return new SectionPos(posToSectionCoord(pPosition.x()), blockToSectionCoord(pPosition.y()), posToSectionCoord(pPosition.z()));
    }

    public static SectionPos bottomOf(ChunkAccess pChunk) {
        return of(pChunk.getPos(), pChunk.getMinSection());
    }

    public static Int128 posToSectionCoord(double pPos) {
        return blockToSectionCoord(Int128.fromDoubleFloorSaturated(pPos));
    }

    public static Int128 blockToSectionCoord(Int128 pBlockCoord) {
        return pBlockCoord.shiftRight(4);
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

    public static int sectionRelative(Int128 pRel) {
        return (int) (pRel.lo() & 15L);
    }

    public static int sectionRelative(int pRel) {
        return pRel & 15;
    }

    public static SectionPos blockToSection(BlockPos pPos) {
        return of(pPos);
    }

    public static short sectionRelativePos(BlockPos pPos) {
        int i = sectionRelative(pPos.getXLo());
        int j = sectionRelative(pPos.getY());
        int k = sectionRelative(pPos.getZLo());
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

    public Int128 relativeToBlockX(short pX) {
        return WorldBounds.addBlockOffset(this.minBlockX(), sectionRelativeX(pX));
    }

    public int relativeToBlockY(short pY) {
        return this.minBlockY() + sectionRelativeY(pY);
    }

    public Int128 relativeToBlockZ(short pZ) {
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

    public static Int128 sectionToBlockCoord(Int128 pSectionCoord) {
        return WorldBounds.chunkToBlock(pSectionCoord);
    }

    public static int sectionToBlockCoord(int pPos, int pOffset) {
        return sectionToBlockCoord(pPos) + pOffset;
    }

    public static long sectionToBlockCoord(long pPos, long pOffset) {
        return WorldBounds.addBlockOffset(sectionToBlockCoord(pPos), pOffset);
    }

    public static Int128 sectionToBlockCoord(Int128 pPos, long pOffset) {
        return WorldBounds.addBlockOffset(sectionToBlockCoord(pPos), pOffset);
    }

    public Int128 getX() { return this.x; }
    public int getY() { return this.y; }
    public Int128 getZ() { return this.z; }

    public Int128 x() {
        return this.getX();
    }

    public int y() {
        return this.getY();
    }

    public Int128 z() {
        return this.getZ();
    }

    public Int128 minBlockX() {
        return sectionToBlockCoord(this.x());
    }

    public int minBlockY() {
        return sectionToBlockCoord(this.y());
    }

    public Int128 minBlockZ() {
        return sectionToBlockCoord(this.z());
    }

    public Int128 maxBlockX() {
        return sectionToBlockCoord(this.x(), 15);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(this.y(), 15);
    }

    public Int128 maxBlockZ() {
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

    public SectionPos offset(Int128 pDx, int pDy, Int128 pDz) {
        return pDx.equals(Int128.ZERO) && pDy == 0 && pDz.equals(Int128.ZERO) ? this
                : new SectionPos(this.x.addSaturated(pDx), this.y + pDy, this.z.addSaturated(pDz));
    }

    public SectionPos relative(Direction pDirection) {
        return this.relative(pDirection, 1);
    }

    public SectionPos relative(Direction pDirection, int pDistance) {
        return this.offset((long) pDirection.getStepX() * pDistance, pDirection.getStepY() * pDistance,
                (long) pDirection.getStepZ() * pDistance);
    }

    public SectionPos relative(Direction.Axis pAxis, int pAmount) {
        return this.offset(pAxis == Direction.Axis.X ? (long) pAmount : 0L,
                pAxis == Direction.Axis.Y ? pAmount : 0, pAxis == Direction.Axis.Z ? (long) pAmount : 0L);
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(SectionPos pCenter, int pRadius) {
        Int128 i = pCenter.x();
        int j = pCenter.y();
        Int128 k = pCenter.z();
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pRadius), j - pRadius, WorldBounds.addChunkOffset(k, -(long) pRadius),
                WorldBounds.addChunkOffset(i, pRadius), j + pRadius, WorldBounds.addChunkOffset(k, pRadius));
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos pChunkPos, int pX, int pY, int pZ) {
        Int128 i = pChunkPos.x();
        Int128 j = pChunkPos.z();
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pX), pY, WorldBounds.addChunkOffset(j, -(long) pX),
                WorldBounds.addChunkOffset(i, pX), pZ - 1, WorldBounds.addChunkOffset(j, pX));
    }

    public static Stream<SectionPos> betweenClosedStream(long pX1, int pY1, long pZ1, long pX2, int pY2, long pZ2) {
        return betweenClosedStream(Int128.fromLong(pX1), pY1, Int128.fromLong(pZ1), Int128.fromLong(pX2), pY2, Int128.fromLong(pZ2));
    }

    public static Stream<SectionPos> betweenClosedStream(Int128 pX1, int pY1, Int128 pZ1, Int128 pX2, int pY2, Int128 pZ2) {
        Int128 minX = WorldBounds.clampChunk(pX1);
        Int128 minZ = WorldBounds.clampChunk(pZ1);
        Int128 maxX = WorldBounds.clampChunk(pX2);
        Int128 maxZ = WorldBounds.clampChunk(pZ2);
        boolean empty = pX1.compareTo(pX2) > 0 || pY1 > pY2 || pZ1.compareTo(pZ2) > 0;
        long size = empty ? 0L : WorldBounds.multiplySaturated(
                WorldBounds.multiplySaturated(spanLong(minX, maxX), (long) pY2 - pY1 + 1L), spanLong(minZ, maxZ));
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<SectionPos>(size, 64) {
            private Int128 x = minX;
            private int y = pY1;
            private Int128 z = minZ;
            private boolean done = empty;

            public boolean tryAdvance(Consumer<? super SectionPos> pConsumer) {
                if (this.done) {
                    return false;
                }
                SectionPos pos = new SectionPos(this.x, this.y, this.z);
                if (WorldBounds.canAdvanceBlock(this.x, maxX)) {
                    this.x = WorldBounds.addChunkOffset(this.x, 1L);
                } else if (this.y != pY2) {
                    this.x = minX;
                    ++this.y;
                } else if (WorldBounds.canAdvanceBlock(this.z, maxZ)) {
                    this.x = minX;
                    this.y = pY1;
                    this.z = WorldBounds.addChunkOffset(this.z, 1L);
                } else {
                    this.done = true;
                }
                pConsumer.accept(pos);
                return true;
            }
        }, false);
    }

    private static long spanLong(Int128 min, Int128 max) {
        Int128 span = WorldBounds.inclusiveBlockSpan(min, max);
        return span.compareTo(Int128.fromLong(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : span.lo();
    }

    public static void aroundAndAtBlockPos(BlockPos pPos, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(pPos.getX(), pPos.getY(), pPos.getZ(), pConsumer);
    }

    public static void aroundAndAtBlockPos(long pX, int pY, long pZ, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(Int128.fromLong(pX), pY, Int128.fromLong(pZ), pConsumer);
    }

    public static void aroundAndAtBlockPos(Int128 pX, int pY, Int128 pZ, Consumer<SectionPos> pConsumer) {
        Int128 i = blockToSectionCoord(WorldBounds.addBlockOffset(pX, -1L));
        Int128 j = blockToSectionCoord(WorldBounds.addBlockOffset(pX, 1L));
        int k = blockToSectionCoord(pY == Integer.MIN_VALUE ? pY : pY - 1);
        int l = blockToSectionCoord(pY == Integer.MAX_VALUE ? pY : pY + 1);
        Int128 i1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, -1L));
        Int128 j1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, 1L));
        if (i.equals(j) && k == l && i1.equals(j1)) {
            pConsumer.accept(new SectionPos(i, k, i1));
        } else {
            for (Int128 k1 = i;; k1 = WorldBounds.addChunkOffset(k1, 1L)) {
                for (int l1 = k; l1 <= l; ++l1) {
                    for (Int128 i2 = i1;; i2 = WorldBounds.addChunkOffset(i2, 1L)) {
                        pConsumer.accept(new SectionPos(k1, l1, i2));
                        if (!WorldBounds.canAdvanceBlock(i2, j1))
                            break;
                    }
                }
                if (!WorldBounds.canAdvanceBlock(k1, j))
                    break;
            }
        }
    }

    public SectionPos offset(Direction direction) {
        return this.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }
}
