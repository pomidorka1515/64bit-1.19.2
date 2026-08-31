package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface EntityGetter {
   List<Entity> getEntities(@Nullable Entity p_45936_, AABB p_45937_, Predicate<? super Entity> p_45938_);

   <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> p_151464_, AABB p_151465_, Predicate<? super T> p_151466_);

   default <T extends Entity> List<T> getEntitiesOfClass(Class<T> p_45979_, AABB p_45980_, Predicate<? super T> p_45981_) {
      SectorAABB exactBox = p_45980_.getSectorBounds();
      if (exactBox != null) {
         return this.getSectorEntities(EntityTypeTest.forClass(p_45979_), exactBox, p_45981_);
      }
      return this.getEntities(EntityTypeTest.forClass(p_45979_), p_45980_, p_45981_);
   }

   List<? extends Player> players();

   /**
    * Exposes the complete entity index for exact local-frame queries. World
    * implementations override this with their section-backed index; pure
    * read-only implementations may return {@code null}.
    */
   @Nullable
   default LevelEntityGetter<Entity> getEntityGetterForSectorQueries() {
      return null;
   }

   default List<Entity> getEntities(@Nullable Entity p_45934_, AABB p_45935_) {
      return this.getEntitiesExactAware(p_45934_, p_45935_, EntitySelector.NO_SPECTATORS);
   }

   /** Uses exact metadata when an entity-centered AABB carries it. */
   default List<Entity> getEntitiesExactAware(@Nullable Entity entity, AABB box,
                                               Predicate<? super Entity> predicate) {
      SectorAABB exactBox = box.getSectorBounds();
      if (exactBox == null) return this.getEntities(entity, box, predicate);
      SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(entity != null ? entity.sectorPosition()
            : SectorVec3.fromBlockAndFraction(exactBox.minBlockX(), exactBox.minSubX(), exactBox.minY(),
                  exactBox.minBlockZ(), exactBox.minSubZ()));
      return this.getSectorEntities(entity, exactBox, exactBox.toLocalAABB(origin), origin, predicate);
   }

   default <T extends Entity> List<T> getSectorEntities(EntityTypeTest<Entity, T> type, SectorAABB exactBox,
                                                         Predicate<? super T> predicate) {
      SectorPhysicsOrigin origin = new SectorPhysicsOrigin(exactBox.minBlockX(), exactBox.minBlockYForRange(),
            exactBox.minBlockZ());
      List<T> result = Lists.newArrayList();
      for (Entity candidate : this.getSectorEntities(null, exactBox, exactBox.toLocalAABB(origin), origin,
            entity -> true)) {
         T cast = type.tryCast(candidate);
         if (cast != null && predicate.test(cast)) result.add(cast);
      }
      return result;
   }

   default boolean isUnobstructed(@Nullable Entity p_45939_, VoxelShape p_45940_) {
      if (p_45940_.isEmpty()) {
         return true;
      } else {
         for(Entity entity : this.getEntities(p_45939_, p_45940_.bounds())) {
            if (!entity.isRemoved() && entity.blocksBuilding && (p_45939_ == null || !entity.isPassengerOfSameVehicle(p_45939_)) && Shapes.joinIsNotEmpty(p_45940_, Shapes.create(entity.getBoundingBox()), BooleanOp.AND)) {
               return false;
            }
         }

         return true;
      }
   }

   default <T extends Entity> List<T> getEntitiesOfClass(Class<T> p_45977_, AABB p_45978_) {
      return this.getEntitiesOfClass(p_45977_, p_45978_, EntitySelector.NO_SPECTATORS);
   }

   default List<VoxelShape> getEntityCollisions(@Nullable Entity p_186451_, AABB p_186452_) {
      if (p_186452_.getSize() < 1.0E-7D) {
         return List.of();
      } else {
         Predicate<Entity> predicate = p_186451_ == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(p_186451_::canCollideWith);
         List<Entity> list = this.getEntities(p_186451_, p_186452_.inflate(1.0E-7D), predicate);
         if (list.isEmpty()) {
            return List.of();
         } else {
            ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size());

            for(Entity entity : list) {
               builder.add(Shapes.create(entity.getBoundingBox()));
            }

            return builder.build();
         }
      }
   }

   /**
    * Returns entities whose exact boxes intersect a sector-physics query. The
    * section overlap is determined in long block coordinates; the final box
    * intersection occurs in the caller's small local frame.
    */
   default List<Entity> getSectorEntities(@Nullable Entity entity, SectorAABB exactBox,
                                          AABB localBox, SectorPhysicsOrigin origin,
                                          Predicate<? super Entity> predicate) {
      LevelEntityGetter<Entity> entityGetter = this.getEntityGetterForSectorQueries();
      if (entityGetter == null) return List.of();
      long minBlockX = WorldBounds.addBlockOffset(exactBox.minBlockXForRange(), -1L);
      long maxBlockX = WorldBounds.addBlockOffset(exactBox.maxBlockXForRangeInclusive(), 1L);
      long minBlockZ = WorldBounds.addBlockOffset(exactBox.minBlockZForRange(), -1L);
      long maxBlockZ = WorldBounds.addBlockOffset(exactBox.maxBlockZForRangeInclusive(), 1L);
      int minBlockY = WorldBounds.addSaturated(exactBox.minBlockYForRange(), -1);
      int maxBlockY = WorldBounds.addSaturated(exactBox.maxBlockYExclusive(), 1);
      long minChunkX = Math.floorDiv(minBlockX, 16L);
      long maxChunkX = Math.floorDiv(maxBlockX, 16L);
      long minChunkZ = Math.floorDiv(minBlockZ, 16L);
      long maxChunkZ = Math.floorDiv(maxBlockZ, 16L);
      int minSectionY = Math.floorDiv(minBlockY, 16);
      int maxSectionY = Math.floorDiv(maxBlockY, 16);
      List<Entity> result = Lists.newArrayList();
      entityGetter.getInSections(minChunkX, maxChunkX, minSectionY, maxSectionY, minChunkZ, maxChunkZ,
            candidate -> {
               addIfInSectorQuery(result, entity, candidate, localBox, origin, predicate,
                     minChunkX, maxChunkX, minSectionY, maxSectionY, minChunkZ, maxChunkZ);
               if (candidate instanceof EnderDragon dragon) {
                  for (EnderDragonPart part : dragon.getSubEntities()) {
                     addIfInSectorQuery(result, entity, part, localBox, origin, predicate,
                           minChunkX, maxChunkX, minSectionY, maxSectionY, minChunkZ, maxChunkZ);
                  }
               }
            });
      return result;
   }

   private static void addIfInSectorQuery(List<Entity> result, @Nullable Entity excluded, Entity candidate,
                                          AABB localBox, SectorPhysicsOrigin origin,
                                          Predicate<? super Entity> predicate,
                                          long minChunkX, long maxChunkX, int minSectionY, int maxSectionY,
                                          long minChunkZ, long maxChunkZ) {
      if (candidate == excluded || !predicate.test(candidate)
            || !isInSectorQueryRange(candidate, minChunkX, maxChunkX, minSectionY, maxSectionY,
                  minChunkZ, maxChunkZ)) return;
      SectorAABB candidateBox = candidate.getSectorBoundingBoxForCulling();
      if (candidateBox != null && candidateBox.toLocalAABB(origin).intersects(localBox)) result.add(candidate);
   }

   default List<VoxelShape> getSectorEntityCollisions(@Nullable Entity entity, SectorAABB exactBox,
                                                       AABB localBox, SectorPhysicsOrigin origin) {
      if (localBox.getSize() < 1.0E-7D) {
         return List.of();
      }

      Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH
            : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
      List<Entity> candidates = this.getSectorEntities(entity, exactBox, localBox, origin, predicate);
      if (candidates.isEmpty()) {
         return List.of();
      }

      ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(candidates.size());
      for (Entity candidate : candidates) {
         SectorAABB candidateBox = candidate.getSectorBoundingBoxForCulling();
         if (candidateBox == null) {
            continue;
         }
         AABB candidateLocalBox = candidateBox.toLocalAABB(origin);
         if (candidateLocalBox.intersects(localBox)) {
            builder.add(Shapes.create(candidateLocalBox));
         }
      }
      return builder.build();
   }

   private static boolean isInSectorQueryRange(Entity entity, long minChunkX, long maxChunkX,
                                                int minSectionY, int maxSectionY,
                                                long minChunkZ, long maxChunkZ) {
      SectorAABB box = entity.getSectorBoundingBoxForCulling();
      if (box == null) {
         return false;
      }
      long minEntityChunkX = Math.floorDiv(box.minBlockXForRange(), 16L);
      long maxEntityChunkX = Math.floorDiv(box.maxBlockXForRangeInclusive(), 16L);
      long minEntityChunkZ = Math.floorDiv(box.minBlockZForRange(), 16L);
      long maxEntityChunkZ = Math.floorDiv(box.maxBlockZForRangeInclusive(), 16L);
      int minEntitySectionY = Math.floorDiv(box.minBlockYForRange(), 16);
      int maxEntitySectionY = Math.floorDiv(box.maxBlockYExclusive(), 16);
      return maxEntityChunkX >= minChunkX && minEntityChunkX <= maxChunkX
            && maxEntityChunkZ >= minChunkZ && minEntityChunkZ <= maxChunkZ
            && maxEntitySectionY >= minSectionY && minEntitySectionY <= maxSectionY;
   }

   @Nullable
   default Player getNearestPlayer(double p_45919_, double p_45920_, double p_45921_, double p_45922_, @Nullable Predicate<Entity> p_45923_) {
      double d0 = -1.0D;
      Player player = null;

      for(Player player1 : this.players()) {
         if (p_45923_ == null || p_45923_.test(player1)) {
            double d1 = player1.distanceToSqr(p_45919_, p_45920_, p_45921_);
            if ((p_45922_ < 0.0D || d1 < p_45922_ * p_45922_) && (d0 == -1.0D || d1 < d0)) {
               d0 = d1;
               player = player1;
            }
         }
      }

      return player;
   }

   @Nullable
   default Player getNearestPlayer(Entity entity, double range) {
      double closest = -1.0D;
      Player nearest = null;
      for (Player player : this.players()) {
         if (!EntitySelector.NO_SPECTATORS.test(player)) continue;
         double distance = entity.distanceToSqr(player);
         if ((range < 0.0D || distance < range * range) && (closest < 0.0D || distance < closest)) {
            closest = distance;
            nearest = player;
         }
      }
      return nearest;
   }

   @Nullable
   default Player getNearestPlayer(double p_45925_, double p_45926_, double p_45927_, double p_45928_, boolean p_45929_) {
      Predicate<Entity> predicate = p_45929_ ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
      return this.getNearestPlayer(p_45925_, p_45926_, p_45927_, p_45928_, predicate);
   }

   default boolean hasNearbyAlivePlayer(double p_45915_, double p_45916_, double p_45917_, double p_45918_) {
      for(Player player : this.players()) {
         if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
            double d0 = player.distanceToSqr(p_45915_, p_45916_, p_45917_);
            if (p_45918_ < 0.0D || d0 < p_45918_ * p_45918_) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   default Player getNearestPlayer(TargetingConditions conditions, LivingEntity source) {
      return this.getNearestEntity(this.players(), conditions, source);
   }

   @Nullable
   default Player getNearestPlayer(TargetingConditions p_45950_, LivingEntity p_45951_, double p_45952_, double p_45953_, double p_45954_) {
      return this.getNearestEntity(this.players(), p_45950_, p_45951_, p_45952_, p_45953_, p_45954_);
   }

   @Nullable
   default Player getNearestPlayer(TargetingConditions p_45942_, double p_45943_, double p_45944_, double p_45945_) {
      return this.getNearestEntity(this.players(), p_45942_, (LivingEntity)null, p_45943_, p_45944_, p_45945_);
   }

   @Nullable
   default <T extends LivingEntity> T getNearestEntity(Class<? extends T> p_45964_, TargetingConditions p_45965_, @Nullable LivingEntity p_45966_, double p_45967_, double p_45968_, double p_45969_, AABB p_45970_) {
      return this.getNearestEntity(this.getEntitiesOfClass(p_45964_, p_45970_, (p_186454_) -> {
         return true;
      }), p_45965_, p_45966_, p_45967_, p_45968_, p_45969_);
   }

   @Nullable
   default <T extends LivingEntity> T getNearestEntity(List<? extends T> entities,
                                                        TargetingConditions conditions,
                                                        @Nullable LivingEntity source) {
      double closest = -1.0D;
      T nearest = null;
      for (T candidate : entities) {
         if (conditions.test(source, candidate)) {
            double distance = source == null ? 0.0D : source.distanceToSqr(candidate);
            if (closest == -1.0D || distance < closest) {
               closest = distance;
               nearest = candidate;
            }
         }
      }
      return nearest;
   }

   @Nullable
   default <T extends LivingEntity> T getNearestEntity(List<? extends T> p_45983_, TargetingConditions p_45984_, @Nullable LivingEntity p_45985_, double p_45986_, double p_45987_, double p_45988_) {
      if (p_45985_ != null) return this.getNearestEntity(p_45983_, p_45984_, p_45985_);
      double d0 = -1.0D;
      T t = null;

      for(T t1 : p_45983_) {
         if (p_45984_.test(null, t1)) {
            double d1 = t1.distanceToSqr(p_45986_, p_45987_, p_45988_);
            if (d0 == -1.0D || d1 < d0) {
               d0 = d1;
               t = t1;
            }
         }
      }

      return t;
   }

   default List<Player> getNearbyPlayers(TargetingConditions p_45956_, LivingEntity p_45957_, AABB p_45958_) {
      List<Player> list = Lists.newArrayList();

      for(Player player : this.players()) {
         if (p_45958_.contains(player.getX(), player.getY(), player.getZ()) && p_45956_.test(p_45957_, player)) {
            list.add(player);
         }
      }

      return list;
   }

   default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> p_45972_, TargetingConditions p_45973_, LivingEntity p_45974_, AABB p_45975_) {
      List<T> list = this.getEntitiesOfClass(p_45972_, p_45975_, (p_186450_) -> {
         return true;
      });
      List<T> list1 = Lists.newArrayList();

      for(T t : list) {
         if (p_45973_.test(p_45974_, t)) {
            list1.add(t);
         }
      }

      return list1;
   }

   @Nullable
   default Player getPlayerByUUID(UUID p_46004_) {
      for(int i = 0; i < this.players().size(); ++i) {
         Player player = this.players().get(i);
         if (p_46004_.equals(player.getUUID())) {
            return player;
         }
      }

      return null;
   }
}