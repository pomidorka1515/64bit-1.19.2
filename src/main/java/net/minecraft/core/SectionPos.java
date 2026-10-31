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
        this(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    SectionPos(Int1088 pX, int pY, Int1088 pZ) {
        super(WorldBounds.clampChunk(pX), pY, WorldBounds.clampChunk(pZ));
    }

    public static SectionPos of(Int1088 pChunkX, int pChunkY, Int1088 pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(long pChunkX, int pChunkY, long pChunkZ) {
        return new SectionPos(pChunkX, pChunkY, pChunkZ);
    }

    public static SectionPos of(BlockPos pPos) {
        return new SectionPos(pPos.getX().shiftRight(4), blockToSectionCoord(pPos.getY()), pPos.getZ().shiftRight(4));
    }

    /** Full-width comparison without allocating either section or coordinate objects. */
    public boolean contains(BlockPos pos) {
        return this.getY() == (pos.getY() >> 4)
                && matchesBlockCoordinate(this.getX(), pos, true)
                && matchesBlockCoordinate(this.getZ(), pos, false);
    }

    /** Compare an exact section identity with a block's primitive, possibly mutable limb view. */
    public static boolean matchesBlockCoordinate(Int1088 section, BlockPos block, boolean x) {
        // Immutable positions already own canonical coordinates. Read them once,
        // rather than dispatching through BlockPos for every sign-extended limb.
        // MutableBlockPos must retain its primitive path: getX/getZ would allocate.
        if (!(block instanceof BlockPos.MutableBlockPos)) {
            return matchesBlockCoordinate(section, x ? block.getX() : block.getZ());
        }
        int length = Math.max(section.limbCount(), x ? block.xLimbCount() : block.zLimbCount());
        for (int i = 0; i < length; ++i) {
            long low = x ? block.xLimb(i) : block.zLimb(i);
            long high = i + 1 < Int1088.LIMBS ? (x ? block.xLimb(i + 1) : block.zLimb(i + 1)) : low >> 63;
            if (section.limb(i) != ((low >>> 4) | (high << 60))) return false;
        }
        return true;
    }

    private static boolean matchesBlockCoordinate(Int1088 section, Int1088 block) {
        if (block.fitsLong()) return section.fitsLong() && section.lowLimb() == (block.lowLimb() >> SECTION_BITS);
        int length = Math.max(section.limbCount(), block.limbCount());
        long low = block.lowLimb();
        for (int i = 0; i < length; ++i) {
            long high = i + 1 < Int1088.LIMBS ? block.limb(i + 1) : low >> 63;
            if (section.limb(i) != ((low >>> SECTION_BITS) | (high << (64 - SECTION_BITS)))) return false;
            low = high;
        }
        return true;
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

    public static Int1088 posToSectionCoord(double pPos) {
        return blockToSectionCoord(Int1088.fromDoubleFloorSaturated(pPos));
    }

    public static Int1088 blockToSectionCoord(Int1088 pBlockCoord) {
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

    public static int sectionRelative(Int1088 pRel) {
        return (int) (pRel.lowLimb() & 15L);
    }

    public static int sectionRelative(int pRel) {
        return pRel & 15;
    }

    public static SectionPos blockToSection(BlockPos pPos) {
        return of(pPos);
    }

    public static short sectionRelativePos(BlockPos pPos) {
        int i = sectionRelative(pPos.xLimb(0));
        int j = sectionRelative(pPos.getY());
        int k = sectionRelative(pPos.zLimb(0));
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

    public Int1088 relativeToBlockX(short pX) {
        return WorldBounds.addBlockOffset(this.minBlockX(), sectionRelativeX(pX));
    }

    public int relativeToBlockY(short pY) {
        return this.minBlockY() + sectionRelativeY(pY);
    }

    public Int1088 relativeToBlockZ(short pZ) {
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

    public static Int1088 sectionToBlockCoord(Int1088 pSectionCoord) {
        return WorldBounds.chunkToBlock(pSectionCoord);
    }

    public static int sectionToBlockCoord(int pPos, int pOffset) {
        return sectionToBlockCoord(pPos) + pOffset;
    }

    public static long sectionToBlockCoord(long pPos, long pOffset) {
        return WorldBounds.addBlockOffset(sectionToBlockCoord(pPos), pOffset);
    }

    public static Int1088 sectionToBlockCoord(Int1088 pPos, long pOffset) {
        return WorldBounds.addBlockOffset(sectionToBlockCoord(pPos), pOffset);
    }

    public Int1088 x() {
        return this.getX();
    }

    public int y() {
        return this.getY();
    }

    public Int1088 z() {
        return this.getZ();
    }

    public Int1088 minBlockX() {
        return sectionToBlockCoord(this.x());
    }

    public int minBlockY() {
        return sectionToBlockCoord(this.y());
    }

    public Int1088 minBlockZ() {
        return sectionToBlockCoord(this.z());
    }

    public Int1088 maxBlockX() {
        return sectionToBlockCoord(this.x(), 15);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(this.y(), 15);
    }

    public Int1088 maxBlockZ() {
        return sectionToBlockCoord(this.z(), 15);
    }

    public static SectionPos getZeroNode(SectionPos sectionPos) {
        return sectionPos.y() == 0 ? sectionPos : new SectionPos(sectionPos.getX(), 0, sectionPos.getZ());
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

    public SectionPos offset(Int1088 pDx, int pDy, Int1088 pDz) {
        return pDx.equals(Int1088.ZERO) && pDy == 0 && pDz.equals(Int1088.ZERO) ? this
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
        Int1088 i = pCenter.x();
        int j = pCenter.y();
        Int1088 k = pCenter.z();
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pRadius), j - pRadius, WorldBounds.addChunkOffset(k, -(long) pRadius),
                WorldBounds.addChunkOffset(i, pRadius), j + pRadius, WorldBounds.addChunkOffset(k, pRadius));
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos pChunkPos, int pX, int pY, int pZ) {
        Int1088 i = pChunkPos.x();
        Int1088 j = pChunkPos.z();
        return betweenClosedStream(WorldBounds.addChunkOffset(i, -(long) pX), pY, WorldBounds.addChunkOffset(j, -(long) pX),
                WorldBounds.addChunkOffset(i, pX), pZ - 1, WorldBounds.addChunkOffset(j, pX));
    }

    public static Stream<SectionPos> betweenClosedStream(long pX1, int pY1, long pZ1, long pX2, int pY2, long pZ2) {
        return betweenClosedStream(Int1088.fromLong(pX1), pY1, Int1088.fromLong(pZ1), Int1088.fromLong(pX2), pY2, Int1088.fromLong(pZ2));
    }

    public static Stream<SectionPos> betweenClosedStream(Int1088 pX1, int pY1, Int1088 pZ1, Int1088 pX2, int pY2, Int1088 pZ2) {
        Int1088 minX = WorldBounds.clampChunk(pX1);
        Int1088 minZ = WorldBounds.clampChunk(pZ1);
        Int1088 maxX = WorldBounds.clampChunk(pX2);
        Int1088 maxZ = WorldBounds.clampChunk(pZ2);
        boolean empty = pX1.compareTo(pX2) > 0 || pY1 > pY2 || pZ1.compareTo(pZ2) > 0;
        Int1088 spanX = WorldBounds.inclusiveBlockSpan(minX, maxX);
        Int1088 spanZ = WorldBounds.inclusiveBlockSpan(minZ, maxZ);
        long size = empty ? 0L : WorldBounds.multiplySaturated(
                WorldBounds.multiplySaturated(spanX.fitsLong() ? spanX.lowLimb() : Long.MAX_VALUE,
                        (long) pY2 - pY1 + 1L), spanZ.fitsLong() ? spanZ.lowLimb() : Long.MAX_VALUE);
        Cursor3D cursor = new Cursor3D(minX, pY1, minZ, maxX, pY2, maxZ);
        // A saturated size is only a hint, not an exact SIZED contract.
        int characteristics = java.util.Spliterator.ORDERED | (size == Long.MAX_VALUE ? 0 : java.util.Spliterator.SIZED);
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<SectionPos>(size, characteristics) {
            public boolean tryAdvance(Consumer<? super SectionPos> pConsumer) {
                if (empty || !cursor.advance()) {
                    return false;
                }
                pConsumer.accept(new SectionPos(cursor.nextX(), cursor.nextY(), cursor.nextZ()));
                return true;
            }
        }, false);
    }

    public static void aroundAndAtBlockPos(BlockPos pPos, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(pPos.getX(), pPos.getY(), pPos.getZ(), pConsumer);
    }

    public static void aroundAndAtBlockPos(long pX, int pY, long pZ, Consumer<SectionPos> pConsumer) {
        aroundAndAtBlockPos(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ), pConsumer);
    }

    public static void aroundAndAtBlockPos(Int1088 pX, int pY, Int1088 pZ, Consumer<SectionPos> pConsumer) {
        Int1088 i = blockToSectionCoord(WorldBounds.addBlockOffset(pX, -1L));
        Int1088 j = blockToSectionCoord(WorldBounds.addBlockOffset(pX, 1L));
        int k = blockToSectionCoord(pY == Integer.MIN_VALUE ? pY : pY - 1);
        int l = blockToSectionCoord(pY == Integer.MAX_VALUE ? pY : pY + 1);
        Int1088 i1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, -1L));
        Int1088 j1 = blockToSectionCoord(WorldBounds.addBlockOffset(pZ, 1L));
        if (i.equals(j) && k == l && i1.equals(j1)) {
            pConsumer.accept(new SectionPos(i, k, i1));
        } else {
            for (Int1088 k1 = i;; k1 = WorldBounds.addChunkOffset(k1, 1L)) {
                for (int l1 = k; l1 <= l; ++l1) {
                    for (Int1088 i2 = i1;; i2 = WorldBounds.addChunkOffset(i2, 1L)) {
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
