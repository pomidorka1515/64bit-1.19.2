package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.teleport.invalidPosition"));

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = pDispatcher.register(Commands.literal("teleport").requires((p_139039_) -> {
                                                                                                                       return p_139039_.hasPermission(2);
                                                                                                                   })
                        .then(Commands.argument("location", Vec3Argument.vec3()).executes((p_139051_) -> {
                            return teleportToPos(p_139051_.getSource(), Collections.singleton(p_139051_.getSource().getEntityOrException()), p_139051_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139051_, "location"), WorldCoordinates.current(), (TeleportCommand.LookAt) null);
                        }))
                        .then(Commands.argument("destination", EntityArgument.entity()).executes((p_139049_) -> {
                            return teleportToEntity(p_139049_.getSource(), Collections.singleton(p_139049_.getSource().getEntityOrException()), EntityArgument.getEntity(p_139049_, "destination"));
                        }))
                        .then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("location", Vec3Argument.vec3()).executes((p_139047_) -> {
                                                                                                                                                 return teleportToPos(p_139047_.getSource(), EntityArgument.getEntities(p_139047_, "targets"), p_139047_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139047_, "location"), (Coordinates) null, (TeleportCommand.LookAt) null);
                                                                                                                                             })
                                                                                                     .then(Commands.argument("rotation", RotationArgument.rotation()).executes((p_139045_) -> {
                                                                                                         return teleportToPos(p_139045_.getSource(), EntityArgument.getEntities(p_139045_, "targets"), p_139045_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139045_, "location"), RotationArgument.getRotation(p_139045_, "rotation"), (TeleportCommand.LookAt) null);
                                                                                                     }))
                                                                                                     .then(Commands.literal("facing").then(Commands.literal("entity").then(Commands.argument("facingEntity", EntityArgument.entity()).executes((p_139043_) -> {
                                                                                                                                                                                                                                         return teleportToPos(p_139043_.getSource(), EntityArgument.getEntities(p_139043_, "targets"), p_139043_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139043_, "location"), (Coordinates) null, new TeleportCommand.LookAt(EntityArgument.getEntity(p_139043_, "facingEntity"), EntityAnchorArgument.Anchor.FEET));
                                                                                                                                                                                                                                     })
                                                                                                                                                           .then(Commands.argument("facingAnchor", EntityAnchorArgument.anchor()).executes((p_139041_) -> {
                                                                                                                                                               return teleportToPos(p_139041_.getSource(), EntityArgument.getEntities(p_139041_, "targets"), p_139041_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139041_, "location"), (Coordinates) null, new TeleportCommand.LookAt(EntityArgument.getEntity(p_139041_, "facingEntity"), EntityAnchorArgument.getAnchor(p_139041_, "facingAnchor")));
                                                                                                                                                           }))))
                                                                                                                     .then(Commands.argument("facingLocation", Vec3Argument.vec3()).executes((p_139037_) -> {
                                                                                                                         return teleportToPos(p_139037_.getSource(), EntityArgument.getEntities(p_139037_, "targets"), p_139037_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139037_, "location"), (Coordinates) null, new TeleportCommand.LookAt(Vec3Argument.getExactVec3(p_139037_, "facingLocation")));
                                                                                                                     }))))
                                        .then(Commands.argument("destination", EntityArgument.entity()).executes((p_139011_) -> {
                                            return teleportToEntity(p_139011_.getSource(), EntityArgument.getEntities(p_139011_, "targets"), EntityArgument.getEntity(p_139011_, "destination"));
                                        }))));
        pDispatcher.register(Commands.literal("tp").requires((p_139013_) -> {
                                                     return p_139013_.hasPermission(2);
                                                 })
                        .redirect(literalcommandnode));
    }

    private static int teleportToEntity(CommandSourceStack pSource, Collection<? extends Entity> pTargets, Entity pDestination) throws CommandSyntaxException {
        for (Entity entity : pTargets) {
            performTeleport(pSource, entity, (ServerLevel) pDestination.level, pDestination.exactPosition(), pDestination.position(), EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class), pDestination.getYRot(), pDestination.getXRot(), (TeleportCommand.LookAt) null);
        }

        if (pTargets.size() == 1) {
            pSource.sendSuccess(Component.translatable("commands.teleport.success.entity.single", pTargets.iterator().next().getDisplayName(), pDestination.getDisplayName()), true);
        } else {
            pSource.sendSuccess(Component.translatable("commands.teleport.success.entity.multiple", pTargets.size(), pDestination.getDisplayName()), true);
        }

        return pTargets.size();
    }

    private static int teleportToPos(CommandSourceStack pSource, Collection<? extends Entity> pTargets, ServerLevel pLevel, Coordinates pPosition, @Nullable Coordinates pRotation, @Nullable TeleportCommand.LookAt pFacing) throws CommandSyntaxException {
        SectorVec3 exact = pPosition.getExactPosition(pSource);
        Vec3 vec3 = exact.toApproximateVec3();
        Vec2 vec2 = pRotation == null ? null : pRotation.getRotation(pSource);
        Set<ClientboundPlayerPositionPacket.RelativeArgument> set = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);
        if (pPosition.isXRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.X);
        }

        if (pPosition.isYRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y);
        }

        if (pPosition.isZRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Z);
        }

        if (pRotation == null) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
        } else {
            if (pRotation.isXRelative()) {
                set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
            }

            if (pRotation.isYRelative()) {
                set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
            }
        }

        for (Entity entity : pTargets) {
            if (pRotation == null) {
                performTeleport(pSource, entity, pLevel, exact, vec3, set, entity.getYRot(), entity.getXRot(), pFacing);
            } else {
                performTeleport(pSource, entity, pLevel, exact, vec3, set, vec2.y, vec2.x, pFacing);
            }
        }

        if (pTargets.size() == 1) {
            pSource.sendSuccess(Component.translatable("commands.teleport.success.location.single", pTargets.iterator().next().getDisplayName(), exact.formatX(6), formatDouble(exact.y()), exact.formatZ(6)), true);
        } else {
            pSource.sendSuccess(Component.translatable("commands.teleport.success.location.multiple", pTargets.size(), exact.formatX(6), formatDouble(exact.y()), exact.formatZ(6)), true);
        }

        return pTargets.size();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static String formatExact(SectorVec3 position) {
        return position.formatX(6) + " " + String.format(Locale.ROOT, "%f", position.y()) + " " + position.formatZ(6);
    }

    private static void performTeleport(CommandSourceStack source, Entity entity, ServerLevel level, @Nullable SectorVec3 exactPosition, Vec3 approximatePosition, Set<ClientboundPlayerPositionPacket.RelativeArgument> relative, float yRot, float xRot, @Nullable TeleportCommand.LookAt lookAt) throws CommandSyntaxException {
        BlockPos blockpos = exactPosition != null ? exactPosition.blockPosition() : new BlockPos(approximatePosition);
        if (!Level.isInSpawnableBounds(blockpos))
            throw INVALID_POSITION.create();
        float wrappedYRot = Mth.wrapDegrees(yRot);
        float wrappedXRot = Mth.wrapDegrees(xRot);
        if (entity instanceof ServerPlayer player) {
            ChunkPos chunkpos = new ChunkPos(blockpos);
            level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, entity.getId());
            entity.stopRiding();
            if (player.isSleeping())
                player.stopSleepInBed(true, true);
            if (level == entity.level) {
                if (exactPosition != null && player.hasSectorPosition())
                    player.connection.teleportExact(exactPosition, wrappedYRot, wrappedXRot, relative, false);
                else
                    player.connection.teleport(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, wrappedXRot, relative);
            } else {
                if (exactPosition != null && player.hasSectorPosition()) {
                    player.teleportTo(level, exactPosition, wrappedYRot, wrappedXRot);
                } else {
                    player.teleportTo(level, approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, wrappedXRot);
                }
            }
            entity.setYHeadRot(wrappedYRot);
        } else {
            float clampedXRot = Mth.clamp(wrappedXRot, -90.0F, 90.0F);
            if (level == entity.level) {
                if (exactPosition != null && entity.hasSectorPosition())
                    entity.absMoveTo(exactPosition, wrappedYRot, clampedXRot);
                else
                    entity.moveTo(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, clampedXRot);
                entity.setYHeadRot(wrappedYRot);
            } else {
                entity.unRide();
                Entity oldEntity = entity;
                entity = entity.getType().create(level);
                if (entity == null)
                    return;
                entity.restoreFrom(oldEntity);
                if (exactPosition != null && entity.hasSectorPosition())
                    entity.applyExactPosition(exactPosition);
                if (exactPosition != null && entity.hasSectorPosition())
                    entity.absMoveTo(exactPosition, wrappedYRot, clampedXRot);
                else
                    entity.moveTo(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, clampedXRot);
                entity.setYHeadRot(wrappedYRot);
                oldEntity.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
                level.addDuringTeleport(entity);
            }
        }
        if (lookAt != null)
            lookAt.perform(source, entity);
        if (!(entity instanceof LivingEntity) || !((LivingEntity) entity).isFallFlying()) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            entity.setOnGround(true);
        }
        if (entity instanceof PathfinderMob mob)
            mob.getNavigation().stop();
    }

    static class LookAt {
        private final Vec3 position;
        @Nullable
        private final SectorVec3 exactPosition;
        private final Entity entity;
        private final EntityAnchorArgument.Anchor anchor;

        public LookAt(Entity entity, EntityAnchorArgument.Anchor anchor) {
            this.entity = entity;
            this.anchor = anchor;
            this.exactPosition = anchor == EntityAnchorArgument.Anchor.EYES && entity.exactEyePosition() != null ? entity.exactEyePosition() : entity.exactPosition();
            this.position = this.exactPosition == null ? anchor.apply(entity) : this.exactPosition.toApproximateVec3();
        }

        public LookAt(Vec3 pPosition) {
            this.entity = null;
            this.exactPosition = null;
            this.position = pPosition;
            this.anchor = null;
        }

        public LookAt(SectorVec3 position) {
            this.entity = null;
            this.exactPosition = position;
            this.position = position.toApproximateVec3();
            this.anchor = null;
        }

        public void perform(CommandSourceStack pSource, Entity pEntity) {
            if (this.entity != null) {
                if (pEntity instanceof ServerPlayer) {
                    ((ServerPlayer) pEntity).lookAt(pSource.getAnchor(), this.entity, this.anchor);
                } else {
                    pEntity.lookAt(pSource.getAnchor(), this.anchor.applyExact(this.entity));
                }
            } else if (this.exactPosition != null && pEntity.hasSectorPosition()) {
                pEntity.lookAt(pSource.getAnchor(), this.exactPosition);
            } else {
                pEntity.lookAt(pSource.getAnchor(), this.position);
            }
        }
    }
}
