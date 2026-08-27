package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;

/** Immutable origin for a single local player-physics operation (not the camera origin). */
public final class SectorPhysicsOrigin {
   private final long originBlockX;
   private final int originBlockY;
   private final long originBlockZ;

   public SectorPhysicsOrigin(long originBlockX, int originBlockY, long originBlockZ) {
      this.originBlockX = originBlockX;
      this.originBlockY = originBlockY;
      this.originBlockZ = originBlockZ;
   }

   public static SectorPhysicsOrigin from(SectorVec3 position) {
      if (position == null) throw new NullPointerException("position");
      return new SectorPhysicsOrigin(position.blockX(), position.blockPosition().getY(), position.blockZ());
   }

   public long originBlockX() { return this.originBlockX; }
   public int originBlockY() { return this.originBlockY; }
   public long originBlockZ() { return this.originBlockZ; }

   public Vec3 toLocal(BlockPos blockPosition) {
      return new Vec3(WorldBounds.signedDifference(blockPosition.getX(), this.originBlockX),
            (double)blockPosition.getY() - this.originBlockY,
            WorldBounds.signedDifference(blockPosition.getZ(), this.originBlockZ));
   }

   public Vec3 toLocal(SectorVec3 position) {
      return position.toLocal(this.originBlockX, this.originBlockY, this.originBlockZ);
   }

   @Override
   public boolean equals(Object object) {
      if (this == object) return true;
      if (!(object instanceof SectorPhysicsOrigin)) return false;
      SectorPhysicsOrigin other = (SectorPhysicsOrigin)object;
      return this.originBlockX == other.originBlockX && this.originBlockY == other.originBlockY
            && this.originBlockZ == other.originBlockZ;
   }

   @Override
   public int hashCode() {
      int result = Long.hashCode(this.originBlockX);
      result = 31 * result + this.originBlockY;
      return 31 * result + Long.hashCode(this.originBlockZ);
   }
}
