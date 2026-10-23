package net.minecraft.core;

import com.mojang.serialization.Codec;
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
    public static final Codec<SectionPos> CODEC = Vec3i.CODEC.xmap(value -> of(value.getX(), value.getY(), value.getZ()), value -> value);
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;

    SectionPos(long pX, int pY, long pZ) {
        this(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    SectionPos(Int128 pX, int pY, Int128 pZ) {
        super(WorldBounds.clampChunk(pX), pY, WorldBounds.clampChunk(pZ));
    }

    /** Coordinates produced by shifting a block position are already inside the section bounds. */
    private SectionPos(long xHi, long xLo, int y, long zHi, long zLo) {
        super(xHi, xLo, y, zHi, zLo);
    }

    public static SectionPos of(Int128 pChunkX, int pChunkY, Int128 pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(long pChunkX, int pChunkY, long pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(BlockPos pPos) {
        return new SectionPos(pPos.getXHi() >> 4, (pPos.getXLo() >>> 4) | (pPos.getXHi() << 60),
                blockToSectionCoord(pPos.getY()), pPos.getZHi() >> 4, (pPos.getZLo() >>> 4) | (pPos.getZHi() << 60));
    }

    /** Full-width comparison without allocating either section or coordinate objects. */
    public boolean contains(BlockPos pos) {
        return this.getY() == (pos.getY() >> 4)
                && this.getXHi() == (pos.getXHi() >> 4)
                && this.getXLo() == ((pos.getXLo() >>> 4) | (pos.getXHi() << 60))
                && this.getZHi() == (pos.getZHi() >> 4)
                && this.getZLo() == ((pos.getZLo() >>> 4) | (pos.getZHi() << 60));
    }

    public static SectionPos of(ChunkPos pChunkPos, int pY) {
        return new SectionPos(pChunkPos.x(), pY, pChunkPos.z());
    }

    public static SectionPos of(EntityAccess pEntity) {
        return of(pEntity.blockPosition());
    }

    public static SectionPos of(Position pPosition) {
        return of(pPosition.exactPosition().blockPosition());
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
        return sectionPos.y() == 0 ? sectionPos : new SectionPos(sectionPos.getXHi(), sectionPos.getXLo(), 0, sectionPos.getZHi(), sectionPos.getZLo());
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
                : new SectionPos(this.x().addSaturated(pDx), this.y() + pDy, this.z().addSaturated(pDz));
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
        long minXHi = Int128Math.addSaturatedHi(pPos.getXHi(), pPos.getXLo(), -1L, -1L);
        long minXLo = Int128Math.addSaturatedLo(pPos.getXHi(), pPos.getXLo(), -1L, -1L);
        long maxXHi = Int128Math.addSaturatedHi(pPos.getXHi(), pPos.getXLo(), 0L, 1L);
        long maxXLo = Int128Math.addSaturatedLo(pPos.getXHi(), pPos.getXLo(), 0L, 1L);
        long minZHi = Int128Math.addSaturatedHi(pPos.getZHi(), pPos.getZLo(), -1L, -1L);
        long minZLo = Int128Math.addSaturatedLo(pPos.getZHi(), pPos.getZLo(), -1L, -1L);
        long maxZHi = Int128Math.addSaturatedHi(pPos.getZHi(), pPos.getZLo(), 0L, 1L);
        long maxZLo = Int128Math.addSaturatedLo(pPos.getZHi(), pPos.getZLo(), 0L, 1L);
        long xLo = (minXLo >>> 4) | (minXHi << 60);
        long xHi = minXHi >> 4;
        long endXLo = (maxXLo >>> 4) | (maxXHi << 60);
        long endXHi = maxXHi >> 4;
        long startZLo = (minZLo >>> 4) | (minZHi << 60);
        long startZHi = minZHi >> 4;
        long endZLo = (maxZLo >>> 4) | (maxZHi << 60);
        long endZHi = maxZHi >> 4;
        int minY = (pPos.getY() == Integer.MIN_VALUE ? pPos.getY() : pPos.getY() - 1) >> 4;
        int maxY = (pPos.getY() == Integer.MAX_VALUE ? pPos.getY() : pPos.getY() + 1) >> 4;
        for (;;) {
            for (int y = minY; y <= maxY; ++y) {
                long zHi = startZHi;
                long zLo = startZLo;
                for (;;) {
                    pConsumer.accept(new SectionPos(xHi, xLo, y, zHi, zLo));
                    if (zHi == endZHi && zLo == endZLo) break;
                    if (++zLo == 0L) ++zHi;
                }
            }
            if (xHi == endXHi && xLo == endXLo) break;
            if (++xLo == 0L) ++xHi;
        }
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
