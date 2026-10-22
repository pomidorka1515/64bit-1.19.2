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
        return (exact != null ? exact : ExactVec3.position(this.location))
                .relativeTo(pEntity.sectorPosition()).lengthSqr();
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
