package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ChatSender;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingPlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandSourceStack implements SharedSuggestionProvider {
   public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
   public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));
   private final CommandSource source;
   private final Vec3 worldPosition;
   private final SectorVec3 exactPosition;
   private final ServerLevel level;
   private final int permissionLevel;
   private final String textName;
   private final Component displayName;
   private final MinecraftServer server;
   private final boolean silent;
   @Nullable
   private final Entity entity;
   @Nullable
   private final ResultConsumer<CommandSourceStack> consumer;
   private final EntityAnchorArgument.Anchor anchor;
   private final Vec2 rotation;
   private final CommandSigningContext signingContext;
   private final TaskChainer chatMessageChainer;

   public CommandSourceStack(CommandSource p_81302_, Vec3 p_81303_, Vec2 p_81304_, ServerLevel p_81305_, int p_81306_, String p_81307_, Component p_81308_, MinecraftServer p_81309_, @Nullable Entity p_81310_) {
      this(p_81302_, p_81303_, p_81304_, p_81305_, p_81306_, p_81307_, p_81308_, p_81309_, p_81310_, false, (p_81361_, p_81362_, p_81363_) -> {
      }, EntityAnchorArgument.Anchor.FEET, CommandSigningContext.ANONYMOUS, TaskChainer.IMMEDIATE);
   }

   protected CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level, int permission,
                                String textName, Component displayName, MinecraftServer server, @Nullable Entity entity,
                                boolean silent, @Nullable ResultConsumer<CommandSourceStack> consumer,
                                EntityAnchorArgument.Anchor anchor, CommandSigningContext signingContext, TaskChainer chatMessageChainer) {
      this(source, position, entity != null ? entity.exactPosition() : null, rotation, level, permission, textName, displayName,
            server, entity, silent, consumer, anchor, signingContext, chatMessageChainer);
   }

   private CommandSourceStack(CommandSource source, Vec3 position, @Nullable SectorVec3 exactPosition, Vec2 rotation,
                              ServerLevel level, int permission, String textName, Component displayName, MinecraftServer server,
                              @Nullable Entity entity, boolean silent, @Nullable ResultConsumer<CommandSourceStack> consumer,
                              EntityAnchorArgument.Anchor anchor, CommandSigningContext signingContext, TaskChainer chatMessageChainer) {
      this.source = source;
      this.worldPosition = position;
      this.exactPosition = exactPosition != null ? exactPosition : SectorVec3.fromApproximate(position.x, position.y, position.z);
      this.level = level;
      this.silent = silent;
      this.entity = entity;
      this.permissionLevel = permission;
      this.textName = textName;
      this.displayName = displayName;
      this.server = server;
      this.consumer = consumer;
      this.anchor = anchor;
      this.rotation = rotation;
      this.signingContext = signingContext;
      this.chatMessageChainer = chatMessageChainer;
   }

   public CommandSourceStack withSource(CommandSource p_165485_) {
      return this.source == p_165485_ ? this : new CommandSourceStack(p_165485_, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withEntity(Entity entity) {
      SectorVec3 exact = entity.exactPosition();
      Vec3 position = exact == null ? entity.position() : exact.toApproximateVec3();
      return this.entity == entity ? this : new CommandSourceStack(this.source, position, exact, this.rotation, this.level,
            this.permissionLevel, entity.getName().getString(), entity.getDisplayName(), this.server, entity, this.silent,
            this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withPosition(Vec3 position) {
      return this.worldPosition.equals(position) ? this : new CommandSourceStack(this.source, position, SectorVec3.fromApproximate(position.x, position.y, position.z), this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withExactPosition(SectorVec3 position) {
      return this.exactPosition.equals(position) ? this : new CommandSourceStack(this.source, position.toApproximateVec3(), position, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withRotation(Vec2 p_81347_) {
      return this.rotation.equals(p_81347_) ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, p_81347_, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> p_81335_) {
      return Objects.equals(this.consumer, p_81335_) ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, p_81335_, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> p_81337_, BinaryOperator<ResultConsumer<CommandSourceStack>> p_81338_) {
      ResultConsumer<CommandSourceStack> resultconsumer = p_81338_.apply(this.consumer, p_81337_);
      return this.withCallback(resultconsumer);
   }

   public CommandSourceStack withSuppressedOutput() {
      return !this.silent && !this.source.alwaysAccepts() ? new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, true, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer) : this;
   }

   public CommandSourceStack withPermission(int p_81326_) {
      return p_81326_ == this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, p_81326_, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withMaximumPermission(int p_81359_) {
      return p_81359_ <= this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, p_81359_, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor p_81351_) {
      return p_81351_ == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, p_81351_, this.signingContext, this.chatMessageChainer);
   }

   public CommandSourceStack withLevel(ServerLevel level) {
      if (level == this.level) return this;
      SectorVec3 exact = this.exactPosition;
      if (this.level != null) {
         double scale = DimensionType.getTeleportationScale(this.level.dimensionType(), level.dimensionType());
         exact = scaleExact(this.exactPosition, scale);
      }
      return new CommandSourceStack(this.source, exact.toApproximateVec3(), exact, this.rotation, level, this.permissionLevel, this.textName,
            this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer);
   }

   private static SectorVec3 scaleExact(SectorVec3 position, double scale) {
      if (!Double.isFinite(scale) || scale == 0.0D) throw new IllegalArgumentException("Invalid dimension scale: " + scale);
      // Dimension scales used by vanilla are powers of two. Scaling the split
      // components independently avoids forming a huge global double.
      java.math.BigDecimal x = java.math.BigDecimal.valueOf(position.blockX()).add(java.math.BigDecimal.valueOf(position.subX())).multiply(java.math.BigDecimal.valueOf(scale));
      java.math.BigDecimal z = java.math.BigDecimal.valueOf(position.blockZ()).add(java.math.BigDecimal.valueOf(position.subZ())).multiply(java.math.BigDecimal.valueOf(scale));
      return SectorVec3.fromDecimal(x.toPlainString(), position.y(), z.toPlainString());
   }

   public CommandSourceStack facing(Entity target, EntityAnchorArgument.Anchor targetAnchor) {
      SectorVec3 targetPosition = targetAnchor == EntityAnchorArgument.Anchor.EYES && target.exactEyePosition() != null
            ? target.exactEyePosition() : target.exactPosition();
      if (targetPosition != null) return this.facingExact(targetPosition);
      Vec3 approximateTarget = targetAnchor.apply(target);
      return this.facingExact(SectorVec3.fromApproximate(approximateTarget.x, approximateTarget.y, approximateTarget.z));
   }

   public CommandSourceStack facingExact(SectorVec3 target) {
      Vec3 delta = target.relativeTo(this.getExactAnchor());
      double d0 = delta.x;
      double d1 = delta.y;
      double d2 = delta.z;
      double d3 = Math.sqrt(d0 * d0 + d2 * d2);
      float f = Mth.wrapDegrees((float)(-(Mth.atan2(d1, d3) * (double)(180F / (float)Math.PI))));
      float f1 = Mth.wrapDegrees((float)(Mth.atan2(d2, d0) * (double)(180F / (float)Math.PI)) - 90.0F);
      return this.withRotation(new Vec2(f, f1));
   }

   public CommandSourceStack withSigningContext(CommandSigningContext p_230894_) {
      return p_230894_ == this.signingContext ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, p_230894_, this.chatMessageChainer);
   }

   public CommandSourceStack withChatMessageChainer(TaskChainer p_242228_) {
      return p_242228_ == this.chatMessageChainer ? this : new CommandSourceStack(this.source, this.worldPosition, this.exactPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, p_242228_);
   }

   public Component getDisplayName() {
      return this.displayName;
   }

   public String getTextName() {
      return this.textName;
   }

   public ChatSender asChatSender() {
      return this.entity != null ? this.entity.asChatSender() : ChatSender.SYSTEM;
   }

   public boolean hasPermission(int p_81370_) {
      return this.permissionLevel >= p_81370_;
   }

   public Vec3 getPosition() {
      return this.worldPosition;
   }

   public SectorVec3 getExactPosition() {
      return this.exactPosition;
   }

   public SectorVec3 getExactAnchor() {
      Entity entity = this.entity;
      if (this.anchor == EntityAnchorArgument.Anchor.EYES && entity != null && entity.exactEyePosition() != null) {
         return entity.exactEyePosition();
      }
      return this.exactPosition;
   }

   public ServerLevel getLevel() {
      return this.level;
   }

   @Nullable
   public Entity getEntity() {
      return this.entity;
   }

   public Entity getEntityOrException() throws CommandSyntaxException {
      if (this.entity == null) {
         throw ERROR_NOT_ENTITY.create();
      } else {
         return this.entity;
      }
   }

   public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
      Entity entity = this.entity;
      if (entity instanceof ServerPlayer) {
         return (ServerPlayer)entity;
      } else {
         throw ERROR_NOT_PLAYER.create();
      }
   }

   @Nullable
   public ServerPlayer getPlayer() {
      Entity entity = this.entity;
      ServerPlayer serverplayer1;
      if (entity instanceof ServerPlayer serverplayer) {
         serverplayer1 = serverplayer;
      } else {
         serverplayer1 = null;
      }

      return serverplayer1;
   }

   public boolean isPlayer() {
      return this.entity instanceof ServerPlayer;
   }

   public Vec2 getRotation() {
      return this.rotation;
   }

   public MinecraftServer getServer() {
      return this.server;
   }

   public EntityAnchorArgument.Anchor getAnchor() {
      return this.anchor;
   }

   public CommandSigningContext getSigningContext() {
      return this.signingContext;
   }

   public TaskChainer getChatMessageChainer() {
      return this.chatMessageChainer;
   }

   public boolean shouldFilterMessageTo(ServerPlayer p_243268_) {
      ServerPlayer serverplayer = this.getPlayer();
      if (p_243268_ == serverplayer) {
         return false;
      } else {
         return serverplayer != null && serverplayer.isTextFilteringEnabled() || p_243268_.isTextFilteringEnabled();
      }
   }

   public void sendChatMessage(OutgoingPlayerChatMessage p_243226_, boolean p_243216_, ChatType.Bound p_243244_) {
      if (!this.silent) {
         ServerPlayer serverplayer = this.getPlayer();
         if (serverplayer != null) {
            serverplayer.sendChatMessage(p_243226_, p_243216_, p_243244_);
         } else {
            this.source.sendSystemMessage(p_243244_.decorate(p_243226_.serverContent()));
         }

      }
   }

   public void sendSystemMessage(Component p_243331_) {
      if (!this.silent) {
         ServerPlayer serverplayer = this.getPlayer();
         if (serverplayer != null) {
            serverplayer.sendSystemMessage(p_243331_);
         } else {
            this.source.sendSystemMessage(p_243331_);
         }

      }
   }

   public void sendSuccess(Component p_81355_, boolean p_81356_) {
      if (this.source.acceptsSuccess() && !this.silent) {
         this.source.sendSystemMessage(p_81355_);
      }

      if (p_81356_ && this.source.shouldInformAdmins() && !this.silent) {
         this.broadcastToAdmins(p_81355_);
      }

   }

   private void broadcastToAdmins(Component p_81367_) {
      Component component = Component.translatable("chat.type.admin", this.getDisplayName(), p_81367_).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
      if (this.server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
         for(ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
            if (serverplayer != this.source && this.server.getPlayerList().isOp(serverplayer.getGameProfile())) {
               serverplayer.sendSystemMessage(component);
            }
         }
      }

      if (this.source != this.server && this.server.getGameRules().getBoolean(GameRules.RULE_LOGADMINCOMMANDS)) {
         this.server.sendSystemMessage(component);
      }

   }

   public void sendFailure(Component p_81353_) {
      if (this.source.acceptsFailure() && !this.silent) {
         this.source.sendSystemMessage(Component.empty().append(p_81353_).withStyle(ChatFormatting.RED));
      }

   }

   public void onCommandComplete(CommandContext<CommandSourceStack> p_81343_, boolean p_81344_, int p_81345_) {
      if (this.consumer != null) {
         this.consumer.onCommandComplete(p_81343_, p_81344_, p_81345_);
      }

   }

   public Collection<String> getOnlinePlayerNames() {
      return Lists.newArrayList(this.server.getPlayerNames());
   }

   public Collection<String> getAllTeams() {
      return this.server.getScoreboard().getTeamNames();
   }

   public Collection<ResourceLocation> getAvailableSoundEvents() {
      return Registry.SOUND_EVENT.keySet();
   }

   public Stream<ResourceLocation> getRecipeNames() {
      return this.server.getRecipeManager().getRecipeIds();
   }

   public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> p_212324_) {
      return Suggestions.empty();
   }

   public CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> p_212330_, SharedSuggestionProvider.ElementSuggestionType p_212331_, SuggestionsBuilder p_212332_, CommandContext<?> p_212333_) {
      return this.registryAccess().registry(p_212330_).map((p_212328_) -> {
         this.suggestRegistryElements(p_212328_, p_212331_, p_212332_);
         return p_212332_.buildFuture();
      }).orElseGet(Suggestions::empty);
   }

   public Set<ResourceKey<Level>> levels() {
      return this.server.levelKeys();
   }

   public RegistryAccess registryAccess() {
      return this.server.registryAccess();
   }
}