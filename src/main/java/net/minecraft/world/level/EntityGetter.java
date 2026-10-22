package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Int128;
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
    List<Entity> getEntities(@Nullable Entity pEntity, AABB pArea, Predicate<? super Entity> pPredicate);

    <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> pEntityTypeTest, AABB pArea, Predicate<? super T> pPredicate);

    default<T extends Entity> List<T> getEntitiesOfClass(Class<T> pClazz, AABB pArea, Predicate<? super T> pFilter) {
        SectorAABB exactBox = pArea.getSectorBounds();
        if (exactBox != null) {
            return this.getSectorEntities(EntityTypeTest.forClass(pClazz), exactBox, pFilter);
        }
        return this.getEntities(EntityTypeTest.forClass(pClazz), pArea, pFilter);
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

    default List<Entity> getEntities(@Nullable Entity pEntity, AABB pArea) {
        return this.getEntitiesExactAware(pEntity, pArea, EntitySelector.NO_SPECTATORS);
    }

    /** Uses exact metadata when an entity-centered AABB carries it. */
    default List<Entity> getEntitiesExactAware(@Nullable Entity entity, AABB box,
            Predicate<? super Entity> predicate) {
        SectorAABB exactBox = box.getSectorBounds();
        if (exactBox == null)
            return this.getEntities(entity, box, predicate);
        SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(entity != null ? entity.sectorPosition()
                                                                             : SectorVec3.fromBlockAndFraction(exactBox.minBlockX(), exactBox.minSubX(), exactBox.minY(),
                                                                                       exactBox.minBlockZ(), exactBox.minSubZ()));
        return this.getSectorEntities(entity, exactBox, exactBox.toLocalAABB(origin), origin, predicate);
    }

    default<T extends Entity> List<T> getSectorEntities(EntityTypeTest<Entity, T> type, SectorAABB exactBox,
            Predicate<? super T> predicate) {
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(exactBox.minBlockX(), exactBox.minBlockYForRange(),
                exactBox.minBlockZ());
        List<T> result = Lists.newArrayList();
        for (Entity candidate : this.getSectorEntities(null, exactBox, exactBox.toLocalAABB(origin), origin,
                     entity -> true)) {
            T cast = type.tryCast(candidate);
            if (cast != null && predicate.test(cast))
                result.add(cast);
        }
        return result;
    }

    default boolean isUnobstructed(@Nullable Entity pEntity, VoxelShape pShape) {
        if (pShape.isEmpty()) {
            return true;
        } else {
            for (Entity entity : this.getEntities(pEntity, pShape.bounds())) {
                if (!entity.isRemoved() && entity.blocksBuilding && (pEntity == null || !entity.isPassengerOfSameVehicle(pEntity)) && Shapes.joinIsNotEmpty(pShape, Shapes.create(entity.exactBoundingBox()), BooleanOp.AND)) {
                    return false;
                }
            }

            return true;
        }
    }

    default<T extends Entity> List<T> getEntitiesOfClass(Class<T> pEntityClass, AABB pArea) {
        return this.getEntitiesOfClass(pEntityClass, pArea, EntitySelector.NO_SPECTATORS);
    }

    default List<VoxelShape> getEntityCollisions(@Nullable Entity pEntity, AABB pCollisionBox) {
        if (pCollisionBox.getSize() < 1.0E-7D) {
            return List.of();
        } else {
            Predicate<Entity> predicate = pEntity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(pEntity::canCollideWith);
            List<Entity> list = this.getEntities(pEntity, pCollisionBox.inflate(1.0E-7D), predicate);
            if (list.isEmpty()) {
                return List.of();
            } else {
                ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size());

                for (Entity entity : list) {
                    builder.add(Shapes.create(entity.exactBoundingBox()));
                }

                return builder.build();
            }
        }
    }

    /**
    * Returns entities whose exact boxes intersect a sector-physics query. The
    * section overlap is determined in full-width block coordinates; the final box
    * intersection occurs in the caller's small local frame.
    */
    default List<Entity> getSectorEntities(@Nullable Entity entity, SectorAABB exactBox,
            AABB localBox, SectorPhysicsOrigin origin,
            Predicate<? super Entity> predicate) {
        LevelEntityGetter<Entity> entityGetter = this.getEntityGetterForSectorQueries();
        if (entityGetter == null)
            return List.of();
        Int128 minBlockX = WorldBounds.addBlockOffset(exactBox.minBlockXForRange(), -1L);
        Int128 maxBlockX = WorldBounds.addBlockOffset(exactBox.maxBlockXForRangeInclusive(), 1L);
        Int128 minBlockZ = WorldBounds.addBlockOffset(exactBox.minBlockZForRange(), -1L);
        Int128 maxBlockZ = WorldBounds.addBlockOffset(exactBox.maxBlockZForRangeInclusive(), 1L);
        int minBlockY = WorldBounds.addSaturated(exactBox.minBlockYForRange(), -1);
        int maxBlockY = WorldBounds.addSaturated(exactBox.maxBlockYExclusive(), 1);
        Int128 minChunkX = minBlockX.shiftRight(4);
        Int128 maxChunkX = maxBlockX.shiftRight(4);
        Int128 minChunkZ = minBlockZ.shiftRight(4);
        Int128 maxChunkZ = maxBlockZ.shiftRight(4);
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
            Int128 minChunkX, Int128 maxChunkX, int minSectionY, int maxSectionY,
            Int128 minChunkZ, Int128 maxChunkZ) {
        if (candidate == excluded || !predicate.test(candidate)
                || !isInSectorQueryRange(candidate, minChunkX, maxChunkX, minSectionY, maxSectionY,
                        minChunkZ, maxChunkZ))
            return;
        SectorAABB candidateBox = candidate.getSectorBoundingBoxForCulling();
        if (candidateBox != null && candidateBox.toLocalAABB(origin).intersects(localBox))
            result.add(candidate);
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

    private static boolean isInSectorQueryRange(Entity entity, Int128 minChunkX, Int128 maxChunkX,
            int minSectionY, int maxSectionY,
            Int128 minChunkZ, Int128 maxChunkZ) {
        SectorAABB box = entity.getSectorBoundingBoxForCulling();
        if (box == null) {
            return false;
        }
        Int128 minEntityChunkX = box.minBlockXForRange().shiftRight(4);
        Int128 maxEntityChunkX = box.maxBlockXForRangeInclusive().shiftRight(4);
        Int128 minEntityChunkZ = box.minBlockZForRange().shiftRight(4);
        Int128 maxEntityChunkZ = box.maxBlockZForRangeInclusive().shiftRight(4);
        int minEntitySectionY = Math.floorDiv(box.minBlockYForRange(), 16);
        int maxEntitySectionY = Math.floorDiv(box.maxBlockYExclusive(), 16);
        return maxEntityChunkX.compareTo(minChunkX) >= 0 && minEntityChunkX.compareTo(maxChunkX) <= 0
                && maxEntityChunkZ.compareTo(minChunkZ) >= 0 && minEntityChunkZ.compareTo(maxChunkZ) <= 0
                && maxEntitySectionY >= minSectionY && minEntitySectionY <= maxSectionY;
    }

    @Nullable
    default Player getNearestPlayer(SectorVec3 position, double range, boolean excludeCreative) {
        Predicate<Entity> predicate = excludeCreative ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        double closest = -1.0D;
        Player nearest = null;
        for (Player player : this.players()) {
            if (predicate.test(player)) {
                double distance = player.sectorPosition().relativeTo(position).lengthSqr();
                if ((range < 0.0D || distance < range * range) && (closest < 0.0D || distance < closest)) {
                    closest = distance;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    default boolean hasNearbyAlivePlayer(SectorVec3 position, double range) {
        for (Player player : this.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)
                    && (range < 0.0D || player.sectorPosition().relativeTo(position).lengthSqr() < range * range)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    default Player getNearestPlayer(double pX, double pY, double pZ, double pDistance, @Nullable Predicate<Entity> pPredicate) {
        double d0 = -1.0D;
        Player player = null;

        for (Player player1 : this.players()) {
            if (pPredicate == null || pPredicate.test(player1)) {
                double d1 = player1.distanceToSqr(pX, pY, pZ);
                if ((pDistance < 0.0D || d1 < pDistance * pDistance) && (d0 == -1.0D || d1 < d0)) {
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
            if (!EntitySelector.NO_SPECTATORS.test(player))
                continue;
            double distance = entity.distanceToSqr(player);
            if ((range < 0.0D || distance < range * range) && (closest < 0.0D || distance < closest)) {
                closest = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    @Nullable
    default Player getNearestPlayer(double pX, double pY, double pZ, double pDistance, boolean pCreativePlayers) {
        Predicate<Entity> predicate = pCreativePlayers ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return this.getNearestPlayer(pX, pY, pZ, pDistance, predicate);
    }

    default boolean hasNearbyAlivePlayer(double pX, double pY, double pZ, double pDistance) {
        for (Player player : this.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d0 = player.distanceToSqr(pX, pY, pZ);
                if (pDistance < 0.0D || d0 < pDistance * pDistance) {
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
    default Player getNearestPlayer(TargetingConditions conditions, @Nullable LivingEntity source, SectorVec3 position) {
        return this.getNearestEntity(this.players(), conditions, source, position);
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions pPredicate, LivingEntity pTarget, double pX, double pY, double pZ) {
        return this.getNearestEntity(this.players(), pPredicate, pTarget, pX, pY, pZ);
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions pPredicate, double pX, double pY, double pZ) {
        return this.getNearestEntity(this.players(), pPredicate, (LivingEntity) null, pX, pY, pZ);
    }

    @Nullable
    default<T extends LivingEntity> T getNearestEntity(Class<? extends T> pEntityClazz, TargetingConditions pConditions, @Nullable LivingEntity pTarget, double pX, double pY, double pZ, AABB pBoundingBox) {
        return this.getNearestEntity(this.getEntitiesOfClass(pEntityClazz, pBoundingBox, (p_186454_) -> {
            return true;
        }), pConditions, pTarget, pX, pY, pZ);
    }

    @Nullable
    default<T extends LivingEntity> T getNearestEntity(Class<? extends T> type, TargetingConditions conditions,
            @Nullable LivingEntity source, SectorVec3 position, AABB box) {
        return this.getNearestEntity(this.getEntitiesOfClass(type, box, entity -> true), conditions, source, position);
    }

    @Nullable
    default<T extends LivingEntity> T getNearestEntity(List<? extends T> entities,
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
    default<T extends LivingEntity> T getNearestEntity(List<? extends T> entities,
            TargetingConditions conditions, @Nullable LivingEntity source, SectorVec3 position) {
        double closest = -1.0D;
        T nearest = null;
        for (T candidate : entities) {
            if (conditions.test(source, candidate)) {
                double distance = candidate.sectorPosition().relativeTo(position).lengthSqr();
                if (closest < 0.0D || distance < closest) {
                    closest = distance;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    @Nullable
    default<T extends LivingEntity> T getNearestEntity(List<? extends T> pEntities, TargetingConditions pPredicate, @Nullable LivingEntity pTarget, double pX, double pY, double pZ) {
        if (pTarget != null)
            return this.getNearestEntity(pEntities, pPredicate, pTarget);
        double d0 = -1.0D;
        T t = null;

        for (T t1 : pEntities) {
            if (pPredicate.test(null, t1)) {
                double d1 = t1.distanceToSqr(pX, pY, pZ);
                if (d0 == -1.0D || d1 < d0) {
                    d0 = d1;
                    t = t1;
                }
            }
        }

        return t;
    }

    default List<Player> getNearbyPlayers(TargetingConditions pPredicate, LivingEntity pTarget, AABB pArea) {
        List<Player> list = Lists.newArrayList();

        for (Player player : this.players()) {
            SectorAABB exactBox = pArea.getSectorBounds();
            boolean contains = exactBox == null ? pArea.contains(player.exactPositionVector())
                    : exactBox.contains(player.sectorPosition());
            if (contains && pPredicate.test(pTarget, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default<T extends LivingEntity> List<T> getNearbyEntities(Class<T> pEntityClazz, TargetingConditions pEntityPredicate, LivingEntity pEntity, AABB pArea) {
        List<T> list = this.getEntitiesOfClass(pEntityClazz, pArea, (p_186450_) -> {
            return true;
        });
        List<T> list1 = Lists.newArrayList();

        for (T t : list) {
            if (pEntityPredicate.test(pEntity, t)) {
                list1.add(t);
            }
        }

        return list1;
    }

    @Nullable
    default Player getPlayerByUUID(UUID pUniqueId) {
        for (int i = 0; i < this.players().size(); ++i) {
            Player player = this.players().get(i);
            if (pUniqueId.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }
}
