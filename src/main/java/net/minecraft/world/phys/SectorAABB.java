package net.minecraft.world.phys;

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
      if (position == null) throw new NullPointerException("position");
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

   public long minBlockX() { return this.minX.block; }
   public double minSubX() { return this.minX.fraction; }
   public double minY() { return this.minY; }
   public long minBlockZ() { return this.minZ.block; }
   public double minSubZ() { return this.minZ.fraction; }
   public long maxBlockX() { return this.maxX.block; }
   public double maxSubX() { return this.maxX.fraction; }
   public double maxY() { return this.maxY; }
   public long maxBlockZ() { return this.maxZ.block; }
   public double maxSubZ() { return this.maxZ.fraction; }

   /** Block range with the same one-block safety margin used by BlockCollisions. */
   public long minBlockXForCollision() { return WorldBounds.addBlockOffset(this.minX.floorMinusEpsilon(), -1L); }
   public long maxBlockXForCollision() { return WorldBounds.addBlockOffset(this.maxX.ceilPlusEpsilon(), 1L); }
   public int minBlockYForCollision() { return WorldBounds.addSaturated(floorMinusEpsilon(this.minY), -1); }
   public int maxBlockYForCollision() { return WorldBounds.addSaturated(floorPlusEpsilon(this.maxY), 1); }
   public long minBlockZForCollision() { return WorldBounds.addBlockOffset(this.minZ.floorMinusEpsilon(), -1L); }
   public long maxBlockZForCollision() { return WorldBounds.addBlockOffset(this.maxZ.ceilPlusEpsilon(), 1L); }


   /** Exact equivalent of floor(minX), used by fluid and inside-block scans. */
   public long minBlockXForRange() { return this.minX.block; }

   /** Exact exclusive equivalent of ceil(maxX), used by fluid and inside-block scans. */
   public long maxBlockXExclusive() { return this.maxBlockXOrZExclusive(this.maxX); }

   /** Inclusive upper bound for scans; unlike an exclusive bound it is defined at Long.MAX_VALUE. */
   public long maxBlockXForRangeInclusive() { return this.maxBlockForRangeInclusive(this.maxX); }

   public long minBlockZForRange() { return this.minZ.block; }

   public long maxBlockZExclusive() { return this.maxBlockXOrZExclusive(this.maxZ); }

   /** Inclusive upper bound for scans; unlike an exclusive bound it is defined at Long.MAX_VALUE. */
   public long maxBlockZForRangeInclusive() { return this.maxBlockForRangeInclusive(this.maxZ); }

   public int minBlockYForRange() { return floorToInt(this.minY); }

   public int maxBlockYExclusive() { return ceilToInt(this.maxY); }

   private long maxBlockXOrZExclusive(Endpoint endpoint) {
      return endpoint.fraction == 0.0D ? endpoint.block : WorldBounds.addBlockOffset(endpoint.block, 1L);
   }

   private long maxBlockForRangeInclusive(Endpoint endpoint) {
      return endpoint.fraction != 0.0D ? endpoint.block : WorldBounds.addBlockOffset(endpoint.block, -1L);
   }

   private static int floorToInt(double value) {
      if (value < Integer.MIN_VALUE || value >= (double)Integer.MAX_VALUE + 1.0D) {
         throw new ArithmeticException("Y range overflow: " + value);
      }
      return (int)Math.floor(value);
   }

   private static int ceilToInt(double value) {
      if (value < (double)Integer.MIN_VALUE || value > (double)Integer.MAX_VALUE) {
         throw new ArithmeticException("Y range overflow: " + value);
      }
      return (int)Math.ceil(value);
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
      if (dx < 0.0D) minX = minX.add(-dx); else if (dx > 0.0D) maxX = maxX.add(-dx);
      if (dz < 0.0D) minZ = minZ.add(-dz); else if (dz > 0.0D) maxZ = maxZ.add(-dz);
      double minY = this.minY;
      double maxY = this.maxY;
      if (dy < 0.0D) minY -= dy; else if (dy > 0.0D) maxY -= dy;
      return this.withEndpoints(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public SectorAABB expandTowards(double dx, double dy, double dz) {
      Endpoint minX = this.minX;
      Endpoint maxX = this.maxX;
      Endpoint minZ = this.minZ;
      Endpoint maxZ = this.maxZ;
      if (dx < 0.0D) minX = minX.add(dx); else if (dx > 0.0D) maxX = maxX.add(dx);
      if (dz < 0.0D) minZ = minZ.add(dz); else if (dz > 0.0D) maxZ = maxZ.add(dz);
      double minY = this.minY;
      double maxY = this.maxY;
      if (dy < 0.0D) minY += dy; else if (dy > 0.0D) maxY += dy;
      return this.withEndpoints(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public SectorAABB inflate(double x, double y, double z) {
      return this.withEndpoints(this.minX.add(-x), this.minY - y, this.minZ.add(-z),
            this.maxX.add(x), this.maxY + y, this.maxZ.add(z));
   }

   /** Converts this exact box into the small local coordinate frame for one physics operation. */
   public AABB toLocalAABB(SectorPhysicsOrigin origin) {
      if (origin == null) throw new NullPointerException("origin");
      return new AABB(this.minX.toLocal(origin.originBlockX()), this.minY - origin.originBlockY(),
            this.minZ.toLocal(origin.originBlockZ()), this.maxX.toLocal(origin.originBlockX()),
            this.maxY - origin.originBlockY(), this.maxZ.toLocal(origin.originBlockZ()));
   }

   /**
    * Sector boxes are immutable value objects. Structural equality is important
    * for pathfinding's per-search collision cache; reference identity would turn
    * each reconstructed clearance box into a cache miss.
    */
   @Override
   public boolean equals(Object object) {
      if (this == object) return true;
      if (!(object instanceof SectorAABB)) return false;
      SectorAABB other = (SectorAABB)object;
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
      return (int)Math.floor(adjusted);
   }

   private static int floorPlusEpsilon(double value) {
      double adjusted = value + EPSILON;
      if (adjusted < Integer.MIN_VALUE || adjusted > Integer.MAX_VALUE) {
         throw new ArithmeticException("Y collision range overflow: " + value);
      }
      return (int)Math.floor(adjusted);
   }

   private static final class Endpoint {
      private final long block;
      private final double fraction;

      private Endpoint(long block, double fraction) {
         this.block = block;
         this.fraction = fraction;
      }

      private static Endpoint of(long block, double fraction) {
         SectorVec3 value = SectorVec3.fromBlockAndFraction(block, fraction, 0.0D, 0L, 0.0D);
         return new Endpoint(value.blockX(), value.subX());
      }

      private static Endpoint from(long block, double fraction) { return of(block, fraction); }

      private Endpoint add(double delta) {
         if (!Double.isFinite(delta)) throw new IllegalArgumentException("Endpoint delta must be finite");
         double newFraction = this.fraction + delta;
         double carry = Math.floor(newFraction);
         if (!Double.isFinite(carry) || carry < Long.MIN_VALUE || carry >= 0x1.0p63 || carry != Math.rint(carry)) {
            // A finite but enormous movement is still a valid hostile input;
            // place the endpoint at the nearest legal edge instead of allowing
            // a long narrowing conversion to wrap.
            return delta < 0.0D ? new Endpoint(Long.MIN_VALUE, 0.0D)
                  : new Endpoint(Long.MAX_VALUE, Math.nextDown(1.0D));
         }
         long carryLong = (long)carry;
         long normalizedBlock = WorldBounds.addBlockOffset(this.block, carryLong);
         if (carryLong > 0L && normalizedBlock <= this.block) {
            return new Endpoint(Long.MAX_VALUE, Math.nextDown(1.0D));
         }
         if (carryLong < 0L && normalizedBlock >= this.block) {
            return new Endpoint(Long.MIN_VALUE, 0.0D);
         }
         return of(normalizedBlock, newFraction - carry);
      }

      private int compareTo(Endpoint other) {
         int result = Long.compare(this.block, other.block);
         return result != 0 ? result : Double.compare(this.fraction, other.fraction);
      }

      @Override
      public boolean equals(Object object) {
         if (this == object) return true;
         if (!(object instanceof Endpoint)) return false;
         Endpoint other = (Endpoint)object;
         return this.block == other.block && Double.compare(this.fraction, other.fraction) == 0;
      }

      @Override
      public int hashCode() {
         return 31 * Long.hashCode(this.block) + Double.hashCode(this.fraction);
      }

      private long floorMinusEpsilon() {
         return this.fraction == 0.0D ? WorldBounds.addBlockOffset(this.block, -1L) : this.block;
      }

      private long ceilPlusEpsilon() {
         return this.fraction >= 1.0D - EPSILON ? WorldBounds.addBlockOffset(this.block, 1L) : this.block;
      }

      private double toLocal(long originBlock) {
         return WorldBounds.signedDifference(this.block, originBlock) + this.fraction;
      }
   }
}
