package net.minecraft.commands.arguments.selector;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Int1088;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {
    public static final int INFINITE = Integer.MAX_VALUE;
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity p_175109_) {
            return p_175109_;
        }

        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final Predicate<Entity> predicate;
    private final MinMaxBounds.Doubles range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final String exactX;
    @Nullable
    private final Double exactY;
    @Nullable
    private final String exactZ;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    @Nullable
    private SectorVec3 exactOriginForSorting;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    private EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(int pMaxResults, boolean pIncludeEntities, boolean pWorldLimited, Predicate<Entity> pPredicate, MinMaxBounds.Doubles pRange, Function<Vec3, Vec3> pPositions, @Nullable String exactX, @Nullable Double exactY, @Nullable String exactZ, @Nullable AABB pAabb, BiConsumer<Vec3, List<? extends Entity>> pOrder, boolean pCurrentEntity, @Nullable String pPlayerName, @Nullable UUID pEntityUUID, @Nullable EntityType<?> pType, boolean pUsesSelector) {
        this.maxResults = pMaxResults;
        this.includesEntities = pIncludeEntities;
        this.worldLimited = pWorldLimited;
        this.predicate = pPredicate;
        this.range = pRange;
        this.position = pPositions;
        this.exactX = exactX;
        this.exactY = exactY;
        this.exactZ = exactZ;
        this.aabb = pAabb;
        this.order = pOrder;
        this.currentEntity = pCurrentEntity;
        this.playerName = pPlayerName;
        this.entityUUID = pEntityUUID;
        this.type = (EntityTypeTest<Entity, ?>) (pType == null ? ANY_TYPE : pType);
        this.usesSelector = pUsesSelector;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack pSource) throws CommandSyntaxException {
        if (this.usesSelector && !pSource.hasPermission(2)) {
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack pSource) throws CommandSyntaxException {
        this.checkPermissions(pSource);
        List<? extends Entity> list = this.findEntities(pSource);
        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack pSource) throws CommandSyntaxException {
        this.checkPermissions(pSource);
        if (!this.includesEntities) {
            return this.findPlayers(pSource);
        } else if (this.playerName != null) {
            ServerPlayer serverplayer = pSource.getServer().getPlayerList().getPlayerByName(this.playerName);
         return (List<? extends Entity>)(serverplayer == null ? Collections.emptyList() : Lists.newArrayList(serverplayer));
        } else if (this.entityUUID != null) {
            for (ServerLevel serverlevel1 : pSource.getServer().getAllLevels()) {
                Entity entity = serverlevel1.getEntity(this.entityUUID);
                if (entity != null) {
                    return Lists.newArrayList(entity);
                }
            }

            return Collections.emptyList();
        } else {
            SectorVec3 exactOrigin = this.exactOrigin(pSource);
            Vec3 vec3 = exactOrigin.toExactVec3();
            this.exactOriginForSorting = exactOrigin;
            Predicate<Entity> predicate = this.getPredicate(vec3, exactOrigin);
            if (this.currentEntity) {
            return (List<? extends Entity>)(pSource.getEntity() != null && predicate.test(pSource.getEntity()) ? Lists.newArrayList(pSource.getEntity()) : Collections.emptyList());
            } else {
                List<Entity> list = Lists.newArrayList();
                if (this.isWorldLimited()) {
                    this.addEntities(list, pSource.getLevel(), exactOrigin, predicate);
                } else {
                    for (ServerLevel serverlevel : pSource.getServer().getAllLevels()) {
                        this.addEntities(list, serverlevel, exactOrigin, predicate);
                    }
                }

                return this.sortAndLimit(vec3, list);
            }
        }
    }

    private void addEntities(List<Entity> pResult, ServerLevel pLevel, SectorVec3 pPos, Predicate<Entity> pPredicate) {
        if (this.aabb != null) {
            pResult.addAll(pLevel.getEntities(this.type, AABB.fromSectorBounds(this.exactBox(pPos)), pPredicate));
        } else {
            pResult.addAll(pLevel.getEntities(this.type, pPredicate));
        }
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack pSource) throws CommandSyntaxException {
        this.checkPermissions(pSource);
        List<ServerPlayer> list = this.findPlayers(pSource);
        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack pSource) throws CommandSyntaxException {
        this.checkPermissions(pSource);
        if (this.playerName != null) {
            ServerPlayer serverplayer2 = pSource.getServer().getPlayerList().getPlayerByName(this.playerName);
            return (List<ServerPlayer>) (serverplayer2 == null ? Collections.emptyList() : Lists.newArrayList(serverplayer2));
        } else if (this.entityUUID != null) {
            ServerPlayer serverplayer1 = pSource.getServer().getPlayerList().getPlayer(this.entityUUID);
            return (List<ServerPlayer>) (serverplayer1 == null ? Collections.emptyList() : Lists.newArrayList(serverplayer1));
        } else {
            SectorVec3 exactOrigin = this.exactOrigin(pSource);
            Vec3 vec3 = exactOrigin.toExactVec3();
            this.exactOriginForSorting = exactOrigin;
            Predicate<Entity> predicate = this.getPredicate(vec3, exactOrigin);
            if (this.currentEntity) {
                if (pSource.getEntity() instanceof ServerPlayer) {
                    ServerPlayer serverplayer3 = (ServerPlayer) pSource.getEntity();
                    if (predicate.test(serverplayer3)) {
                        return Lists.newArrayList(serverplayer3);
                    }
                }

                return Collections.emptyList();
            } else {
                List<ServerPlayer> list;
                if (this.isWorldLimited()) {
                    list = pSource.getLevel().getPlayers(predicate);
                } else {
                    list = Lists.newArrayList();

                    for (ServerPlayer serverplayer : pSource.getServer().getPlayerList().getPlayers()) {
                        if (predicate.test(serverplayer)) {
                            list.add(serverplayer);
                        }
                    }
                }

                return this.sortAndLimit(vec3, list);
            }
        }
    }

    private SectorVec3 exactOrigin(CommandSourceStack source) {
        SectorVec3 result = source.getExactPosition();
        if (this.exactX != null) {
            var coordinate = net.minecraft.commands.arguments.coordinates.WorldCoordinate.absoluteDecimal(this.exactX, false);
            result = SectorVec3.fromBlockAndFraction(coordinate.getAbsoluteBlock(), coordinate.getAbsoluteFraction(), result.y(), result.blockZ(), result.subZ());
        }
        if (this.exactZ != null) {
            var coordinate = net.minecraft.commands.arguments.coordinates.WorldCoordinate.absoluteDecimal(this.exactZ, false);
            result = SectorVec3.fromBlockAndFraction(result.blockX(), result.subX(), result.y(), coordinate.getAbsoluteBlock(), coordinate.getAbsoluteFraction());
        }
        return this.exactY == null ? result : result.withY(this.exactY);
    }

    private Predicate<Entity> getPredicate(Vec3 pPos, SectorVec3 exactOrigin) {
        Predicate<Entity> predicate = this.predicate;
        if (this.aabb != null) {
            SectorAABB aabb = this.exactBox(exactOrigin);
            predicate = predicate.and((entity) -> {
                return entity.getSectorBoundingBox().intersects(aabb);
            });
        }

        if (!this.range.isAny()) {
            predicate = predicate.and((entity) -> {
                SectorVec3 position = entity.sectorPosition();
                double squared = distanceSquared(position, exactOrigin);
                // Preserve ordinary squared comparisons, but do not compare infinities
                // produced by squaring otherwise finite wide distances/range bounds.
                if (Double.isFinite(squared)
                        && (this.range.getMin() == null || Double.isFinite(this.range.getMin() * this.range.getMin()))
                        && (this.range.getMax() == null || Double.isFinite(this.range.getMax() * this.range.getMax()))) {
                    return this.range.matchesSqr(squared);
                }
                return this.range.matches(distance(position, exactOrigin));
            });
        }

        return predicate;
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 origin, List<T> entities) {
        if (entities.size() > 1) {
            if (this.exactOriginForSorting != null && this.order != EntitySelectorParser.ORDER_RANDOM && this.order != EntitySelectorParser.ORDER_ARBITRARY) {
                entities.sort((first, second) -> {
                    double firstDistance = exactDistance(first);
                    double secondDistance = exactDistance(second);
                    int comparison = Double.compare(firstDistance, secondDistance);
                    if (Double.isInfinite(firstDistance) && Double.isInfinite(secondDistance)) {
                        firstDistance = distance(first.sectorPosition(), this.exactOriginForSorting);
                        secondDistance = distance(second.sectorPosition(), this.exactOriginForSorting);
                        comparison = Double.compare(firstDistance, secondDistance);
                        if (Double.isInfinite(firstDistance) && Double.isInfinite(secondDistance)) {
                            comparison = Double.compare(scaledDistance(first.sectorPosition(), this.exactOriginForSorting),
                                    scaledDistance(second.sectorPosition(), this.exactOriginForSorting));
                        }
                    }
                    return EntitySelectorParser.ORDER_FURTHEST.equals(this.order) ? -comparison : comparison;
                });
            } else {
                this.order.accept(origin, entities);
            }
        }
        return entities.subList(0, Math.min(this.maxResults, entities.size()));
    }

    private double exactDistance(Entity entity) {
        SectorVec3 origin = this.exactOriginForSorting;
        if (origin == null)
            return Double.POSITIVE_INFINITY;
        return distanceSquared(entity.sectorPosition(), origin);
    }

    private SectorAABB exactBox(SectorVec3 origin) {
        SectorVec3 min = origin.add(this.aabb.minX, this.aabb.minY, this.aabb.minZ);
        SectorVec3 max = origin.add(this.aabb.maxX, this.aabb.maxY, this.aabb.maxZ);
        return new SectorAABB(min.blockX(), min.subX(), min.y(), min.blockZ(), min.subZ(),
                max.blockX(), max.subX(), max.y(), max.blockZ(), max.subZ());
    }

    private static double distanceSquared(SectorVec3 position, SectorVec3 origin) {
        double x = horizontalDifference(position.blockX(), origin.blockX()) + position.subX() - origin.subX();
        double z = horizontalDifference(position.blockZ(), origin.blockZ()) + position.subZ() - origin.subZ();
        double y = position.y() - origin.y();
        return x * x + y * y + z * z;
    }

    private static double distance(SectorVec3 position, SectorVec3 origin) {
        double x = horizontalDifference(position.blockX(), origin.blockX()) + position.subX() - origin.subX();
        double z = horizontalDifference(position.blockZ(), origin.blockZ()) + position.subZ() - origin.subZ();
        return Math.hypot(Math.hypot(x, z), position.y() - origin.y());
    }

    private static double horizontalDifference(Int1088 value, Int1088 origin) {
        long local = WorldBounds.signedDifferenceAsLong(value, origin);
        if (local != Long.MIN_VALUE && local != Long.MAX_VALUE) {
            return (double) local;
        }
        // This span helper subtracts wide integers before projecting and retains
        // magnitude even when the difference exceeds the signed coordinate domain.
        return value.compareTo(origin) >= 0
                ? WorldBounds.inclusiveBlockSpanDouble(origin, value) - 1.0D
                : -(WorldBounds.inclusiveBlockSpanDouble(value, origin) - 1.0D);
    }

    private static double scaledDistance(SectorVec3 position, SectorVec3 origin) {
        // Only used when both distances exceed Double.MAX_VALUE. A common
        // power-of-two scale preserves their order without overflowing doubles.
        double x = horizontalDifference(position.blockX().shiftRight(128), origin.blockX().shiftRight(128));
        double z = horizontalDifference(position.blockZ().shiftRight(128), origin.blockZ().shiftRight(128));
        return Math.hypot(Math.hypot(x, z), Math.scalb(position.y(), -128) - Math.scalb(origin.y(), -128));
    }

    public static Component joinNames(List<? extends Entity> pNames) {
        return ComponentUtils.formatList(pNames, Entity::getDisplayName);
    }
}
