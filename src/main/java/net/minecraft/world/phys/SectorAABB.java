package net.minecraft.world.phys;

import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;

/**
 * An X/Z exact world-space axis-aligned box.  X and Z endpoints are stored as
 * block coordinate plus a normalized local fraction; Y remains an ordinary
 * double.  This type deliberately does not replace {@link AABB}.
 */
public final class SectorAABB {
    private static final double EPSILON = 1.0E-7D;

    private final Endpoint minX;
    private final double minY;
    private final Endpoint minZ;
    private final Endpoint maxX;
    private final double maxY;
    private final Endpoint maxZ;

    public SectorAABB(long minBlockX, double minSubX, double minY, long minBlockZ, double minSubZ,
            long maxBlockX, double maxSubX, double maxY, long maxBlockZ, double maxSubZ) {
        this(Int128.fromLong(minBlockX), minSubX, minY, Int128.fromLong(minBlockZ), minSubZ,
                Int128.fromLong(maxBlockX), maxSubX, maxY, Int128.fromLong(maxBlockZ), maxSubZ);
    }

    public SectorAABB(Int128 minBlockX, double minSubX, double minY, Int128 minBlockZ, double minSubZ,
            Int128 maxBlockX, double maxSubX, double maxY, Int128 maxBlockZ, double maxSubZ) {
        Endpoint x0 = Endpoint.of(minBlockX, minSubX);
        Endpoint x1 = Endpoint.of(maxBlockX, maxSubX);
        Endpoint z0 = Endpoint.of(minBlockZ, minSubZ);
        Endpoint z1 = Endpoint.of(maxBlockZ, maxSubZ);
        this.minX = x0.compareTo(x1) <= 0 ? x0 : x1;
        this.maxX = x0.compareTo(x1) <= 0 ? x1 : x0;
        this.minZ = z0.compareTo(z1) <= 0 ? z0 : z1;
        this.maxZ = z0.compareTo(z1) <= 0 ? z1 : z0;
        if (!Double.isFinite(minY) || !Double.isFinite(maxY)) {
            throw new IllegalArgumentException("Y endpoints must be finite");
        }
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
    }

    public static SectorAABB around(SectorVec3 position, double width, double height) {
        if (position == null)
            throw new NullPointerException("position");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 0.0D || height < 0.0D) {
            throw new IllegalArgumentException("Width and height must be finite and non-negative");
        }
        double halfWidth = width * 0.5D;
        Endpoint minX = Endpoint.from(position.blockX(), position.subX()).add(-halfWidth);
        Endpoint maxX = Endpoint.from(position.blockX(), position.subX()).add(halfWidth);
        Endpoint minZ = Endpoint.from(position.blockZ(), position.subZ()).add(-halfWidth);
        Endpoint maxZ = Endpoint.from(position.blockZ(), position.subZ()).add(halfWidth);
        return new SectorAABB(minX.block, minX.fraction, position.y(), minZ.block, minZ.fraction,
                maxX.block, maxX.fraction, position.y() + height, maxZ.block, maxZ.fraction);
    }

    public SectorAABB intersect(SectorAABB other) {
        return fromEndpoints(this.minX.compareTo(other.minX) >= 0 ? this.minX : other.minX,
                Math.max(this.minY, other.minY), this.minZ.compareTo(other.minZ) >= 0 ? this.minZ : other.minZ,
                this.maxX.compareTo(other.maxX) <= 0 ? this.maxX : other.maxX,
                Math.min(this.maxY, other.maxY), this.maxZ.compareTo(other.maxZ) <= 0 ? this.maxZ : other.maxZ);
    }

    public SectorAABB minmax(SectorAABB other) {
        return fromEndpoints(this.minX.compareTo(other.minX) <= 0 ? this.minX : other.minX,
                Math.min(this.minY, other.minY), this.minZ.compareTo(other.minZ) <= 0 ? this.minZ : other.minZ,
                this.maxX.compareTo(other.maxX) >= 0 ? this.maxX : other.maxX,
                Math.max(this.maxY, other.maxY), this.maxZ.compareTo(other.maxZ) >= 0 ? this.maxZ : other.maxZ);
    }

    public boolean intersects(SectorAABB other) {
        return this.minX.compareTo(other.maxX) < 0 && this.maxX.compareTo(other.minX) > 0
                && this.minY < other.maxY && this.maxY > other.minY
                && this.minZ.compareTo(other.maxZ) < 0 && this.maxZ.compareTo(other.minZ) > 0;
    }

    /** Includes the lower endpoint and excludes the upper endpoint, like AABB. */
    public boolean contains(SectorVec3 position) {
        Endpoint x = Endpoint.from(position.blockX(), position.subX());
        Endpoint z = Endpoint.from(position.blockZ(), position.subZ());
        return this.minX.compareTo(x) <= 0 && this.maxX.compareTo(x) > 0
                && this.minY <= position.y() && this.maxY > position.y()
                && this.minZ.compareTo(z) <= 0 && this.maxZ.compareTo(z) > 0;
    }

    public double getXsize() {
        return this.maxX.distanceFrom(this.minX);
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ.distanceFrom(this.minZ);
    }

    public double getSize() {
        return (this.getXsize() + this.getYsize() + this.getZsize()) / 3.0D;
    }

    public SectorVec3 getCenter() {
        Endpoint x = this.minX.midpoint(this.maxX);
        Endpoint z = this.minZ.midpoint(this.maxZ);
        return SectorVec3.fromBlockAndFraction(x.block, x.fraction, this.minY * 0.5D + this.maxY * 0.5D,
                z.block, z.fraction);
    }

    private static SectorAABB fromEndpoints(Endpoint minX, double minY, Endpoint minZ,
            Endpoint maxX, double maxY, Endpoint maxZ) {
        return new SectorAABB(minX.block, minX.fraction, minY, minZ.block, minZ.fraction,
                maxX.block, maxX.fraction, maxY, maxZ.block, maxZ.fraction);
    }

    public Int128 minBlockX() {
        return this.minX.block;
    }
    public double minSubX() {
        return this.minX.fraction;
    }
    public double minY() {
        return this.minY;
    }
    public Int128 minBlockZ() {
        return this.minZ.block;
    }
    public double minSubZ() {
        return this.minZ.fraction;
    }
    public Int128 maxBlockX() {
        return this.maxX.block;
    }
    public double maxSubX() {
        return this.maxX.fraction;
    }
    public double maxY() {
        return this.maxY;
    }
    public Int128 maxBlockZ() {
        return this.maxZ.block;
    }
    public double maxSubZ() {
        return this.maxZ.fraction;
    }

    /** Block range with the same one-block safety margin used by BlockCollisions. */
    public Int128 minBlockXForCollision() {
        return WorldBounds.addBlockOffset(this.minX.floorMinusEpsilon(), -1L);
    }
    public Int128 maxBlockXForCollision() {
        return WorldBounds.addBlockOffset(this.maxX.ceilPlusEpsilon(), 1L);
    }
    public int minBlockYForCollision() {
        return WorldBounds.addSaturated(floorMinusEpsilon(this.minY), -1);
    }
    public int maxBlockYForCollision() {
        return WorldBounds.addSaturated(floorPlusEpsilon(this.maxY), 1);
    }
    public Int128 minBlockZForCollision() {
        return WorldBounds.addBlockOffset(this.minZ.floorMinusEpsilon(), -1L);
    }
    public Int128 maxBlockZForCollision() {
        return WorldBounds.addBlockOffset(this.maxZ.ceilPlusEpsilon(), 1L);
    }

    /** Exact equivalent of floor(minX), used by fluid and inside-block scans. */
    public Int128 minBlockXForRange() {
        return this.minX.block;
    }

    /** Exact exclusive equivalent of ceil(maxX), used by fluid and inside-block scans. */
    public Int128 maxBlockXExclusive() {
        return this.maxBlockXOrZExclusive(this.maxX);
    }

    /** Inclusive upper bound for scans; unlike an exclusive bound it is defined at Int128.MAX. */
    public Int128 maxBlockXForRangeInclusive() {
        return this.maxBlockForRangeInclusive(this.maxX);
    }

    public Int128 minBlockZForRange() {
        return this.minZ.block;
    }

    public Int128 maxBlockZExclusive() {
        return this.maxBlockXOrZExclusive(this.maxZ);
    }

    /** Inclusive upper bound for scans; unlike an exclusive bound it is defined at Int128.MAX. */
    public Int128 maxBlockZForRangeInclusive() {
        return this.maxBlockForRangeInclusive(this.maxZ);
    }

    public int minBlockYForRange() {
        return floorToInt(this.minY);
    }

    public int maxBlockYExclusive() {
        return ceilToInt(this.maxY);
    }

    private Int128 maxBlockXOrZExclusive(Endpoint endpoint) {
        return endpoint.fraction == 0.0D ? endpoint.block : WorldBounds.addBlockOffset(endpoint.block, 1L);
    }

    private Int128 maxBlockForRangeInclusive(Endpoint endpoint) {
        return endpoint.fraction != 0.0D ? endpoint.block : WorldBounds.addBlockOffset(endpoint.block, -1L);
    }

    private static int floorToInt(double value) {
        if (value < Integer.MIN_VALUE || value >= (double) Integer.MAX_VALUE + 1.0D) {
            throw new ArithmeticException("Y range overflow: " + value);
        }
        return (int) Math.floor(value);
    }

    private static int ceilToInt(double value) {
        if (value < (double) Integer.MIN_VALUE || value > (double) Integer.MAX_VALUE) {
            throw new ArithmeticException("Y range overflow: " + value);
        }
        return (int) Math.ceil(value);
    }

    public SectorAABB move(double dx, double dy, double dz) {
        return this.withEndpoints(this.minX.add(dx), this.minY + dy, this.minZ.add(dz),
                this.maxX.add(dx), this.maxY + dy, this.maxZ.add(dz));
    }

    public SectorAABB contract(double dx, double dy, double dz) {
        Endpoint minX = this.minX;
        Endpoint maxX = this.maxX;
        Endpoint minZ = this.minZ;
        Endpoint maxZ = this.maxZ;
        if (dx < 0.0D)
            minX = minX.add(-dx);
        else if (dx > 0.0D)
            maxX = maxX.add(-dx);
        if (dz < 0.0D)
            minZ = minZ.add(-dz);
        else if (dz > 0.0D)
            maxZ = maxZ.add(-dz);
        double minY = this.minY;
        double maxY = this.maxY;
        if (dy < 0.0D)
            minY -= dy;
        else if (dy > 0.0D)
            maxY -= dy;
        return this.withEndpoints(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public SectorAABB expandTowards(double dx, double dy, double dz) {
        Endpoint minX = this.minX;
        Endpoint maxX = this.maxX;
        Endpoint minZ = this.minZ;
        Endpoint maxZ = this.maxZ;
        if (dx < 0.0D)
            minX = minX.add(dx);
        else if (dx > 0.0D)
            maxX = maxX.add(dx);
        if (dz < 0.0D)
            minZ = minZ.add(dz);
        else if (dz > 0.0D)
            maxZ = maxZ.add(dz);
        double minY = this.minY;
        double maxY = this.maxY;
        if (dy < 0.0D)
            minY += dy;
        else if (dy > 0.0D)
            maxY += dy;
        return this.withEndpoints(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public SectorAABB inflate(double x, double y, double z) {
        return this.withEndpoints(this.minX.add(-x), this.minY - y, this.minZ.add(-z),
                this.maxX.add(x), this.maxY + y, this.maxZ.add(z));
    }

    /** Converts this exact box into the small local coordinate frame for one physics operation. */
    public AABB toLocalAABB(SectorPhysicsOrigin origin) {
        if (origin == null)
            throw new NullPointerException("origin");
        this.requireSafeLocalSpan();
        Int128 originX = origin.originBlockX();
        Int128 originZ = origin.originBlockZ();
        return new AABB(this.minX.toLocal(originX), this.minY - origin.originBlockY(),
                this.minZ.toLocal(originZ), this.maxX.toLocal(originX),
                this.maxY - origin.originBlockY(), this.maxZ.toLocal(originZ));
    }

    /**
    * Converts this exact box into coordinates relative to an exact entity or
    * camera position.  Unlike subtracting {@link AABB#minX} from an entity's
    * legacy {@code getX()}, this keeps the sub-block extent intact beyond the
    * precision range of an absolute double.
    */
    public AABB toLocalAABB(SectorVec3 origin) {
        if (origin == null)
            throw new NullPointerException("origin");
        this.requireSafeLocalSpan();
        return new AABB(this.minX.relativeTo(origin.blockX(), origin.subX()), this.minY - origin.y(),
                this.minZ.relativeTo(origin.blockZ(), origin.subZ()),
                this.maxX.relativeTo(origin.blockX(), origin.subX()), this.maxY - origin.y(),
                this.maxZ.relativeTo(origin.blockZ(), origin.subZ()));
    }

    private void requireSafeLocalSpan() {
        if (WorldBounds.localSpanExceeds(this.minX.block, this.maxX.block)
                || WorldBounds.localSpanExceeds(this.minZ.block, this.maxZ.block))
            throw new IllegalArgumentException("Box exceeds safe local physics span");
    }

    /**
    * Sector boxes are immutable value objects. Structural equality is important
    * for pathfinding's per-search collision cache; reference identity would turn
    * each reconstructed clearance box into a cache miss.
    */
    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof SectorAABB))
            return false;
        SectorAABB other = (SectorAABB) object;
        return this.minX.equals(other.minX) && Double.compare(this.minY, other.minY) == 0
                && this.minZ.equals(other.minZ) && this.maxX.equals(other.maxX)
                && Double.compare(this.maxY, other.maxY) == 0 && this.maxZ.equals(other.maxZ);
    }

    @Override
    public int hashCode() {
        int result = this.minX.hashCode();
        result = 31 * result + Double.hashCode(this.minY);
        result = 31 * result + this.minZ.hashCode();
        result = 31 * result + this.maxX.hashCode();
        result = 31 * result + Double.hashCode(this.maxY);
        return 31 * result + this.maxZ.hashCode();
    }

    private SectorAABB withEndpoints(Endpoint minX, double minY, Endpoint minZ,
            Endpoint maxX, double maxY, Endpoint maxZ) {
        return new SectorAABB(minX.block, minX.fraction, minY, minZ.block, minZ.fraction,
                maxX.block, maxX.fraction, maxY, maxZ.block, maxZ.fraction);
    }

    private static int floorMinusEpsilon(double value) {
        double adjusted = value - EPSILON;
        if (adjusted < Integer.MIN_VALUE || adjusted > Integer.MAX_VALUE) {
            throw new ArithmeticException("Y collision range overflow: " + value);
        }
        return (int) Math.floor(adjusted);
    }

    private static int floorPlusEpsilon(double value) {
        double adjusted = value + EPSILON;
        if (adjusted < Integer.MIN_VALUE || adjusted > Integer.MAX_VALUE) {
            throw new ArithmeticException("Y collision range overflow: " + value);
        }
        return (int) Math.floor(adjusted);
    }

    private static final class Endpoint {
        private final Int128 block;
        private final double fraction;

        private Endpoint(Int128 block, double fraction) {
            this.block = block;
            this.fraction = fraction;
        }

        private static Endpoint of(Int128 block, double fraction) {
            SectorVec3 value = SectorVec3.fromBlockAndFraction(block, fraction, 0.0D, Int128.ZERO, 0.0D);
            return new Endpoint(value.blockX(), value.subX());
        }

        private static Endpoint from(Int128 block, double fraction) {
            return of(block, fraction);
        }

        private Endpoint add(double delta) {
            if (!Double.isFinite(delta))
                throw new IllegalArgumentException("Endpoint delta must be finite");
            return of(this.block, this.fraction + delta);
        }

        private int compareTo(Endpoint other) {
            int result = this.block.compareTo(other.block);
            return result != 0 ? result : Double.compare(this.fraction, other.fraction);
        }

        private double distanceFrom(Endpoint other) {
            return WorldBounds.signedDifference(this.block, other.block) + (this.fraction - other.fraction);
        }

        private Endpoint midpoint(Endpoint other) {
            // Floor the integer average without overflowing even across the full domain.
            Int128 block = this.block.and(other.block).add(this.block.xor(other.block).shiftRight(1));
            double halfBlock = ((this.block.lo() ^ other.block.lo()) & 1L) * 0.5D;
            return of(block, halfBlock + this.fraction * 0.5D + other.fraction * 0.5D);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (!(object instanceof Endpoint))
                return false;
            Endpoint other = (Endpoint) object;
            return this.block.equals(other.block) && Double.compare(this.fraction, other.fraction) == 0;
        }

        @Override
        public int hashCode() {
            return 31 * this.block.hashCode() + Double.hashCode(this.fraction);
        }

        private Int128 floorMinusEpsilon() {
            return this.fraction == 0.0D ? WorldBounds.addBlockOffset(this.block, -1L) : this.block;
        }

        private Int128 ceilPlusEpsilon() {
            return this.fraction >= 1.0D - EPSILON ? WorldBounds.addBlockOffset(this.block, 1L) : this.block;
        }

        private double toLocal(Int128 originBlock) {
            return this.relativeTo(originBlock, 0.0D);
        }

        private double relativeTo(Int128 originBlock, double originFraction) {
            double difference = WorldBounds.signedDifferenceExact(this.block, originBlock);
            if (Double.isNaN(difference))
                throw new IllegalArgumentException("Box endpoint exceeds safe local physics span");
            return difference + (this.fraction - originFraction);
        }
    }
}
