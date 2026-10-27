package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;

/** Immutable origin for a single local player-physics operation (not the camera origin). */
public final class SectorPhysicsOrigin {
    private final Int1088 originBlockX;
    private final int originBlockY;
    private final Int1088 originBlockZ;

    public SectorPhysicsOrigin(long originBlockX, int originBlockY, long originBlockZ) {
        this(Int1088.fromLong(originBlockX), originBlockY, Int1088.fromLong(originBlockZ));
    }

    public SectorPhysicsOrigin(Int1088 originBlockX, int originBlockY, Int1088 originBlockZ) {
        if (originBlockX == null || originBlockZ == null)
            throw new NullPointerException("origin blocks");
        this.originBlockX = originBlockX;
        this.originBlockY = originBlockY;
        this.originBlockZ = originBlockZ;
    }

    public static SectorPhysicsOrigin from(SectorVec3 position) {
        if (position == null)
            throw new NullPointerException("position");
        return new SectorPhysicsOrigin(position.blockX(), position.blockPosition().getY(), position.blockZ());
    }

    public Int1088 originBlockX() {
        return this.originBlockX;
    }
    public int originBlockY() {
        return this.originBlockY;
    }
    public Int1088 originBlockZ() {
        return this.originBlockZ;
    }

    public Vec3 toLocal(BlockPos blockPosition) {
        this.requireSafe(blockPosition.getX(), blockPosition.getZ());
        return new Vec3(WorldBounds.signedDifference(blockPosition.getX(), this.originBlockX),
                (double) blockPosition.getY() - this.originBlockY,
                WorldBounds.signedDifference(blockPosition.getZ(), this.originBlockZ));
    }

    public Vec3 toLocal(SectorVec3 position) {
        this.requireSafe(position.blockX(), position.blockZ());
        return position.toLocal(this.originBlockX, this.originBlockY, this.originBlockZ);
    }

    public boolean isSafe(Int1088 blockX, Int1088 blockZ) {
        return !WorldBounds.localSpanExceeds(blockX, this.originBlockX)
                && !WorldBounds.localSpanExceeds(blockZ, this.originBlockZ);
    }

    private void requireSafe(Int1088 blockX, Int1088 blockZ) {
        if (!this.isSafe(blockX, blockZ))
            throw new IllegalArgumentException("Position exceeds safe local physics span");
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof SectorPhysicsOrigin))
            return false;
        SectorPhysicsOrigin other = (SectorPhysicsOrigin) object;
        return this.originBlockX.equals(other.originBlockX) && this.originBlockY == other.originBlockY
                && this.originBlockZ.equals(other.originBlockZ);
    }

    @Override
    public int hashCode() {
        int result = this.originBlockX.hashCode();
        result = 31 * result + this.originBlockY;
        return 31 * result + this.originBlockZ.hashCode();
    }
}
