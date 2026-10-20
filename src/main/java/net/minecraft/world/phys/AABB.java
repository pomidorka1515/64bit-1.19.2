package net.minecraft.world.phys;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class AABB {
    private static final double EPSILON = 1.0E-7D;
    /**
    * Optional exact world-space bounds. Entity-centered boxes retain this value
    * through the ordinary inflate/expand/move operations so legacy gameplay
    * queries can use the exact-coordinate entity index without losing X/Z bits.
    */
    @Nullable
    private final SectorAABB sectorBounds;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double pX1, double pY1, double pZ1, double pX2, double pY2, double pZ2) {
        this(pX1, pY1, pZ1, pX2, pY2, pZ2, null);
    }

    private AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            @Nullable SectorAABB sectorBounds) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.sectorBounds = sectorBounds;
    }

    /** Creates the lossy compatibility mirror of an exact horizontal box. */
    public static AABB fromSectorBounds(SectorAABB bounds) {
        return new AABB(bounds);
    }

    private AABB(SectorAABB bounds) {
        this(approximateCorner(bounds, false), approximateCorner(bounds, true), bounds);
    }

    private AABB(Vec3 min, Vec3 max, SectorAABB bounds) {
        this(min.x, min.y, min.z, max.x, max.y, max.z, bounds);
    }

    private static Vec3 approximateCorner(SectorAABB bounds, boolean maximum) {
        if (bounds == null)
            throw new NullPointerException("bounds");
        return SectorVec3.fromBlockAndFraction(
                maximum ? bounds.maxBlockX() : bounds.minBlockX(),
                maximum ? bounds.maxSubX() : bounds.minSubX(),
                maximum ? bounds.maxY() : bounds.minY(),
                maximum ? bounds.maxBlockZ() : bounds.minBlockZ(),
                maximum ? bounds.maxSubZ() : bounds.minSubZ()).toApproximateVec3();
    }

    /** Exact metadata carried by entity-centered compatibility boxes. */
    @Nullable
    public SectorAABB getSectorBounds() {
        return this.sectorBounds;
    }

    public AABB(BlockPos pPos) {
        this(new SectorAABB(pPos.getX(), 0.0D, pPos.getY(), pPos.getZ(), 0.0D,
                        pPos.getX(), 0.0D, pPos.getY() + 1.0D, pPos.getZ(), 0.0D).expandTowards(1.0D, 0.0D, 1.0D));
    }

    public AABB(BlockPos pStart, BlockPos pEnd) {
        this(new SectorAABB(pStart.getX(), 0.0D, pStart.getY(), pStart.getZ(), 0.0D,
                pEnd.getX(), 0.0D, pEnd.getY(), pEnd.getZ(), 0.0D));
    }

    public AABB(Vec3 pStart, Vec3 pEnd) {
        this(pStart.x, pStart.y, pStart.z, pEnd.x, pEnd.y, pEnd.z);
    }

    public static AABB of(BoundingBox pMutableBox) {
        return fromSectorBounds(new SectorAABB(pMutableBox.minX(), 0.0D, pMutableBox.minY(), pMutableBox.minZ(), 0.0D,
                pMutableBox.maxX(), 0.0D, pMutableBox.maxY() + 1.0D, pMutableBox.maxZ(), 0.0D)
                .expandTowards(1.0D, 0.0D, 1.0D));
    }

    public static AABB unitCubeFromLowerCorner(Vec3 pVector) {
        return new AABB(pVector.x, pVector.y, pVector.z, pVector.x + 1.0D, pVector.y + 1.0D, pVector.z + 1.0D);
    }

    public AABB setMinX(double pMinX) {
        if (this.sectorBounds != null) return this.withExactEndpoint(0, pMinX);
        return new AABB(pMinX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double pMinY) {
        if (this.sectorBounds != null) return this.withExactEndpoint(1, pMinY);
        return new AABB(this.minX, pMinY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double pMinZ) {
        if (this.sectorBounds != null) return this.withExactEndpoint(2, pMinZ);
        return new AABB(this.minX, this.minY, pMinZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double pMaxX) {
        if (this.sectorBounds != null) return this.withExactEndpoint(3, pMaxX);
        return new AABB(this.minX, this.minY, this.minZ, pMaxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double pMaxY) {
        if (this.sectorBounds != null) return this.withExactEndpoint(4, pMaxY);
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, pMaxY, this.maxZ);
    }

    public AABB setMaxZ(double pMaxZ) {
        if (this.sectorBounds != null) return this.withExactEndpoint(5, pMaxZ);
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, pMaxZ);
    }

    private AABB withExactEndpoint(int endpoint, double value) {
        SectorAABB b = this.sectorBounds;
        double previous = switch (endpoint) {
            case 0 -> this.minX;
            case 1 -> this.minY;
            case 2 -> this.minZ;
            case 3 -> this.maxX;
            case 4 -> this.maxY;
            default -> this.maxZ;
        };
        if (value == previous) return this;
        SectorVec3 replacement = SectorVec3.fromApproximate(
                endpoint == 0 || endpoint == 3 ? value : 0.0D,
                endpoint == 1 || endpoint == 4 ? value : 0.0D,
                endpoint == 2 || endpoint == 5 ? value : 0.0D);
        return fromSectorBounds(new SectorAABB(
                endpoint == 0 ? replacement.blockX() : b.minBlockX(), endpoint == 0 ? replacement.subX() : b.minSubX(),
                endpoint == 1 ? value : b.minY(),
                endpoint == 2 ? replacement.blockZ() : b.minBlockZ(), endpoint == 2 ? replacement.subZ() : b.minSubZ(),
                endpoint == 3 ? replacement.blockX() : b.maxBlockX(), endpoint == 3 ? replacement.subX() : b.maxSubX(),
                endpoint == 4 ? value : b.maxY(),
                endpoint == 5 ? replacement.blockZ() : b.maxBlockZ(), endpoint == 5 ? replacement.subZ() : b.maxSubZ()));
    }

    public double min(Direction.Axis pAxis) {
        return pAxis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis pAxis) {
        return pAxis.choose(this.maxX, this.maxY, this.maxZ);
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof AABB)) {
            return false;
        } else {
            AABB aabb = (AABB) pOther;
            if (Double.compare(aabb.minX, this.minX) != 0) {
                return false;
            } else if (Double.compare(aabb.minY, this.minY) != 0) {
                return false;
            } else if (Double.compare(aabb.minZ, this.minZ) != 0) {
                return false;
            } else if (Double.compare(aabb.maxX, this.maxX) != 0) {
                return false;
            } else if (Double.compare(aabb.maxY, this.maxY) != 0) {
                return false;
            } else {
                return Double.compare(aabb.maxZ, this.maxZ) == 0;
            }
        }
    }

    public int hashCode() {
        long i = Double.doubleToLongBits(this.minX);
        int j = (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.minY);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.minZ);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxX);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxY);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxZ);
        return 31 * j + (int) (i ^ i >>> 32);
    }

    public AABB contract(double pX, double pY, double pZ) {
        double d0 = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (pX < 0.0D) {
            d0 -= pX;
        } else if (pX > 0.0D) {
            d3 -= pX;
        }

        if (pY < 0.0D) {
            d1 -= pY;
        } else if (pY > 0.0D) {
            d4 -= pY;
        }

        if (pZ < 0.0D) {
            d2 -= pZ;
        } else if (pZ > 0.0D) {
            d5 -= pZ;
        }

        SectorAABB exact = this.sectorBounds == null ? null : this.sectorBounds.contract(pX, pY, pZ);
        return new AABB(d0, d1, d2, d3, d4, d5, exact);
    }

    public AABB expandTowards(Vec3 pVector) {
        return this.expandTowards(pVector.x, pVector.y, pVector.z);
    }

    public AABB expandTowards(double pX, double pY, double pZ) {
        double d0 = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (pX < 0.0D) {
            d0 += pX;
        } else if (pX > 0.0D) {
            d3 += pX;
        }

        if (pY < 0.0D) {
            d1 += pY;
        } else if (pY > 0.0D) {
            d4 += pY;
        }

        if (pZ < 0.0D) {
            d2 += pZ;
        } else if (pZ > 0.0D) {
            d5 += pZ;
        }

        SectorAABB exact = this.sectorBounds == null ? null
                                                     : this.sectorBounds.expandTowards(pX, pY, pZ);
        return new AABB(d0, d1, d2, d3, d4, d5, exact);
    }

    public AABB inflate(double pX, double pY, double pZ) {
        double d0 = this.minX - pX;
        double d1 = this.minY - pY;
        double d2 = this.minZ - pZ;
        double d3 = this.maxX + pX;
        double d4 = this.maxY + pY;
        double d5 = this.maxZ + pZ;
        SectorAABB exact = this.sectorBounds == null ? null
                                                     : this.sectorBounds.inflate(pX, pY, pZ);
        return new AABB(d0, d1, d2, d3, d4, d5, exact);
    }

    public AABB inflate(double pValue) {
        return this.inflate(pValue, pValue, pValue);
    }

    public AABB intersect(AABB pOther) {
        if (this.sectorBounds != null || pOther.sectorBounds != null) {
            return fromSectorBounds(this.exactBounds().intersect(pOther.exactBounds()));
        }
        double d0 = Math.max(this.minX, pOther.minX);
        double d1 = Math.max(this.minY, pOther.minY);
        double d2 = Math.max(this.minZ, pOther.minZ);
        double d3 = Math.min(this.maxX, pOther.maxX);
        double d4 = Math.min(this.maxY, pOther.maxY);
        double d5 = Math.min(this.maxZ, pOther.maxZ);
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    public AABB minmax(AABB pOther) {
        if (this.sectorBounds != null || pOther.sectorBounds != null) {
            return fromSectorBounds(this.exactBounds().minmax(pOther.exactBounds()));
        }
        double d0 = Math.min(this.minX, pOther.minX);
        double d1 = Math.min(this.minY, pOther.minY);
        double d2 = Math.min(this.minZ, pOther.minZ);
        double d3 = Math.max(this.maxX, pOther.maxX);
        double d4 = Math.max(this.maxY, pOther.maxY);
        double d5 = Math.max(this.maxZ, pOther.maxZ);
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    private SectorAABB exactBounds() {
        if (this.sectorBounds != null) return this.sectorBounds;
        SectorVec3 min = SectorVec3.fromApproximate(this.minX, this.minY, this.minZ);
        SectorVec3 max = SectorVec3.fromApproximate(this.maxX, this.maxY, this.maxZ);
        return new SectorAABB(min.blockX(), min.subX(), min.y(), min.blockZ(), min.subZ(),
                max.blockX(), max.subX(), max.y(), max.blockZ(), max.subZ());
    }

    public AABB move(double pX, double pY, double pZ) {
        SectorAABB exact = this.sectorBounds == null ? null
                                                     : this.sectorBounds.move(pX, pY, pZ);
        return new AABB(this.minX + pX, this.minY + pY, this.minZ + pZ,
                this.maxX + pX, this.maxY + pY, this.maxZ + pZ, exact);
    }

    public AABB move(BlockPos pPos) {
        SectorAABB b = this.exactBounds();
        return fromSectorBounds(new SectorAABB(
                WorldBounds.addBlockOffset(b.minBlockX(), pPos.getX()), b.minSubX(),
                b.minY() + pPos.getY(),
                WorldBounds.addBlockOffset(b.minBlockZ(), pPos.getZ()), b.minSubZ(),
                WorldBounds.addBlockOffset(b.maxBlockX(), pPos.getX()), b.maxSubX(),
                b.maxY() + pPos.getY(),
                WorldBounds.addBlockOffset(b.maxBlockZ(), pPos.getZ()), b.maxSubZ()));
    }

    public AABB move(Vec3 pVec) {
        return this.move(pVec.x, pVec.y, pVec.z);
    }

    public boolean intersects(AABB pOther) {
        if (this.sectorBounds != null || pOther.sectorBounds != null) {
            return this.exactBounds().intersects(pOther.exactBounds());
        }
        return this.intersects(pOther.minX, pOther.minY, pOther.minZ, pOther.maxX, pOther.maxY, pOther.maxZ);
    }

    public boolean intersects(double pX1, double pY1, double pZ1, double pX2, double pY2, double pZ2) {
        return this.minX<pX2&& this.maxX> pX1 && this.minY<pY2&& this.maxY> pY1 && this.minZ<pZ2&& this.maxZ> pZ1;
    }

    public boolean intersects(Vec3 pMin, Vec3 pMax) {
        return this.intersects(Math.min(pMin.x, pMax.x), Math.min(pMin.y, pMax.y), Math.min(pMin.z, pMax.z), Math.max(pMin.x, pMax.x), Math.max(pMin.y, pMax.y), Math.max(pMin.z, pMax.z));
    }

    public boolean contains(Vec3 pVec) {
        return this.contains(pVec.x, pVec.y, pVec.z);
    }

    public boolean contains(double pX, double pY, double pZ) {
        return pX >= this.minX && pX < this.maxX && pY >= this.minY && pY < this.maxY && pZ >= this.minZ && pZ < this.maxZ;
    }

    public double getSize() {
        double d0 = this.getXsize();
        double d1 = this.getYsize();
        double d2 = this.getZsize();
        return (d0 + d1 + d2) / 3.0D;
    }

    public double getXsize() {
        if (this.sectorBounds != null) {
            return WorldBounds.signedDifference(this.sectorBounds.maxBlockX(), this.sectorBounds.minBlockX())
                    + this.sectorBounds.maxSubX() - this.sectorBounds.minSubX();
        }
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        if (this.sectorBounds != null) {
            return WorldBounds.signedDifference(this.sectorBounds.maxBlockZ(), this.sectorBounds.minBlockZ())
                    + this.sectorBounds.maxSubZ() - this.sectorBounds.minSubZ();
        }
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double pX, double pY, double pZ) {
        return this.inflate(-pX, -pY, -pZ);
    }

    public AABB deflate(double pValue) {
        return this.inflate(-pValue);
    }

    public Optional<Vec3> clip(Vec3 pFrom, Vec3 pTo) {
        double[] adouble = new double[] {1.0D};
        double d0 = pTo.x - pFrom.x;
        double d1 = pTo.y - pFrom.y;
        double d2 = pTo.z - pFrom.z;
        Direction direction = getDirection(this, pFrom, adouble, (Direction) null, d0, d1, d2);
        if (direction == null) {
            return Optional.empty();
        } else {
            double d3 = adouble[0];
            return Optional.of(pFrom.add(d3 * d0, d3 * d1, d3 * d2));
        }
    }

    @Nullable
    public static BlockHitResult clip(Iterable<AABB> pBoxes, Vec3 pStart, Vec3 pEnd, BlockPos pPos) {
        double[] adouble = new double[] {1.0D};
        Direction direction = null;
        double d0 = pEnd.x - pStart.x;
        double d1 = pEnd.y - pStart.y;
        double d2 = pEnd.z - pStart.z;

        for (AABB aabb : pBoxes) {
            direction = getDirection(aabb.move(pPos), pStart, adouble, direction, d0, d1, d2);
        }

        if (direction == null) {
            return null;
        } else {
            double d3 = adouble[0];
            return new BlockHitResult(pStart.add(d3 * d0, d3 * d1, d3 * d2), direction, pPos, false);
        }
    }

    @Nullable
    private static Direction getDirection(AABB pAabb, Vec3 pStart, double[] pMinDistance, @Nullable Direction pFacing, double pDeltaX, double pDeltaY, double pDeltaZ) {
        if (pDeltaX > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaX, pDeltaY, pDeltaZ, pAabb.minX, pAabb.minY, pAabb.maxY, pAabb.minZ, pAabb.maxZ, Direction.WEST, pStart.x, pStart.y, pStart.z);
        } else if (pDeltaX < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaX, pDeltaY, pDeltaZ, pAabb.maxX, pAabb.minY, pAabb.maxY, pAabb.minZ, pAabb.maxZ, Direction.EAST, pStart.x, pStart.y, pStart.z);
        }

        if (pDeltaY > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaY, pDeltaZ, pDeltaX, pAabb.minY, pAabb.minZ, pAabb.maxZ, pAabb.minX, pAabb.maxX, Direction.DOWN, pStart.y, pStart.z, pStart.x);
        } else if (pDeltaY < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaY, pDeltaZ, pDeltaX, pAabb.maxY, pAabb.minZ, pAabb.maxZ, pAabb.minX, pAabb.maxX, Direction.UP, pStart.y, pStart.z, pStart.x);
        }

        if (pDeltaZ > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaZ, pDeltaX, pDeltaY, pAabb.minZ, pAabb.minX, pAabb.maxX, pAabb.minY, pAabb.maxY, Direction.NORTH, pStart.z, pStart.x, pStart.y);
        } else if (pDeltaZ < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaZ, pDeltaX, pDeltaY, pAabb.maxZ, pAabb.minX, pAabb.maxX, pAabb.minY, pAabb.maxY, Direction.SOUTH, pStart.z, pStart.x, pStart.y);
        }

        return pFacing;
    }

    @Nullable
    private static Direction clipPoint(double[] pMinDistance, @Nullable Direction pPrevDirection, double pDistanceSide, double pDistanceOtherA, double pDistanceOtherB, double pMinSide, double pMinOtherA, double pMaxOtherA, double pMinOtherB, double pMaxOtherB, Direction pHitSide, double pStartSide, double pStartOtherA, double pStartOtherB) {
        double d0 = (pMinSide - pStartSide) / pDistanceSide;
        double d1 = pStartOtherA + d0 * pDistanceOtherA;
        double d2 = pStartOtherB + d0 * pDistanceOtherB;
        if (0.0D < d0 && d0 < pMinDistance[0] && pMinOtherA - 1.0E-7D < d1 && d1 < pMaxOtherA + 1.0E-7D && pMinOtherB - 1.0E-7D < d2 && d2 < pMaxOtherB + 1.0E-7D) {
            pMinDistance[0] = d0;
            return pHitSide;
        } else {
            return pPrevDirection;
        }
    }

    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5D, this.minX, this.maxX), Mth.lerp(0.5D, this.minY, this.maxY), Mth.lerp(0.5D, this.minZ, this.maxZ));
    }

    public static AABB ofSize(Vec3 pCenter, double pXSize, double pYSize, double pZSize) {
        return new AABB(pCenter.x - pXSize / 2.0D, pCenter.y - pYSize / 2.0D, pCenter.z - pZSize / 2.0D, pCenter.x + pXSize / 2.0D, pCenter.y + pYSize / 2.0D, pCenter.z + pZSize / 2.0D);
    }
}
