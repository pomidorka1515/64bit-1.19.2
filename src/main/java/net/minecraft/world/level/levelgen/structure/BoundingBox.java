package net.minecraft.world.level.levelgen.structure;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.world.level.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int1088;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

public class BoundingBox {
    /** X/Z endpoints are full 1088-bit values; Y stays in its existing int domain. */
    public static final Codec<BoundingBox> CODEC = BoundingBox.Endpoints.CODEC
        .xmap(Endpoints::toBoundingBox, Endpoints::fromBoundingBox)
        .stable();
    private Int1088 minX;
    private int minY;
    private Int1088 minZ;
    private Int1088 maxX;
    private int maxY;
    private Int1088 maxZ;

    public BoundingBox(BlockPos pPos) {
        this(pPos.getX(), pPos.getY(), pPos.getZ(), pPos.getX(), pPos.getY(), pPos.getZ());
    }

    public BoundingBox(long pMinX, int pMinY, long pMinZ, long pMaxX, int pMaxY, long pMaxZ) {
        this(Int1088.fromLong(pMinX), pMinY, Int1088.fromLong(pMinZ), Int1088.fromLong(pMaxX), pMaxY, Int1088.fromLong(pMaxZ));
    }

    public BoundingBox(Int1088 pMinX, int pMinY, Int1088 pMinZ, Int1088 pMaxX, int pMaxY, Int1088 pMaxZ) {
        this.minX = min(pMinX, pMaxX);
        this.minY = Math.min(pMinY, pMaxY);
        this.minZ = min(pMinZ, pMaxZ);
        this.maxX = max(pMinX, pMaxX);
        this.maxY = Math.max(pMinY, pMaxY);
        this.maxZ = max(pMinZ, pMaxZ);
    }

    public static BoundingBox fromCorners(Vec3i pFirst, Vec3i pSecond) {
        return new BoundingBox(pFirst.getX(), pFirst.getY(), pFirst.getZ(), pSecond.getX(), pSecond.getY(), pSecond.getZ());
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Int1088.MIN, Integer.MIN_VALUE, Int1088.MIN, Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX);
    }

    /** Converts inclusive block bounds into a small, origin-relative shape. */
    public AABB toLocalAABB(BlockPos origin) {
        // Subtract exact block coordinates BEFORE converting to floating point.
        // Add the exclusive upper endpoint locally, even at Int1088.MAX.
        return new AABB(WorldBounds.signedDifference(this.minX, origin.getX()), (double) this.minY - origin.getY(),
                WorldBounds.signedDifference(this.minZ, origin.getZ()),
                WorldBounds.signedDifference(this.maxX, origin.getX()) + 1.0D, (double) this.maxY - origin.getY() + 1.0D,
                WorldBounds.signedDifference(this.maxZ, origin.getZ()) + 1.0D);
    }

    public static BoundingBox orientBox(long pStructureMinX, int pStructureMinY, long pStructureMinZ, int pXMin, int pYMin, int pZMin, int pXMax, int pYMax, int pZMax, Direction pFacing) {
        return orientBox(Int1088.fromLong(pStructureMinX), pStructureMinY, Int1088.fromLong(pStructureMinZ), pXMin, pYMin, pZMin, pXMax, pYMax, pZMax, pFacing);
    }

    public static BoundingBox orientBox(Int1088 pStructureMinX, int pStructureMinY, Int1088 pStructureMinZ, int pXMin, int pYMin, int pZMin, int pXMax, int pYMax, int pZMax, Direction pFacing) {
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
        return this.intersects(pBox.minX, pBox.minZ, pBox.maxX, pBox.maxZ) && this.maxY >= pBox.minY && this.minY <= pBox.maxY;
    }

    public boolean intersects(long pMinX, long pMinZ, long pMaxX, long pMaxZ) {
        return this.intersects(Int1088.fromLong(pMinX), Int1088.fromLong(pMinZ), Int1088.fromLong(pMaxX), Int1088.fromLong(pMaxZ));
    }

    public boolean intersects(Int1088 pMinX, Int1088 pMinZ, Int1088 pMaxX, Int1088 pMaxZ) {
        return this.maxX.compareTo(min(pMinX, pMaxX)) >= 0 && this.minX.compareTo(max(pMinX, pMaxX)) <= 0
            && this.maxZ.compareTo(min(pMinZ, pMaxZ)) >= 0 && this.minZ.compareTo(max(pMinZ, pMaxZ)) <= 0;
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
        this.minX = min(this.minX, p_162387_.minX);
        this.minY = Math.min(this.minY, p_162387_.minY);
        this.minZ = min(this.minZ, p_162387_.minZ);
        this.maxX = max(this.maxX, p_162387_.maxX);
        this.maxY = Math.max(this.maxY, p_162387_.maxY);
        this.maxZ = max(this.maxZ, p_162387_.maxZ);
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox encapsulate(BlockPos p_162372_) {
        this.minX = min(this.minX, p_162372_.getX());
        this.minY = Math.min(this.minY, p_162372_.getY());
        this.minZ = min(this.minZ, p_162372_.getZ());
        this.maxX = max(this.maxX, p_162372_.getX());
        this.maxY = Math.max(this.maxY, p_162372_.getY());
        this.maxZ = max(this.maxZ, p_162372_.getZ());
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox move(long pX, int pY, long pZ) {
        return this.move(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    @Deprecated
    public BoundingBox move(Int1088 pX, int pY, Int1088 pZ) {
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
        return this.moved(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ));
    }

    public BoundingBox moved(Int1088 pX, int pY, Int1088 pZ) {
        return new BoundingBox(WorldBounds.addBlockOffset(this.minX, pX), this.minY + pY, WorldBounds.addBlockOffset(this.minZ, pZ), WorldBounds.addBlockOffset(this.maxX, pX), this.maxY + pY, WorldBounds.addBlockOffset(this.maxZ, pZ));
    }

    public BoundingBox inflatedBy(int pValue) {
        return new BoundingBox(WorldBounds.addBlockOffset(this.minX, -(long) pValue), this.minY - pValue, WorldBounds.addBlockOffset(this.minZ, -(long) pValue), WorldBounds.addBlockOffset(this.maxX, pValue), this.maxY + pValue, WorldBounds.addBlockOffset(this.maxZ, pValue));
    }

    public boolean isInside(Vec3i pVector) {
        return WorldBounds.isWithinBlockRange(pVector.getX(), this.minX, this.maxX) && WorldBounds.isWithinBlockRange(pVector.getZ(), this.minZ, this.maxZ) && pVector.getY() >= this.minY && pVector.getY() <= this.maxY;
    }

    public Vec3i getLength() {
        return new Vec3i(this.maxX.subtractSaturated(this.minX), this.maxY - this.minY, this.maxZ.subtractSaturated(this.minZ));
    }

    public Int1088 getXSpan() {
        return WorldBounds.inclusiveBlockSpan(this.minX, this.maxX);
    }

    /** Saturated long projection of the X span for int-indexed shape allocation. */
    public long xSpanAsSaturatedLong() {
        Int1088 span = this.getXSpan();
        return span.fitsLong() ? span.lowLimb() : Long.MAX_VALUE;
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public Int1088 getZSpan() {
        return WorldBounds.inclusiveBlockSpan(this.minZ, this.maxZ);
    }

    /** Saturated long projection of the Z span for int-indexed shape allocation. */
    public long zSpanAsSaturatedLong() {
        Int1088 span = this.getZSpan();
        return span.fitsLong() ? span.lowLimb() : Long.MAX_VALUE;
    }

    public BlockPos getCenter() {
        return new BlockPos(center(this.minX, this.maxX), this.minY + (this.maxY - this.minY + 1) / 2, center(this.minZ, this.maxZ));
    }

    public double getXSpanDouble() {
        return WorldBounds.inclusiveBlockSpanDouble(this.minX, this.maxX);
    }

    public double getZSpanDouble() {
        return WorldBounds.inclusiveBlockSpanDouble(this.minZ, this.maxZ);
    }

    private static Int1088 center(Int1088 min, Int1088 max) {
        return WorldBounds.middleBlockCoordinate(min, max);
    }

    private static Int1088 min(Int1088 first, Int1088 second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static Int1088 max(Int1088 first, Int1088 second) {
        return first.compareTo(second) >= 0 ? first : second;
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
            return this.minX.equals(boundingbox.minX) && this.minY == boundingbox.minY && this.minZ.equals(boundingbox.minZ) && this.maxX.equals(boundingbox.maxX) && this.maxY == boundingbox.maxY && this.maxZ.equals(boundingbox.maxZ);
        }
    }

    public int hashCode() {
        return Objects.hash(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public Int1088 minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public Int1088 minZ() {
        return this.minZ;
    }

    public Int1088 maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public Int1088 maxZ() {
        return this.maxZ;
    }

    /** Serializable endpoint form; each horizontal value uses the 17-limb Int1088 codec. */
    private record Endpoints(Int1088 minX, int minY, Int1088 minZ, Int1088 maxX, int maxY, Int1088 maxZ) {
        private static final Codec<Endpoints> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Int1088.CODEC.fieldOf("minX").forGetter(Endpoints::minX),
                    Codec.INT.fieldOf("minY").forGetter(Endpoints::minY),
                    Int1088.CODEC.fieldOf("minZ").forGetter(Endpoints::minZ),
                    Int1088.CODEC.fieldOf("maxX").forGetter(Endpoints::maxX),
                    Codec.INT.fieldOf("maxY").forGetter(Endpoints::maxY),
                    Int1088.CODEC.fieldOf("maxZ").forGetter(Endpoints::maxZ))
                .apply(instance, Endpoints::new));

        private static Endpoints fromBoundingBox(BoundingBox box) {
            return new Endpoints(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        private BoundingBox toBoundingBox() {
            return new BoundingBox(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }
}
