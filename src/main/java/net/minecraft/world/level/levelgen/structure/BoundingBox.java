package net.minecraft.world.level.levelgen.structure;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.minecraft.Util;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class BoundingBox {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<BoundingBox> CODEC = Codec.LONG_STREAM.comapFlatMap((p_162383_) -> { return Util.fixedSize(p_162383_, 6).map((p_162385_) -> {
                                                                                                       return new BoundingBox(p_162385_[0], (int) p_162385_[1], p_162385_[2], p_162385_[3], (int) p_162385_[4], p_162385_[5]);
                                                                                                   }); }, (p_162391_) -> { return LongStream.of(p_162391_.minX, p_162391_.minY, p_162391_.minZ, p_162391_.maxX, p_162391_.maxY, p_162391_.maxZ); }).stable();
    private long minX;
    private int minY;
    private long minZ;
    private long maxX;
    private int maxY;
    private long maxZ;

    public BoundingBox(BlockPos pPos) {
        this(pPos.getX(), pPos.getY(), pPos.getZ(), pPos.getX(), pPos.getY(), pPos.getZ());
    }

    public BoundingBox(long pMinX, int pMinY, long pMinZ, long pMaxX, int pMaxY, long pMaxZ) {
        this.minX = WorldBounds.minBlockCoordinate(pMinX, pMaxX);
        this.minY = Math.min(pMinY, pMaxY);
        this.minZ = WorldBounds.minBlockCoordinate(pMinZ, pMaxZ);
        this.maxX = WorldBounds.maxBlockCoordinate(pMinX, pMaxX);
        this.maxY = Math.max(pMinY, pMaxY);
        this.maxZ = WorldBounds.maxBlockCoordinate(pMinZ, pMaxZ);
    }

    public static BoundingBox fromCorners(Vec3i pFirst, Vec3i pSecond) {
        return new BoundingBox(pFirst.getX(), pFirst.getY(), pFirst.getZ(), pSecond.getX(), pSecond.getY(), pSecond.getZ());
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Long.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    /** Converts inclusive block bounds into a small, origin-relative shape. */
    public AABB toLocalAABB(BlockPos origin) {
        // Subtract exact block coordinates BEFORE converting to floating point.
        // Add the exclusive upper endpoint locally, even at Long.MAX_VALUE.
        return new AABB(WorldBounds.signedDifference(this.minX, origin.getX()), (double) this.minY - origin.getY(),
                WorldBounds.signedDifference(this.minZ, origin.getZ()),
                WorldBounds.signedDifference(this.maxX, origin.getX()) + 1.0D, (double) this.maxY - origin.getY() + 1.0D,
                WorldBounds.signedDifference(this.maxZ, origin.getZ()) + 1.0D);
    }

    public static BoundingBox orientBox(long pStructureMinX, int pStructureMinY, long pStructureMinZ, int pXMin, int pYMin, int pZMin, int pXMax, int pYMax, int pZMax, Direction pFacing) {
        switch (pFacing) {
            case SOUTH:
            default:
                return new BoundingBox(WorldBounds.addBlockOffset(pStructureMinX, pXMin), pStructureMinY + pYMin, WorldBounds.addBlockOffset(pStructureMinZ, pZMin), WorldBounds.addBlockOffset(pStructureMinX, (long) pXMax - 1L + pXMin), pStructureMinY + pYMax - 1, WorldBounds.addBlockOffset(pStructureMinZ, (long) pZMax - 1L + pZMin));
            case NORTH:
                return new BoundingBox(WorldBounds.addBlockOffset(pStructureMinX, pXMin), pStructureMinY + pYMin, WorldBounds.addBlockOffset(pStructureMinZ, -(long) pZMax + 1L + pZMin), WorldBounds.addBlockOffset(pStructureMinX, (long) pXMax - 1L + pXMin), pStructureMinY + pYMax - 1, WorldBounds.addBlockOffset(pStructureMinZ, pZMin));
            case WEST:
                return new BoundingBox(WorldBounds.addBlockOffset(pStructureMinX, -(long) pZMax + 1L + pZMin), pStructureMinY + pYMin, WorldBounds.addBlockOffset(pStructureMinZ, pXMin), WorldBounds.addBlockOffset(pStructureMinX, pZMin), pStructureMinY + pYMax - 1, WorldBounds.addBlockOffset(pStructureMinZ, (long) pXMax - 1L + pXMin));
            case EAST:
                return new BoundingBox(WorldBounds.addBlockOffset(pStructureMinX, pZMin), pStructureMinY + pYMin, WorldBounds.addBlockOffset(pStructureMinZ, pXMin), WorldBounds.addBlockOffset(pStructureMinX, (long) pZMax - 1L + pZMin), pStructureMinY + pYMax - 1, WorldBounds.addBlockOffset(pStructureMinZ, (long) pXMax - 1L + pXMin));
        }
    }

    public boolean intersects(BoundingBox pBox) {
        return WorldBounds.blockRangesOverlap(this.minX, this.maxX, pBox.minX, pBox.maxX) && WorldBounds.blockRangesOverlap(this.minZ, this.maxZ, pBox.minZ, pBox.maxZ) && this.maxY >= pBox.minY && this.minY <= pBox.maxY;
    }

    public boolean intersects(long pMinX, long pMinZ, long pMaxX, long pMaxZ) {
        return WorldBounds.blockRangesOverlap(this.minX, this.maxX, pMinX, pMaxX) && WorldBounds.blockRangesOverlap(this.minZ, this.maxZ, pMinZ, pMaxZ);
    }

    public static Optional<BoundingBox> encapsulatingPositions(Iterable<BlockPos> pPositions) {
        Iterator<BlockPos> iterator = pPositions.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingbox = new BoundingBox(iterator.next());
            iterator.forEachRemaining(boundingbox::encapsulate);
            return Optional.of(boundingbox);
        }
    }

    public static Optional<BoundingBox> encapsulatingBoxes(Iterable<BoundingBox> pBoxes) {
        Iterator<BoundingBox> iterator = pBoxes.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingbox = iterator.next();
            BoundingBox boundingbox1 = new BoundingBox(boundingbox.minX, boundingbox.minY, boundingbox.minZ, boundingbox.maxX, boundingbox.maxY, boundingbox.maxZ);
            iterator.forEachRemaining(boundingbox1::encapsulate);
            return Optional.of(boundingbox1);
        }
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox encapsulate(BoundingBox p_162387_) {
        this.minX = WorldBounds.minBlockCoordinate(this.minX, p_162387_.minX);
        this.minY = Math.min(this.minY, p_162387_.minY);
        this.minZ = WorldBounds.minBlockCoordinate(this.minZ, p_162387_.minZ);
        this.maxX = WorldBounds.maxBlockCoordinate(this.maxX, p_162387_.maxX);
        this.maxY = Math.max(this.maxY, p_162387_.maxY);
        this.maxZ = WorldBounds.maxBlockCoordinate(this.maxZ, p_162387_.maxZ);
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox encapsulate(BlockPos p_162372_) {
        this.minX = WorldBounds.minBlockCoordinate(this.minX, p_162372_.getX());
        this.minY = Math.min(this.minY, p_162372_.getY());
        this.minZ = WorldBounds.minBlockCoordinate(this.minZ, p_162372_.getZ());
        this.maxX = WorldBounds.maxBlockCoordinate(this.maxX, p_162372_.getX());
        this.maxY = Math.max(this.maxY, p_162372_.getY());
        this.maxZ = WorldBounds.maxBlockCoordinate(this.maxZ, p_162372_.getZ());
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox move(long pX, int pY, long pZ) {
        this.minX = WorldBounds.addBlockOffset(this.minX, pX);
        this.minY += pY;
        this.minZ = WorldBounds.addBlockOffset(this.minZ, pZ);
        this.maxX = WorldBounds.addBlockOffset(this.maxX, pX);
        this.maxY += pY;
        this.maxZ = WorldBounds.addBlockOffset(this.maxZ, pZ);
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox move(Vec3i pVector) {
        return this.move(pVector.getX(), pVector.getY(), pVector.getZ());
    }

    public BoundingBox moved(int pX, int pY, int pZ) {
        return new BoundingBox(WorldBounds.addBlockOffset(this.minX, pX), this.minY + pY, WorldBounds.addBlockOffset(this.minZ, pZ), WorldBounds.addBlockOffset(this.maxX, pX), this.maxY + pY, WorldBounds.addBlockOffset(this.maxZ, pZ));
    }

    public BoundingBox inflatedBy(int pValue) {
        return new BoundingBox(WorldBounds.addBlockOffset(this.minX, -(long) pValue), this.minY - pValue, WorldBounds.addBlockOffset(this.minZ, -(long) pValue), WorldBounds.addBlockOffset(this.maxX, pValue), this.maxY + pValue, WorldBounds.addBlockOffset(this.maxZ, pValue));
    }

    public boolean isInside(Vec3i pVector) {
        return WorldBounds.isWithinBlockRange(pVector.getX(), this.minX, this.maxX) && WorldBounds.isWithinBlockRange(pVector.getZ(), this.minZ, this.maxZ) && pVector.getY() >= this.minY && pVector.getY() <= this.maxY;
    }

    public Vec3i getLength() {
        return new Vec3i(distanceAsLong(this.minX, this.maxX), this.maxY - this.minY, distanceAsLong(this.minZ, this.maxZ));
    }

    public long getXSpan() {
        return WorldBounds.inclusiveBlockSpan(this.minX, this.maxX);
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public long getZSpan() {
        return WorldBounds.inclusiveBlockSpan(this.minZ, this.maxZ);
    }

    public BlockPos getCenter() {
        return new BlockPos(center(this.minX, this.maxX), this.minY + (this.maxY - this.minY + 1) / 2, center(this.minZ, this.maxZ));
    }

    private static long distanceAsLong(long min, long max) {
        return WorldBounds.signedDifferenceAsLong(max, min);
    }

    private static long center(long min, long max) {
        return WorldBounds.middleBlockCoordinate(min, max);
    }

    public void forAllCorners(Consumer<BlockPos> pPos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        pPos.accept(blockpos$mutableblockpos.set(this.maxX, this.maxY, this.maxZ));
        pPos.accept(blockpos$mutableblockpos.set(this.minX, this.maxY, this.maxZ));
        pPos.accept(blockpos$mutableblockpos.set(this.maxX, this.minY, this.maxZ));
        pPos.accept(blockpos$mutableblockpos.set(this.minX, this.minY, this.maxZ));
        pPos.accept(blockpos$mutableblockpos.set(this.maxX, this.maxY, this.minZ));
        pPos.accept(blockpos$mutableblockpos.set(this.minX, this.maxY, this.minZ));
        pPos.accept(blockpos$mutableblockpos.set(this.maxX, this.minY, this.minZ));
        pPos.accept(blockpos$mutableblockpos.set(this.minX, this.minY, this.minZ));
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("minX", this.minX).add("minY", this.minY).add("minZ", this.minZ).add("maxX", this.maxX).add("maxY", this.maxY).add("maxZ", this.maxZ).toString();
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof BoundingBox)) {
            return false;
        } else {
            BoundingBox boundingbox = (BoundingBox) pOther;
            return this.minX == boundingbox.minX && this.minY == boundingbox.minY && this.minZ == boundingbox.minZ && this.maxX == boundingbox.maxX && this.maxY == boundingbox.maxY && this.maxZ == boundingbox.maxZ;
        }
    }

    public int hashCode() {
        return Objects.hash(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public long minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public long minZ() {
        return this.minZ;
    }

    public long maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public long maxZ() {
        return this.maxZ;
    }
}
