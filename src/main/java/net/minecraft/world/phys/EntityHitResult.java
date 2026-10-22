package net.minecraft.world.phys;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

public class EntityHitResult extends HitResult {
    private final Entity entity;
    @Nullable
    private final SectorVec3 exactLocation;

    public EntityHitResult(Entity pEntity) {
        this(pEntity, pEntity.exactPosition());
    }

    public EntityHitResult(Entity pEntity, Vec3 pLocation) {
        this(pEntity, pLocation, pLocation instanceof ExactVec3 ? ExactVec3.position(pLocation) : null);
    }

    /** Creates an entity hit retaining its split-coordinate X/Z location. */
    public EntityHitResult(Entity entity, SectorVec3 exactLocation) {
        this(entity, exactLocation.toExactVec3(), exactLocation);
    }

    private EntityHitResult(Entity entity, Vec3 location, @Nullable SectorVec3 exactLocation) {
        super(location);
        this.entity = entity;
        this.exactLocation = exactLocation;
    }

    public Entity getEntity() {
        return this.entity;
    }

    @Override
    @Nullable
    public SectorVec3 getExactLocation() {
        return this.exactLocation;
    }

    public HitResult.Type getType() {
        return HitResult.Type.ENTITY;
    }
}
