package net.minecraft.world.phys;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

public abstract class HitResult {
    protected final Vec3 location;

    protected HitResult(Vec3 pLocation) {
        this.location = pLocation;
    }

    /** Exact split-coordinate location for sector-aware hits; null for legacy hits. */
    @Nullable
    public SectorVec3 getExactLocation() {
        return null;
    }

    public double distanceTo(Entity pEntity) {
        SectorVec3 exact = this.getExactLocation();
        if (exact != null && pEntity.hasSectorPosition()) {
            return exact.relativeTo(pEntity.sectorPosition()).lengthSqr();
        }
        double d0 = this.location.x - pEntity.getX();
        double d1 = this.location.y - pEntity.getY();
        double d2 = this.location.z - pEntity.getZ();
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public abstract HitResult.Type getType();

    public Vec3 getLocation() {
        return this.location;
    }

    public static enum Type {
        MISS,
        BLOCK,
        ENTITY;
    }
}
