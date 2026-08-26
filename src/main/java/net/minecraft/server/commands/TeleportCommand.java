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

   public static void register(CommandDispatcher<CommandSourceStack> p_139009_) {
      LiteralCommandNode<CommandSourceStack> literalcommandnode = p_139009_.register(Commands.literal("teleport").requires((p_139039_) -> {
         return p_139039_.hasPermission(2);
      }).then(Commands.argument("location", Vec3Argument.vec3()).executes((p_139051_) -> {
         return teleportToPos(p_139051_.getSource(), Collections.singleton(p_139051_.getSource().getEntityOrException()), p_139051_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139051_, "location"), WorldCoordinates.current(), (TeleportCommand.LookAt)null);
      })).then(Commands.argument("destination", EntityArgument.entity()).executes((p_139049_) -> {
         return teleportToEntity(p_139049_.getSource(), Collections.singleton(p_139049_.getSource().getEntityOrException()), EntityArgument.getEntity(p_139049_, "destination"));
      })).then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("location", Vec3Argument.vec3()).executes((p_139047_) -> {
         return teleportToPos(p_139047_.getSource(), EntityArgument.getEntities(p_139047_, "targets"), p_139047_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139047_, "location"), (Coordinates)null, (TeleportCommand.LookAt)null);
      }).then(Commands.argument("rotation", RotationArgument.rotation()).executes((p_139045_) -> {
         return teleportToPos(p_139045_.getSource(), EntityArgument.getEntities(p_139045_, "targets"), p_139045_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139045_, "location"), RotationArgument.getRotation(p_139045_, "rotation"), (TeleportCommand.LookAt)null);
      })).then(Commands.literal("facing").then(Commands.literal("entity").then(Commands.argument("facingEntity", EntityArgument.entity()).executes((p_139043_) -> {
         return teleportToPos(p_139043_.getSource(), EntityArgument.getEntities(p_139043_, "targets"), p_139043_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139043_, "location"), (Coordinates)null, new TeleportCommand.LookAt(EntityArgument.getEntity(p_139043_, "facingEntity"), EntityAnchorArgument.Anchor.FEET));
      }).then(Commands.argument("facingAnchor", EntityAnchorArgument.anchor()).executes((p_139041_) -> {
         return teleportToPos(p_139041_.getSource(), EntityArgument.getEntities(p_139041_, "targets"), p_139041_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139041_, "location"), (Coordinates)null, new TeleportCommand.LookAt(EntityArgument.getEntity(p_139041_, "facingEntity"), EntityAnchorArgument.getAnchor(p_139041_, "facingAnchor")));
      })))).then(Commands.argument("facingLocation", Vec3Argument.vec3()).executes((p_139037_) -> {
         return teleportToPos(p_139037_.getSource(), EntityArgument.getEntities(p_139037_, "targets"), p_139037_.getSource().getLevel(), Vec3Argument.getCoordinates(p_139037_, "location"), (Coordinates)null, new TeleportCommand.LookAt(Vec3Argument.getExactVec3(p_139037_, "facingLocation")));
      })))).then(Commands.argument("destination", EntityArgument.entity()).executes((p_139011_) -> {
         return teleportToEntity(p_139011_.getSource(), EntityArgument.getEntities(p_139011_, "targets"), EntityArgument.getEntity(p_139011_, "destination"));
      }))));
      p_139009_.register(Commands.literal("tp").requires((p_139013_) -> {
         return p_139013_.hasPermission(2);
      }).redirect(literalcommandnode));
   }

   private static int teleportToEntity(CommandSourceStack p_139033_, Collection<? extends Entity> p_139034_, Entity p_139035_) throws CommandSyntaxException {
      for(Entity entity : p_139034_) {
         performTeleport(p_139033_, entity, (ServerLevel)p_139035_.level, p_139035_.exactPosition(), p_139035_.position(), EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class), p_139035_.getYRot(), p_139035_.getXRot(), (TeleportCommand.LookAt)null);
      }

      if (p_139034_.size() == 1) {
         p_139033_.sendSuccess(Component.translatable("commands.teleport.success.entity.single", p_139034_.iterator().next().getDisplayName(), p_139035_.getDisplayName()), true);
      } else {
         p_139033_.sendSuccess(Component.translatable("commands.teleport.success.entity.multiple", p_139034_.size(), p_139035_.getDisplayName()), true);
      }

      return p_139034_.size();
   }

   private static int teleportToPos(CommandSourceStack p_139026_, Collection<? extends Entity> p_139027_, ServerLevel p_139028_, Coordinates p_139029_, @Nullable Coordinates p_139030_, @Nullable TeleportCommand.LookAt p_139031_) throws CommandSyntaxException {
      SectorVec3 exact = p_139029_.getExactPosition(p_139026_);
      Vec3 vec3 = exact.toApproximateVec3();
      Vec2 vec2 = p_139030_ == null ? null : p_139030_.getRotation(p_139026_);
      Set<ClientboundPlayerPositionPacket.RelativeArgument> set = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);
      if (p_139029_.isXRelative()) {
         set.add(ClientboundPlayerPositionPacket.RelativeArgument.X);
      }

      if (p_139029_.isYRelative()) {
         set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y);
      }

      if (p_139029_.isZRelative()) {
         set.add(ClientboundPlayerPositionPacket.RelativeArgument.Z);
      }

      if (p_139030_ == null) {
         set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
         set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
      } else {
         if (p_139030_.isXRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT);
         }

         if (p_139030_.isYRelative()) {
            set.add(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT);
         }
      }

      for(Entity entity : p_139027_) {
         if (p_139030_ == null) {
            performTeleport(p_139026_, entity, p_139028_, exact, vec3, set, entity.getYRot(), entity.getXRot(), p_139031_);
         } else {
            performTeleport(p_139026_, entity, p_139028_, exact, vec3, set, vec2.y, vec2.x, p_139031_);
         }
      }

      if (p_139027_.size() == 1) {
         p_139026_.sendSuccess(Component.translatable("commands.teleport.success.location.single", p_139027_.iterator().next().getDisplayName(), exact.formatX(6), formatDouble(exact.y()), exact.formatZ(6)), true);
      } else {
         p_139026_.sendSuccess(Component.translatable("commands.teleport.success.location.multiple", p_139027_.size(), exact.formatX(6), formatDouble(exact.y()), exact.formatZ(6)), true);
      }

      return p_139027_.size();
   }

   private static String formatDouble(double value) {
      return String.format(Locale.ROOT, "%f", value);
   }

   private static String formatExact(SectorVec3 position) {
      return position.formatX(6) + " " + String.format(Locale.ROOT, "%f", position.y()) + " " + position.formatZ(6);
   }

   private static void performTeleport(CommandSourceStack source, Entity entity, ServerLevel level, @Nullable SectorVec3 exactPosition, Vec3 approximatePosition, Set<ClientboundPlayerPositionPacket.RelativeArgument> relative, float yRot, float xRot, @Nullable TeleportCommand.LookAt lookAt) throws CommandSyntaxException {
      BlockPos blockpos = exactPosition != null ? exactPosition.blockPosition() : new BlockPos(approximatePosition);
      if (!Level.isInSpawnableBounds(blockpos)) throw INVALID_POSITION.create();
      float wrappedYRot = Mth.wrapDegrees(yRot);
      float wrappedXRot = Mth.wrapDegrees(xRot);
      if (entity instanceof ServerPlayer player) {
         ChunkPos chunkpos = new ChunkPos(blockpos);
         level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, entity.getId());
         entity.stopRiding();
         if (player.isSleeping()) player.stopSleepInBed(true, true);
         if (level == entity.level) {
            if (exactPosition != null && player.hasSectorPosition()) player.connection.teleportExact(exactPosition, wrappedYRot, wrappedXRot, relative, false);
            else player.connection.teleport(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, wrappedXRot, relative);
         } else {
            player.teleportTo(level, approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, wrappedXRot);
            if (exactPosition != null && player.hasSectorPosition()) player.applyExactPosition(exactPosition);
         }
         entity.setYHeadRot(wrappedYRot);
      } else {
         float clampedXRot = Mth.clamp(wrappedXRot, -90.0F, 90.0F);
         if (level == entity.level) {
            if (exactPosition != null && entity.hasSectorPosition()) entity.absMoveTo(exactPosition, wrappedYRot, clampedXRot);
            else entity.moveTo(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, clampedXRot);
            entity.setYHeadRot(wrappedYRot);
         } else {
            entity.unRide();
            Entity oldEntity = entity;
            entity = entity.getType().create(level);
            if (entity == null) return;
            entity.restoreFrom(oldEntity);
            if (exactPosition != null && entity.hasSectorPosition()) entity.applyExactPosition(exactPosition);
            if (exactPosition != null && entity.hasSectorPosition()) entity.absMoveTo(exactPosition, wrappedYRot, clampedXRot);
            else entity.moveTo(approximatePosition.x, approximatePosition.y, approximatePosition.z, wrappedYRot, clampedXRot);
            entity.setYHeadRot(wrappedYRot);
            oldEntity.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
            level.addDuringTeleport(entity);
         }
      }
      if (lookAt != null) lookAt.perform(source, entity);
      if (!(entity instanceof LivingEntity) || !((LivingEntity)entity).isFallFlying()) {
         entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
         entity.setOnGround(true);
      }
      if (entity instanceof PathfinderMob mob) mob.getNavigation().stop();
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

      public LookAt(Vec3 p_139059_) {
         this.entity = null;
         this.exactPosition = null;
         this.position = p_139059_;
         this.anchor = null;
      }

      public LookAt(SectorVec3 position) {
         this.entity = null;
         this.exactPosition = position;
         this.position = position.toApproximateVec3();
         this.anchor = null;
      }

      public void perform(CommandSourceStack p_139061_, Entity p_139062_) {
         if (this.entity != null) {
            if (p_139062_ instanceof ServerPlayer) {
               ((ServerPlayer)p_139062_).lookAt(p_139061_.getAnchor(), this.entity, this.anchor);
            } else {
               p_139062_.lookAt(p_139061_.getAnchor(), this.position);
            }
         } else if (this.exactPosition != null && p_139062_.hasSectorPosition()) {
            p_139062_.lookAt(p_139061_.getAnchor(), this.exactPosition.toApproximateVec3());
         } else {
            p_139062_.lookAt(p_139061_.getAnchor(), this.position);
         }

      }
   }
}