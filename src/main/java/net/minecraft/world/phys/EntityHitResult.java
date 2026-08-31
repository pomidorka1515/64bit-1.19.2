package net.minecraft.world.phys;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

public class EntityHitResult extends HitResult {
   private final Entity entity;
   @Nullable
   private final SectorVec3 exactLocation;

   public EntityHitResult(Entity p_82439_) {
      this(p_82439_, p_82439_.position(), p_82439_.exactPosition());
   }

   public EntityHitResult(Entity p_82441_, Vec3 p_82442_) {
      this(p_82441_, p_82442_, null);
   }

   /** Creates an entity hit retaining its split-coordinate X/Z location. */
   public EntityHitResult(Entity entity, SectorVec3 exactLocation) {
      this(entity, exactLocation.toApproximateVec3(), exactLocation);
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