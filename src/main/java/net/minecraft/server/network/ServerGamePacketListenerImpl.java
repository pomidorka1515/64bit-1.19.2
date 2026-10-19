package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatMessageContent;
import net.minecraft.network.chat.ChatPreviewCache;
import net.minecraft.network.chat.ChatPreviewThrottler;
import net.minecraft.network.chat.ChatSender;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSigner;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.PreviewableCommand;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChatPreviewPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatPreviewPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class ServerGamePacketListenerImpl implements ServerPlayerConnection, TickablePacketListener, ServerGamePacketListener {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int LATENCY_CHECK_INTERVAL = 15000;
    public static final double MAX_INTERACTION_DISTANCE = Mth.square(6.0D);
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int PENDING_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    public final Connection connection;
    private final MinecraftServer server;
    public ServerPlayer player;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private int chatSpamTickCount;
    private int dropSpamTickCount;
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    @Nullable
    private SectorVec3 firstGoodSector;
    @Nullable
    private SectorVec3 lastGoodSector;
    @Nullable
    private Entity lastVehicle;
    @Nullable
    private SectorVec3 vehicleFirstGoodSector;
    @Nullable
    private SectorVec3 vehicleLastGoodSector;
    @Nullable
    private Vec3 awaitingPositionFromClient;
    @Nullable
    private SectorVec3 awaitingSectorPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    private final ChatPreviewCache chatPreviewCache = new ChatPreviewCache();
    private final ChatPreviewThrottler chatPreviewThrottler = new ChatPreviewThrottler();
    private final AtomicReference<Instant> lastChatTimeStamp = new AtomicReference<>(Instant.EPOCH);
    private final SignedMessageChain.Decoder signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessagesValidator = new LastSeenMessagesValidator();
    private final FutureChain chatMessageChain;

    public ServerGamePacketListenerImpl(MinecraftServer pServer, Connection pConnection, ServerPlayer pPlayer) {
        this.server = pServer;
        this.connection = pConnection;
        pConnection.setListener(this);
        this.player = pPlayer;
        pPlayer.connection = this;
        this.keepAliveTime = Util.getMillis();
        pPlayer.getTextFilter().join();
        ProfilePublicKey profilepublickey = pPlayer.getProfilePublicKey();
        if (profilepublickey != null) {
            this.signedMessageDecoder = (new SignedMessageChain()).decoder();
        } else {
            this.signedMessageDecoder = SignedMessageChain.Decoder.UNSIGNED;
        }

        this.chatMessageChain = new FutureChain(pServer);
    }

    private boolean worldgenDebugSubscribed;

    public void tick() {
        if (this.worldgenDebugSubscribed) {
            this.send(net.minecraft.network.protocol.game.WorldgenDebug.sample(this.player));
        }
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        if (this.player.hasSectorPosition() && this.firstGoodSector != null) {
            // The tick baseline is exact; never restore it through legacy global doubles.
            this.player.applyExactPosition(this.firstGoodSector);
        } else {
            this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        }
        ++this.tickCount;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger()) {
            if (++this.aboveGroundTickCount > 80) {
                LOGGER.warn("{} was kicked for floating too long!", (Object) this.player.getName().getString());
                this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodSector = this.lastVehicle.sectorPosition();
            this.vehicleLastGoodSector = this.lastVehicle.sectorPosition();
            if (this.clientVehicleIsFloating && this.player.getRootVehicle().getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > 80) {
                    LOGGER.warn("{} was kicked for floating a vehicle too long!", (Object) this.player.getName().getString());
                    this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.vehicleFirstGoodSector = null;
            this.vehicleLastGoodSector = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        this.server.getProfiler().push("keepAlive");
        long i = Util.getMillis();
        if (i - this.keepAliveTime >= 15000L) {
            if (this.keepAlivePending) {
                this.disconnect(Component.translatable("disconnect.timeout"));
            } else {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        this.server.getProfiler().pop();
        if (this.chatSpamTickCount > 0) {
            --this.chatSpamTickCount;
        }

        if (this.dropSpamTickCount > 0) {
            --this.dropSpamTickCount;
        }

        if (this.player.getLastActionTime() > 0L && this.server.getPlayerIdleTimeout() > 0 && Util.getMillis() - this.player.getLastActionTime() > (long) (this.server.getPlayerIdleTimeout() * 1000 * 60)) {
            this.disconnect(Component.translatable("multiplayer.disconnect.idling"));
        }

        this.chatPreviewThrottler.tick();
    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
        if (this.player.hasSectorPosition()) {
            this.firstGoodSector = this.player.sectorPosition();
            this.lastGoodSector = this.player.sectorPosition();
        } else {
            this.firstGoodSector = null;
            this.lastGoodSector = null;
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    private boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.player.getGameProfile());
    }

    public void disconnect(Component pTextComponent) {
        this.connection.send(new ClientboundDisconnectPacket(pTextComponent), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(pTextComponent);
        }));
        this.connection.setReadOnly();
        this.server.executeBlocking(this.connection::handleDisconnection);
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T p_243240_, BiFunction<TextFilter, T, CompletableFuture<R>> p_243271_) {
        return p_243271_.apply(this.player.getTextFilter(), p_243240_).thenApply((p_243192_) -> {
            if (!this.getConnection().isConnected()) {
                LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return p_243192_;
            }
        });
    }

    private CompletableFuture<FilteredText> filterTextPacket(String pText) {
        return this.filterTextPacket(pText, TextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> pTexts) {
        return this.filterTextPacket(pTexts, TextFilter::processMessageBundle);
    }

    public void handlePlayerInput(ServerboundPlayerInputPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.setPlayerInput(pPacket.getXxa(), pPacket.getZza(), pPacket.isJumping(), pPacket.isShiftKeyDown());
    }

    private static boolean containsInvalidValues(double p_143664_, double p_143665_, double p_143666_, float p_143667_, float p_143668_) {
        return !Double.isFinite(p_143664_) || !Double.isFinite(p_143665_) || !Double.isFinite(p_143666_)
                || !Floats.isFinite(p_143668_) || !Floats.isFinite(p_143667_);
    }

    private static double clampHorizontal(double pValue) {
        return WorldBounds.clampAbsoluteDouble(pValue);
    }

    private static double clampVertical(double pValue) {
        return WorldBounds.clampVerticalDouble(pValue);
    }

    public void handleMoveVehicle(ServerboundMoveVehiclePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        SectorVec3 target = pPacket.getExactPosition();
        if (!target.isFinite() || !Floats.isFinite(pPacket.getYRot()) || !Floats.isFinite(pPacket.getXRot())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
            return;
        }

        Entity entity = this.player.getRootVehicle();
        if (entity == this.player || entity.getControllingPassenger() != this.player || entity != this.lastVehicle
                || this.vehicleFirstGoodSector == null || this.vehicleLastGoodSector == null) {
            return;
        }

        ServerLevel serverlevel = this.player.getLevel();
        SectorVec3 before = entity.sectorPosition();
        float yRot = Mth.wrapDegrees(pPacket.getYRot());
        float xRot = Mth.wrapDegrees(pPacket.getXRot());
        Vec3 firstDelta = target.relativeTo(this.vehicleFirstGoodSector);
        double speedSqr = entity.getDeltaMovement().lengthSqr();
        double firstDistanceSqr = firstDelta.lengthSqr();
        if (firstDistanceSqr - speedSqr > 100.0D && !this.isSingleplayerOwner()) {
            LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName().getString(),
                    this.player.getName().getString(), firstDelta.x, firstDelta.y, firstDelta.z);
            this.connection.send(new ClientboundMoveVehiclePacket(entity));
            return;
        }

        boolean unobstructedBefore = entity.hasExactNoCollision();
        Vec3 requested = target.relativeTo(this.vehicleLastGoodSector);
        requested = requested.add(0.0D, -1.0E-6D, 0.0D);
        boolean wasOnGround = entity.verticalCollisionBelow;
        entity.move(MoverType.PLAYER, requested);
        Vec3 remaining = target.relativeTo(entity.sectorPosition());
        double verticalResidual = remaining.y;
        if (verticalResidual > -0.5D && verticalResidual < 0.5D) {
            verticalResidual = 0.0D;
        }
        double residualSqr = remaining.x * remaining.x + verticalResidual * verticalResidual + remaining.z * remaining.z;
        boolean movedWrongly = residualSqr > 0.0625D;
        if (movedWrongly) {
            LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", entity.getName().getString(), this.player.getName().getString(),
                    Math.sqrt(residualSqr));
        }

        entity.absMoveTo(target, yRot, xRot);
        boolean unobstructedAfter = entity.hasExactNoCollision();
        if (unobstructedBefore && (movedWrongly || !unobstructedAfter)) {
            entity.absMoveTo(before, yRot, xRot);
            this.connection.send(new ClientboundMoveVehiclePacket(entity));
            return;
        }

        this.player.getLevel().getChunkSource().move(this.player);
        Vec3 travelled = entity.sectorPosition().relativeTo(before);
        this.player.checkMovementStatistics(travelled);
        this.clientVehicleIsFloating = verticalResidual >= -0.03125D && !wasOnGround && !this.server.isFlightAllowed()
                && !entity.isNoGravity() && this.noBlocksAround(entity);
        this.vehicleLastGoodSector = entity.sectorPosition();
    }

    private boolean noBlocksAround(Entity entity) {
        net.minecraft.world.phys.SectorAABB box = entity.getSectorBoundingBox().inflate(0.0625D, 0.0625D, 0.0625D).expandTowards(0.0D, -0.55D, 0.0D);
        for (long x = box.minBlockXForRange();; x = WorldBounds.addBlockOffset(x, 1L)) {
            for (int y = box.minBlockYForRange(); y < box.maxBlockYExclusive(); ++y) {
                for (long z = box.minBlockZForRange();; z = WorldBounds.addBlockOffset(z, 1L)) {
                    if (!entity.level.getBlockState(new BlockPos(x, y, z)).isAir())
                        return false;
                    if (!WorldBounds.canAdvanceBlock(z, box.maxBlockZForRangeInclusive()))
                        break;
                }
            }
            if (!WorldBounds.canAdvanceBlock(x, box.maxBlockXForRangeInclusive()))
                break;
        }
        return true;
    }

    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (pPacket.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
                return;
            }

            if (this.player.hasSectorPosition() && this.awaitingSectorPositionFromClient != null) {
                this.player.applyExactPosition(this.awaitingSectorPositionFromClient);
                this.lastGoodSector = this.awaitingSectorPositionFromClient;
            } else {
                this.player.absMoveTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z,
                        this.player.getYRot(), this.player.getXRot());
            }
            this.lastGoodX = this.player.getX();
            this.lastGoodY = this.player.getY();
            this.lastGoodZ = this.player.getZ();
            if (this.player.isChangingDimension()) {
                this.player.hasChangedDimension();
            }

            this.awaitingPositionFromClient = null;
            this.awaitingSectorPositionFromClient = null;
        }
    }

    public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.server.getRecipeManager().byKey(pPacket.getRecipe()).ifPresent(this.player.getRecipeBook()::removeHighlight);
    }

    public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.getRecipeBook().setBookSetting(pPacket.getBookType(), pPacket.isOpen(), pPacket.isFiltering());
    }

    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (pPacket.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            ResourceLocation resourcelocation = pPacket.getTab();
            Advancement advancement = this.server.getAdvancements().getAdvancement(resourcelocation);
            if (advancement != null) {
                this.player.getAdvancements().setSelectedTab(advancement);
            }
        }
    }

    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        StringReader stringreader = new StringReader(pPacket.getCommand());
        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }

        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());
        this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((p_238197_) -> {
            this.connection.send(new ClientboundCommandSuggestionsPacket(pPacket.getId(), p_238197_));
        });
    }

    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = null;
            CommandBlockEntity commandblockentity = null;
            BlockPos blockpos = pPacket.getPos();
            if (!this.player.level.isLoaded(blockpos))
                return;
            BlockEntity blockentity = this.player.level.getBlockEntity(blockpos);
            if (blockentity instanceof CommandBlockEntity) {
                commandblockentity = (CommandBlockEntity) blockentity;
                basecommandblock = commandblockentity.getCommandBlock();
            }

            String s = pPacket.getCommand();
            boolean flag = pPacket.isTrackOutput();
            if (basecommandblock != null) {
                CommandBlockEntity.Mode commandblockentity$mode = commandblockentity.getMode();
                BlockState blockstate = this.player.level.getBlockState(blockpos);
                Direction direction = blockstate.getValue(CommandBlock.FACING);
                BlockState blockstate1;
                switch (pPacket.getMode()) {
                    case SEQUENCE:
                        blockstate1 = Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case AUTO:
                        blockstate1 = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case REDSTONE:
                    default:
                        blockstate1 = Blocks.COMMAND_BLOCK.defaultBlockState();
                }

                BlockState blockstate2 = blockstate1.setValue(CommandBlock.FACING, direction).setValue(CommandBlock.CONDITIONAL, Boolean.valueOf(pPacket.isConditional()));
                if (blockstate2 != blockstate) {
                    this.player.level.setBlock(blockpos, blockstate2, 2);
                    blockentity.setBlockState(blockstate2);
                    this.player.level.getChunkAt(blockpos).setBlockEntity(blockentity);
                }

                basecommandblock.setCommand(s);
                basecommandblock.setTrackOutput(flag);
                if (!flag) {
                    basecommandblock.setLastOutput((Component) null);
                }

                commandblockentity.setAutomatic(pPacket.isAutomatic());
                if (commandblockentity$mode != pPacket.getMode()) {
                    commandblockentity.onModeSwitch();
                }

                basecommandblock.onUpdated();
                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", s));
                }
            }
        }
    }

    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = pPacket.getCommandBlock(this.player.level);
            if (basecommandblock != null) {
                basecommandblock.setCommand(pPacket.getCommand());
                basecommandblock.setTrackOutput(pPacket.isTrackOutput());
                if (!pPacket.isTrackOutput()) {
                    basecommandblock.setLastOutput((Component) null);
                }

                basecommandblock.onUpdated();
                this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", pPacket.getCommand()));
            }
        }
    }

    public void handlePickItem(ServerboundPickItemPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.getInventory().pickSlot(pPacket.getSlot());
        this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, this.player.getInventory().selected, this.player.getInventory().getItem(this.player.getInventory().selected)));
        this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, pPacket.getSlot(), this.player.getInventory().getItem(pPacket.getSlot())));
        this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
    }

    public void handleRenameItem(ServerboundRenameItemPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        AbstractContainerMenu $$2 = this.player.containerMenu;
        if ($$2 instanceof AnvilMenu anvilmenu) {
            if (!anvilmenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilmenu);
                return;
            }

            String s = SharedConstants.filterText(pPacket.getName());
            if (s.length() <= 50) {
                anvilmenu.setItemName(s);
            }
        }
    }

    public void handleSetBeaconPacket(ServerboundSetBeaconPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;
        if (abstractcontainermenu instanceof BeaconMenu beaconmenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            beaconmenu.updateEffects(pPacket.getPrimary(), pPacket.getSecondary());
        }
    }

    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = pPacket.getPos();
            BlockState blockstate = this.player.level.getBlockState(blockpos);
            BlockEntity blockentity = this.player.level.getBlockEntity(blockpos);
            if (blockentity instanceof StructureBlockEntity) {
                StructureBlockEntity structureblockentity = (StructureBlockEntity) blockentity;
                structureblockentity.setMode(pPacket.getMode());
                structureblockentity.setStructureName(pPacket.getName());
                structureblockentity.setStructurePos(pPacket.getOffset());
                structureblockentity.setStructureSize(pPacket.getSize());
                structureblockentity.setMirror(pPacket.getMirror());
                structureblockentity.setRotation(pPacket.getRotation());
                structureblockentity.setMetaData(pPacket.getData());
                structureblockentity.setIgnoreEntities(pPacket.isIgnoreEntities());
                structureblockentity.setShowAir(pPacket.isShowAir());
                structureblockentity.setShowBoundingBox(pPacket.isShowBoundingBox());
                structureblockentity.setIntegrity(pPacket.getIntegrity());
                structureblockentity.setSeed(pPacket.getSeed());
                if (structureblockentity.hasStructureName()) {
                    String s = structureblockentity.getStructureName();
                    if (pPacket.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (structureblockentity.saveStructure()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_failure", s), false);
                        }
                    } else if (pPacket.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!structureblockentity.isStructureLoadable()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", s), false);
                        } else if (structureblockentity.loadStructure(this.player.getLevel())) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", s), false);
                        }
                    } else if (pPacket.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (structureblockentity.detectSize()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", pPacket.getName()), false);
                }

                structureblockentity.setChanged();
                this.player.level.sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }
        }
    }

    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = pPacket.getPos();
            BlockState blockstate = this.player.level.getBlockState(blockpos);
            BlockEntity blockentity = this.player.level.getBlockEntity(blockpos);
            if (blockentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity) blockentity;
                jigsawblockentity.setName(pPacket.getName());
                jigsawblockentity.setTarget(pPacket.getTarget());
                jigsawblockentity.setPool(ResourceKey.create(Registry.TEMPLATE_POOL_REGISTRY, pPacket.getPool()));
                jigsawblockentity.setFinalState(pPacket.getFinalState());
                jigsawblockentity.setJoint(pPacket.getJoint());
                jigsawblockentity.setChanged();
                this.player.level.sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }
        }
    }

    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = pPacket.getPos();
            BlockEntity blockentity = this.player.level.getBlockEntity(blockpos);
            if (blockentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity) blockentity;
                jigsawblockentity.generate(this.player.getLevel(), pPacket.levels(), pPacket.keepJigsaws());
            }
        }
    }

    public void handleSelectTrade(ServerboundSelectTradePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        int i = pPacket.getItem();
        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;
        if (abstractcontainermenu instanceof MerchantMenu merchantmenu) {
            if (!merchantmenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantmenu);
                return;
            }

            merchantmenu.setSelectionHint(i);
            merchantmenu.tryMoveItems(i);
        }
    }

    public void handleEditBook(ServerboundEditBookPacket pPacket) {
        int i = pPacket.getSlot();
        if (Inventory.isHotbarSlot(i) || i == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = pPacket.getTitle();
            optional.ifPresent(list::add);
            pPacket.getPages().stream().limit(100L).forEach(list::add);
            Consumer<List<FilteredText>> consumer = optional.isPresent() ? (p_238198_) -> {
                this.signBook(p_238198_.get(0), p_238198_.subList(1, p_238198_.size()), i);
            } : (p_143627_) -> {
                this.updateBookContents(p_143627_, i);
            };
            this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
        }
    }

    private void updateBookContents(List<FilteredText> p_9813_, int p_9814_) {
        ItemStack itemstack = this.player.getInventory().getItem(p_9814_);
        if (itemstack.is(Items.WRITABLE_BOOK)) {
            this.updateBookPages(p_9813_, UnaryOperator.identity(), itemstack);
        }
    }

    private void signBook(FilteredText pTitle, List<FilteredText> pPages, int pIndex) {
        ItemStack itemstack = this.player.getInventory().getItem(pIndex);
        if (itemstack.is(Items.WRITABLE_BOOK)) {
            ItemStack itemstack1 = new ItemStack(Items.WRITTEN_BOOK);
            CompoundTag compoundtag = itemstack.getTag();
            if (compoundtag != null) {
                itemstack1.setTag(compoundtag.copy());
            }

            itemstack1.addTagElement("author", StringTag.valueOf(this.player.getName().getString()));
            if (this.player.isTextFilteringEnabled()) {
                itemstack1.addTagElement("title", StringTag.valueOf(pTitle.filteredOrEmpty()));
            } else {
                itemstack1.addTagElement("filtered_title", StringTag.valueOf(pTitle.filteredOrEmpty()));
                itemstack1.addTagElement("title", StringTag.valueOf(pTitle.raw()));
            }

            this.updateBookPages(pPages, (p_238206_) -> {
                return Component.Serializer.toJson(Component.literal(p_238206_));
            }, itemstack1);
            this.player.getInventory().setItem(pIndex, itemstack1);
        }
    }

    private void updateBookPages(List<FilteredText> p_143635_, UnaryOperator<String> p_143636_, ItemStack p_143637_) {
        ListTag listtag = new ListTag();
        if (this.player.isTextFilteringEnabled()) {
            p_143635_.stream().map((p_238209_) -> {
                                  return StringTag.valueOf(p_143636_.apply(p_238209_.filteredOrEmpty()));
                              })
                    .forEach(listtag::add);
        } else {
            CompoundTag compoundtag = new CompoundTag();
            int i = 0;

            for (int j = p_143635_.size(); i < j; ++i) {
                FilteredText filteredtext = p_143635_.get(i);
                String s = filteredtext.raw();
                listtag.add(StringTag.valueOf(p_143636_.apply(s)));
                if (filteredtext.isFiltered()) {
                    compoundtag.putString(String.valueOf(i), p_143636_.apply(filteredtext.filteredOrEmpty()));
                }
            }

            if (!compoundtag.isEmpty()) {
                p_143637_.addTagElement("filtered_pages", compoundtag);
            }
        }

        p_143637_.addTagElement("pages", listtag);
    }

    public void handleEntityTagQuery(ServerboundEntityTagQuery pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.getLevel().getEntity(pPacket.getEntityId());
            if (entity != null) {
                CompoundTag compoundtag = entity.saveWithoutId(new CompoundTag());
                this.player.connection.send(new ClientboundTagQueryPacket(pPacket.getTransactionId(), compoundtag));
            }
        }
    }

    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQuery pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.hasPermissions(2)) {
            BlockEntity blockentity = this.player.getLevel().getBlockEntity(pPacket.getPos());
            CompoundTag compoundtag = blockentity != null ? blockentity.saveWithoutMetadata() : null;
            this.player.connection.send(new ClientboundTagQueryPacket(pPacket.getTransactionId(), compoundtag));
        }
    }

    public void handleMovePlayer(ServerboundMovePlayerPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (containsInvalidValues(pPacket.getX(0.0D), pPacket.getY(0.0D), pPacket.getZ(0.0D), pPacket.getYRot(0.0F), pPacket.getXRot(0.0F))) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
        } else {
            ServerLevel serverlevel = this.player.getLevel();
            if (!this.player.wonGame) {
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (this.awaitingPositionFromClient != null) {
                    if (this.tickCount - this.awaitingTeleportTime > 20) {
                        this.awaitingTeleportTime = this.tickCount;
                        if (this.player.hasSectorPosition() && this.awaitingSectorPositionFromClient != null) {
                            // Re-send the preserved split-coordinate target; reconstructing it
                            // from awaitingPositionFromClient would lose the sub-block bits.
                            this.player.connection.send(new ClientboundPlayerPositionPacket(this.awaitingSectorPositionFromClient,
                                    this.player.getYRot(), this.player.getXRot(), Collections.emptySet(),
                                    this.awaitingTeleport, false));
                        } else {
                            this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y,
                                    this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
                        }
                    }

                } else {
                    this.awaitingTeleportTime = this.tickCount;
                    SectorVec3 packetSector = this.player.hasSectorPosition()
                            ? pPacket.getExactPosition(this.player.sectorPosition())
                            : null;
                    double d0 = packetSector == null ? clampHorizontal(pPacket.getX(this.player.getX())) : this.player.getX();
                    double d1 = clampVertical(pPacket.getY(this.player.getY()));
                    double d2 = packetSector == null ? clampHorizontal(pPacket.getZ(this.player.getZ())) : this.player.getZ();
                    float f = Mth.wrapDegrees(pPacket.getYRot(this.player.getYRot()));
                    float f1 = Mth.wrapDegrees(pPacket.getXRot(this.player.getXRot()));
                    if (this.player.isPassenger()) {
                        this.player.absMoveTo(this.player.sectorPosition(), f, f1);
                        this.player.getLevel().getChunkSource().move(this.player);
                    } else {
                        double d3 = this.player.getX();
                        double d4 = this.player.getY();
                        double d5 = this.player.getZ();
                        double d6 = this.player.getY();
                        double d7;
                        double d8;
                        double d9;
                        if (packetSector != null && this.firstGoodSector != null) {
                            Vec3 exactFirstDelta = packetSector.withY(d1).relativeTo(this.firstGoodSector);
                            d7 = exactFirstDelta.x;
                            d8 = exactFirstDelta.y;
                            d9 = exactFirstDelta.z;
                        } else {
                            d7 = d0 - this.firstGoodX;
                            d8 = d1 - this.firstGoodY;
                            d9 = d2 - this.firstGoodZ;
                        }
                        double d10 = this.player.getDeltaMovement().lengthSqr();
                        double d11 = d7 * d7 + d8 * d8 + d9 * d9;
                        if (this.player.isSleeping()) {
                            if (d11 > 1.0D) {
                                if (this.player.hasSectorPosition()) {
                                    this.teleportExactAbsolute(this.player.sectorPosition(), f, f1);
                                } else {
                                    this.teleportPlayerToCurrentPosition(f, f1);
                                }
                            }

                        } else {
                            ++this.receivedMovePacketCount;
                            int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                            if (i > 5) {
                                LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                i = 1;
                            }

                            if (!this.player.isChangingDimension() && (!this.player.getLevel().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !this.player.isFallFlying())) {
                                float f2 = this.player.isFallFlying() ? 300.0F : 100.0F;
                                if (d11 - d10 > (double) (f2 * (float) i) && !this.isSingleplayerOwner()) {
                                    LOGGER.warn("{} moved too quickly! {},{},{}", this.player.getName().getString(), d7, d8, d9);
                                    if (this.player.hasSectorPosition()) {
                                        this.teleportExactAbsolute(this.player.sectorPosition(), this.player.getYRot(), this.player.getXRot());
                                    } else {
                                        this.teleportPlayerToCurrentPosition(this.player.getYRot(), this.player.getXRot());
                                    }
                                    return;
                                }
                            }

                            AABB aabb = this.player.getBoundingBox();
                            SectorVec3 movementStart = this.player.hasSectorPosition() ? this.player.sectorPosition() : null;
                            if (packetSector != null && this.lastGoodSector != null) {
                                Vec3 exactPacketDelta = packetSector.withY(d1).relativeTo(this.lastGoodSector);
                                d7 = exactPacketDelta.x;
                                d8 = exactPacketDelta.y;
                                d9 = exactPacketDelta.z;
                            } else {
                                d7 = d0 - this.lastGoodX;
                                d8 = d1 - this.lastGoodY;
                                d9 = d2 - this.lastGoodZ;
                            }
                            boolean flag = d8 > 0.0D;
                            if (this.player.isOnGround() && !pPacket.isOnGround() && flag) {
                                this.player.jumpFromGround();
                            }

                            boolean flag1 = this.player.verticalCollisionBelow;
                            this.player.move(MoverType.PLAYER, new Vec3(d7, d8, d9));
                            if (packetSector != null && this.player.hasSectorPosition()) {
                                Vec3 exactRemaining = packetSector.withY(d1).relativeTo(this.player.sectorPosition());
                                d7 = exactRemaining.x;
                                d8 = exactRemaining.y;
                                d9 = exactRemaining.z;
                            } else {
                                d7 = d0 - this.player.getX();
                                d8 = d1 - this.player.getY();
                                if (d8 > -0.5D || d8 < 0.5D) {
                                    d8 = 0.0D;
                                }
                                d9 = d2 - this.player.getZ();
                            }
                            d11 = d7 * d7 + d8 * d8 + d9 * d9;
                            boolean flag2 = false;
                            if (!this.player.isChangingDimension() && d11 > 0.0625D && !this.player.isSleeping() && !this.player.gameMode.isCreative() && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                                flag2 = true;
                                LOGGER.warn("{} moved wrongly!", (Object) this.player.getName().getString());
                            }

                            if (packetSector != null && this.player.hasSectorPosition()) {
                                // Movement above already resolved the exact target against local collision shapes.
                                // Do not overwrite that resolved SectorVec3 with a lossy absolute packet mirror.
                                this.player.setYRot(f);
                                this.player.setXRot(f1);
                            } else {
                                this.player.absMoveTo(d0, d1, d2, f, f1);
                            }
                            if (this.player.noPhysics || this.player.isSleeping() || (!flag2 || !this.player.hasExactNoCollision()) && !this.isPlayerCollidingWithAnythingNew(serverlevel, aabb)) {
                                this.clientIsFloating = d8 >= -0.03125D && !flag1 && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !this.server.isFlightAllowed() && !this.player.getAbilities().mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !this.player.isFallFlying() && !this.player.isAutoSpinAttack() && this.noBlocksAround(this.player);
                                this.player.getLevel().getChunkSource().move(this.player);
                                this.player.doCheckFallDamage(this.player.getY() - d6, pPacket.isOnGround());
                                this.player.setOnGround(pPacket.isOnGround());
                                if (flag) {
                                    this.player.resetFallDistance();
                                }

                                if (movementStart != null) {
                                    this.player.checkMovementStatistics(this.player.sectorPosition().relativeTo(movementStart));
                                } else {
                                    this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                }
                                this.lastGoodX = this.player.getX();
                                this.lastGoodY = this.player.getY();
                                this.lastGoodZ = this.player.getZ();
                                this.lastGoodSector = this.player.hasSectorPosition() ? this.player.sectorPosition() : null;
                            } else {
                                if (this.player.hasSectorPosition() && movementStart != null) {
                                    this.teleportExactAbsolute(movementStart, f, f1);
                                } else {
                                    this.teleport(d3, d4, d5, f, f1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isPlayerCollidingWithAnythingNew(LevelReader p_9796_, AABB p_9797_) {
        // The legacy AABB here is only a lossy compatibility mirror for sector players.
        // Rechecking it at an extreme coordinate can report a false overlap after the
        // exact movement resolver already stopped at the block face, which teleports the
        // player back and looks like block pushback.
        if (this.player.hasSectorPosition()) {
            return !this.player.hasExactNoCollision();
        }

        Iterable<VoxelShape> iterable = p_9796_.getCollisions(this.player, this.player.getBoundingBox().deflate((double) 1.0E-5F));
        VoxelShape voxelshape = Shapes.create(p_9797_.deflate((double) 1.0E-5F));

        for (VoxelShape voxelshape1 : iterable) {
            if (!Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND)) {
                return true;
            }
        }

        return false;
    }

    public void dismount(double pX, double pY, double pZ, float pYaw, float pPitch) {
        this.teleport(pX, pY, pZ, pYaw, pPitch, Collections.emptySet(), true);
    }

    private void teleportPlayerToCurrentPosition(float yRot, float xRot) {
        if (this.player.hasSectorPosition()) {
            this.teleportExactAbsolute(this.player.sectorPosition(), yRot, xRot);
        } else {
            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), yRot, xRot);
        }
    }

    private void teleportExactAbsolute(SectorVec3 position, float yRot, float xRot) {
        this.awaitingPositionFromClient = position.toApproximateVec3();
        this.awaitingSectorPositionFromClient = position;
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }
        this.awaitingTeleportTime = this.tickCount;
        this.player.applyExactPosition(position);
        this.player.setYRot(yRot);
        this.player.setXRot(xRot);
        this.player.setDeltaMovement(Vec3.ZERO);

        if (this.player.getLevel().players().contains(this.player)) {
            this.player.getLevel().getChunkSource().move(this.player);
        }
        this.player.connection.send(new ClientboundPlayerPositionPacket(position, yRot, xRot,
                Collections.emptySet(), this.awaitingTeleport, false));
    }

    public void teleport(double pX, double pY, double pZ, float pYaw, float pPitch) {
        this.teleport(pX, pY, pZ, pYaw, pPitch, Collections.emptySet(), false);
    }

    public void teleport(double pX, double pY, double pZ, float pYaw, float pPitch, Set<ClientboundPlayerPositionPacket.RelativeArgument> pRelativeSet) {
        this.teleport(pX, pY, pZ, pYaw, pPitch, pRelativeSet, false);
    }

    public void teleportExact(SectorVec3 position, float yRot, float xRot) {
        this.teleportExactAbsolute(position, yRot, xRot);
    }

    public void teleportExact(SectorVec3 position, float yRot, float xRot, Set<ClientboundPlayerPositionPacket.RelativeArgument> relative, boolean dismountVehicle) {
        if (dismountVehicle)
            this.player.stopRiding();
        this.teleportExactAbsolute(position, yRot, xRot);
    }

    public void teleport(double pX, double pY, double pZ, float pYaw, float pPitch, Set<ClientboundPlayerPositionPacket.RelativeArgument> pRelativeSet, boolean pDismountVehicle) {
        double d0 = pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.X) ? this.player.getX() : 0.0D;
        double d1 = pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.Y) ? this.player.getY() : 0.0D;
        double d2 = pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.Z) ? this.player.getZ() : 0.0D;
        float f = pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT) ? this.player.getYRot() : 0.0F;
        float f1 = pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.X_ROT) ? this.player.getXRot() : 0.0F;
        SectorVec3 exactTarget = null;
        if (this.player.hasSectorPosition()) {
            SectorVec3 current = this.player.sectorPosition();
            // TeleportCommand has already resolved relative coordinates into the
            // absolute Vec3 arguments. Relative flags describe the client packet;
            // they do not mean that those resolved values must be added again.
            // Adding them here made /tp ~ ~ ~ apply the current position twice,
            // most visibly in Y.
            exactTarget = current;
            if (!pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.X)) {
                double floorX = Math.floor(pX);
                exactTarget = exactTarget.withX((long) floorX, pX - floorX);
            }
            if (!pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.Z)) {
                double floorZ = Math.floor(pZ);
                exactTarget = exactTarget.withZ((long) floorZ, pZ - floorZ);
            }
            if (!pRelativeSet.contains(ClientboundPlayerPositionPacket.RelativeArgument.Y)) {
                exactTarget = exactTarget.withY(pY);
            }
        }
        this.awaitingPositionFromClient = new Vec3(pX, pY, pZ);
        this.awaitingSectorPositionFromClient = exactTarget;
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        this.awaitingTeleportTime = this.tickCount;
        if (this.player.hasSectorPosition()) {
            exactTarget = this.awaitingSectorPositionFromClient;
            this.player.applyExactPosition(exactTarget);
            this.player.setYRot(pYaw);
            this.player.setXRot(pPitch);
            this.player.connection.send(new ClientboundPlayerPositionPacket(exactTarget, pYaw - f, pPitch - f1,
                    pRelativeSet, this.awaitingTeleport, pDismountVehicle));
        } else {
            this.player.absMoveTo(pX, pY, pZ, pYaw, pPitch);
            this.player.connection.send(new ClientboundPlayerPositionPacket(pX - d0, pY - d1, pZ - d2,
                    pYaw - f, pPitch - f1, pRelativeSet, this.awaitingTeleport, pDismountVehicle));
        }
    }

    public void handlePlayerAction(ServerboundPlayerActionPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        BlockPos blockpos = pPacket.getPos();
        this.player.resetLastActionTime();
        ServerboundPlayerActionPacket.Action serverboundplayeractionpacket$action = pPacket.getAction();
        switch (serverboundplayeractionpacket$action) {
            case SWAP_ITEM_WITH_OFFHAND:
                if (!this.player.isSpectator()) {
                    ItemStack itemstack = this.player.getItemInHand(InteractionHand.OFF_HAND);
                    this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    this.player.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    this.player.stopUsingItem();
                }

                return;
            case DROP_ITEM:
                if (!this.player.isSpectator()) {
                    this.player.drop(false);
                }

                return;
            case DROP_ALL_ITEMS:
                if (!this.player.isSpectator()) {
                    this.player.drop(true);
                }

                return;
            case RELEASE_USE_ITEM:
                this.player.releaseUsingItem();
                return;
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                if (!this.player.level.isLoaded(blockpos))
                    return;
                this.player.gameMode.handleBlockBreakAction(blockpos, serverboundplayeractionpacket$action, pPacket.getDirection(), this.player.level.getMaxBuildHeight(), pPacket.getSequence());
                this.player.connection.ackBlockChangesUpTo(pPacket.getSequence());
                return;
            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer pPlayer, ItemStack pStack) {
        if (pStack.isEmpty()) {
            return false;
        } else {
            Item item = pStack.getItem();
            return (item instanceof BlockItem || item instanceof BucketItem) && !pPlayer.getCooldowns().isOnCooldown(item);
        }
    }

    public void handleUseItemOn(ServerboundUseItemOnPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.connection.ackBlockChangesUpTo(pPacket.getSequence());
        ServerLevel serverlevel = this.player.getLevel();
        InteractionHand interactionhand = pPacket.getHand();
        ItemStack itemstack = this.player.getItemInHand(interactionhand);
        BlockHitResult blockhitresult = pPacket.getHitResult();
        Vec3 vec3 = blockhitresult.getLocation();
        BlockPos blockpos = blockhitresult.getBlockPos();
        if (!serverlevel.isLoaded(blockpos))
            return;
        Vec3 vec31 = Vec3.atCenterOf(blockpos);
        SectorVec3 exactBlockCenter = SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D,
                (double) blockpos.getY() + 0.5D, blockpos.getZ(), 0.5D);
        if (!(this.player.exactDistanceToSqr(exactBlockCenter) > MAX_INTERACTION_DISTANCE)) {
            SectorVec3 exactHit = blockhitresult.getExactLocation();
            Vec3 vec32 = exactHit == null ? vec3.subtract(vec31)
                                          : exactHit.relativeTo(exactBlockCenter);
            double d0 = 1.0000001D;
            if (Math.abs(vec32.x()) < 1.0000001D && Math.abs(vec32.y()) < 1.0000001D && Math.abs(vec32.z()) < 1.0000001D) {
                Direction direction = blockhitresult.getDirection();
                this.player.resetLastActionTime();
                int i = this.player.level.getMaxBuildHeight();
                if (blockpos.getY() < i) {
                    if (this.awaitingPositionFromClient == null && this.player.exactPositionDistanceToSqr(exactBlockCenter) < 64.0D && serverlevel.mayInteract(this.player, blockpos)) {
                        InteractionResult interactionresult = this.player.gameMode.useItemOn(this.player, serverlevel, itemstack, interactionhand, blockhitresult);
                        if (direction == Direction.UP && !interactionresult.consumesAction() && blockpos.getY() >= i - 1 && wasBlockPlacementAttempt(this.player, itemstack)) {
                            Component component = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                            this.player.sendSystemMessage(component, true);
                        } else if (interactionresult.shouldSwing()) {
                            this.player.swing(interactionhand, true);
                        }
                    }
                } else {
                    Component component1 = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                    this.player.sendSystemMessage(component1, true);
                }

                this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos));
                this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos.relative(direction)));
            } else {
                LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", this.player.getGameProfile().getName(), vec3, blockpos);
            }
        }
    }

    public void handleUseItem(ServerboundUseItemPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.ackBlockChangesUpTo(pPacket.getSequence());
        ServerLevel serverlevel = this.player.getLevel();
        InteractionHand interactionhand = pPacket.getHand();
        ItemStack itemstack = this.player.getItemInHand(interactionhand);
        this.player.resetLastActionTime();
        if (!itemstack.isEmpty()) {
            InteractionResult interactionresult = this.player.gameMode.useItem(this.player, serverlevel, itemstack, interactionhand);
            if (interactionresult.shouldSwing()) {
                this.player.swing(interactionhand, true);
            }
        }
    }

    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.isSpectator()) {
            for (ServerLevel serverlevel : this.server.getAllLevels()) {
                Entity entity = pPacket.getEntity(serverlevel);
                if (entity != null) {
                    this.player.teleportTo(serverlevel, entity.sectorPosition(), entity.getYRot(), entity.getXRot());
                    return;
                }
            }
        }
    }

    public void handleResourcePackResponse(ServerboundResourcePackPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (pPacket.getAction() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            LOGGER.info("Disconnecting {} due to resource pack rejection", (Object) this.player.getName());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
    }

    public void handlePaddleBoat(ServerboundPaddleBoatPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        Entity entity = this.player.getVehicle();
        if (entity instanceof Boat) {
            ((Boat) entity).setPaddleState(pPacket.getLeft(), pPacket.getRight());
        }
    }

    public void handlePong(ServerboundPongPacket pPacket) {
    }

    public void onDisconnect(Component pReason) {
        LOGGER.info("{} lost connection: {}", this.player.getName().getString(), pReason.getString());
        this.server.invalidateStatus();
        this.server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
        this.player.disconnect();
        this.server.getPlayerList().remove(this.player);
        this.player.getTextFilter().leave();
        if (this.isSingleplayerOwner()) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
    }

    public void ackBlockChangesUpTo(int p_215202_) {
        if (p_215202_ < 0) {
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(p_215202_, this.ackBlockChangesUpTo);
        }
    }

    public void send(Packet<?> pPacket) {
        this.send(pPacket, (PacketSendListener) null);
    }

    public void send(Packet<?> pPacket, @Nullable PacketSendListener pListener) {
        try {
            this.connection.send(pPacket, pListener);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Packet being sent");
            crashreportcategory.setDetail("Packet class", () -> {
                return pPacket.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (pPacket.getSlot() >= 0 && pPacket.getSlot() < Inventory.getSelectionSize()) {
            if (this.player.getInventory().selected != pPacket.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().selected = pPacket.getSlot();
            this.player.resetLastActionTime();
        } else {
            LOGGER.warn("{} tried to set an invalid carried item", (Object) this.player.getName().getString());
        }
    }

    public void handleChat(ServerboundChatPacket pPacket) {
        if (isChatMessageIllegal(pPacket.message())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"));
        } else {
            if (this.tryHandleChat(pPacket.message(), pPacket.timeStamp(), pPacket.lastSeenMessages())) {
                this.server.submit(() -> {
                    PlayerChatMessage playerchatmessage = this.getSignedMessage(pPacket);
                    if (this.verifyChatMessage(playerchatmessage)) {
                        this.chatMessageChain.append(() -> {
                            CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent().plain());
                            CompletableFuture<PlayerChatMessage> completablefuture1 = this.server.getChatDecorator().decorate(this.player, playerchatmessage);
                            return CompletableFuture.allOf(completablefuture, completablefuture1).thenAcceptAsync((p_243197_) -> {
                                FilterMask filtermask = completablefuture.join().mask();
                                PlayerChatMessage playerchatmessage1 = completablefuture1.join().filter(filtermask);
                                this.broadcastChatMessage(playerchatmessage1);
                            }, this.server);
                        });
                    }
                });
            }
        }
    }

    public void handleChatCommand(ServerboundChatCommandPacket pPacket) {
        if (isChatMessageIllegal(pPacket.command())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"));
        } else {
            if (this.tryHandleChat(pPacket.command(), pPacket.timeStamp(), pPacket.lastSeenMessages())) {
                this.server.submit(() -> {
                    this.performChatCommand(pPacket);
                    this.detectRateSpam();
                });
            }
        }
    }

    private void performChatCommand(ServerboundChatCommandPacket pPacket) {
        ParseResults<CommandSourceStack> parseresults = this.parseCommand(pPacket.command());
        Map<String, PlayerChatMessage> map = this.collectSignedArguments(pPacket, PreviewableCommand.of(parseresults));

        for (PlayerChatMessage playerchatmessage : map.values()) {
            if (!this.verifyChatMessage(playerchatmessage)) {
                return;
            }
        }

        CommandSigningContext commandsigningcontext = new CommandSigningContext.SignedArguments(map);
        parseresults = Commands.mapSource(parseresults, (p_242749_) -> {
            return p_242749_.withSigningContext(commandsigningcontext);
        });
        this.server.getCommands().performCommand(parseresults, pPacket.command());
    }

    private Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandPacket pPacket, PreviewableCommand<?> pCommand) {
        Component component = this.chatPreviewCache.pull(pPacket.command());
        MessageSigner messagesigner = new MessageSigner(this.player.getUUID(), pPacket.timeStamp(), pPacket.salt());
        LastSeenMessages lastseenmessages = pPacket.lastSeenMessages().lastSeen();
        Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap<>();
        SignedMessageChain.Decoder signedmessagechain$decoder = this.player.connection.signedMessageDecoder();

        for (Pair<String, String> pair : ArgumentSignatures.collectPlainSignableArguments(pCommand)) {
            String s = pair.getFirst();
            String s1 = pair.getSecond();
            MessageSignature messagesignature = pPacket.argumentSignatures().get(s);
            ChatMessageContent chatmessagecontent;
            if (pPacket.signedPreview() && component != null) {
                chatmessagecontent = new ChatMessageContent(s1, component);
            } else {
                chatmessagecontent = new ChatMessageContent(s1);
            }

            SignedMessageChain.Link signedmessagechain$link = new SignedMessageChain.Link(messagesignature);
            map.put(s, signedmessagechain$decoder.unpack(signedmessagechain$link, messagesigner, chatmessagecontent, lastseenmessages));
        }

        return map;
    }

    private ParseResults<CommandSourceStack> parseCommand(String pCommand) {
        CommandDispatcher<CommandSourceStack> commanddispatcher = this.server.getCommands().getDispatcher();
        return commanddispatcher.parse(pCommand, this.player.createCommandSourceStack());
    }

    private boolean tryHandleChat(String p_242372_, Instant p_242311_, LastSeenMessages.Update p_242217_) {
        if (!this.updateChatOrder(p_242311_)) {
            LOGGER.warn("{} sent out-of-order chat: '{}'", this.player.getName().getString(), p_242372_);
            this.disconnect(Component.translatable("multiplayer.disconnect.out_of_order_chat"));
            return false;
        } else if (this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
            return false;
        } else {
            Set<LastSeenMessagesValidator.ErrorCondition> set;
            synchronized (this.lastSeenMessagesValidator) {
                set = this.lastSeenMessagesValidator.validateAndUpdate(p_242217_);
            }

            if (!set.isEmpty()) {
                this.handleValidationFailure(set);
                return false;
            } else {
                this.player.resetLastActionTime();
                return true;
            }
        }
    }

    private boolean updateChatOrder(Instant pTimestamp) {
        Instant instant;
        do {
            instant = this.lastChatTimeStamp.get();
            if (pTimestamp.isBefore(instant)) {
                return false;
            }
        } while (!this.lastChatTimeStamp.compareAndSet(instant, pTimestamp));

        return true;
    }

    private static boolean isChatMessageIllegal(String pMessage) {
        for (int i = 0; i < pMessage.length(); ++i) {
            if (!SharedConstants.isAllowedChatCharacter(pMessage.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private PlayerChatMessage getSignedMessage(ServerboundChatPacket pPacket) {
        MessageSigner messagesigner = pPacket.getSigner(this.player);
        SignedMessageChain.Link signedmessagechain$link = new SignedMessageChain.Link(pPacket.signature());
        LastSeenMessages lastseenmessages = pPacket.lastSeenMessages().lastSeen();
        ChatMessageContent chatmessagecontent = this.getSignedContent(pPacket);
        return this.signedMessageDecoder.unpack(signedmessagechain$link, messagesigner, chatmessagecontent, lastseenmessages);
    }

    private ChatMessageContent getSignedContent(ServerboundChatPacket pPacket) {
        Component component = this.chatPreviewCache.pull(pPacket.message());
        return pPacket.signedPreview() && component != null ? new ChatMessageContent(pPacket.message(), component) : new ChatMessageContent(pPacket.message());
    }

    private void broadcastChatMessage(PlayerChatMessage pMessage) {
        this.server.getPlayerList().broadcastChatMessage(pMessage, this.player, ChatType.bind(ChatType.CHAT, this.player));
        this.detectRateSpam();
    }

    private boolean verifyChatMessage(PlayerChatMessage pMessage) {
        ChatSender chatsender = this.player.asChatSender();
        ProfilePublicKey profilepublickey = chatsender.profilePublicKey();
        if (profilepublickey != null) {
            if (profilepublickey.data().hasExpired()) {
                this.player.sendSystemMessage(Component.translatable("chat.disabled.expiredProfileKey").withStyle(ChatFormatting.RED));
                return false;
            }

            if (!pMessage.verify(chatsender)) {
                this.disconnect(Component.translatable("multiplayer.disconnect.unsigned_chat"));
                return false;
            }
        }

        if (pMessage.hasExpiredServer(Instant.now())) {
            LOGGER.warn("{} sent expired chat: '{}'. Is the client/server system time unsynchronized?", this.player.getName().getString(), pMessage.signedContent().plain());
        }

        return true;
    }

    private void detectRateSpam() {
        this.chatSpamTickCount += 20;
        if (this.chatSpamTickCount > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) {
            this.disconnect(Component.translatable("disconnect.spam"));
        }
    }

    public void handleChatPreview(ServerboundChatPreviewPacket pPacket) {
        if (this.handlesPreviewRequests()) {
            this.chatPreviewThrottler.schedule(() -> {
                int i = pPacket.queryId();
                String s = pPacket.query();
                return this.queryPreview(s).thenAccept((p_238195_) -> {
                    this.sendPreviewResponse(i, p_238195_);
                });
            });
        }
    }

    private boolean handlesPreviewRequests() {
        return this.server.previewsChat() || this.connection.isMemoryConnection();
    }

    private void sendPreviewResponse(int pQueryId, Component pPreview) {
        this.send(new ClientboundChatPreviewPacket(pQueryId, pPreview), PacketSendListener.exceptionallySend(() -> {
            return new ClientboundChatPreviewPacket(pQueryId, (Component) null);
        }));
    }

    private CompletableFuture<Component> queryPreview(String pMessage) {
        String s = StringUtils.normalizeSpace(pMessage);
        return s.startsWith("/") ? this.queryCommandPreview(s.substring(1)) : this.queryChatPreview(pMessage);
    }

    private CompletableFuture<Component> queryChatPreview(String pMessage) {
        Component component = Component.literal(pMessage);
        CompletableFuture<Component> completablefuture = this.server.getChatDecorator().decorate(this.player, component).thenApply((p_238202_) -> {
            return !component.equals(p_238202_) ? p_238202_ : null;
        });
        completablefuture.thenAcceptAsync((p_242747_) -> {
            this.chatPreviewCache.set(pMessage, p_242747_);
        }, this.server);
        return completablefuture;
    }

    private CompletableFuture<Component> queryCommandPreview(String pCommand) {
        CommandSourceStack commandsourcestack = this.player.createCommandSourceStack();
        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(pCommand, commandsourcestack);
        CompletableFuture<Component> completablefuture = this.getPreviewedArgument(commandsourcestack, PreviewableCommand.of(parseresults));
        completablefuture.thenAcceptAsync((p_242743_) -> {
            this.chatPreviewCache.set(pCommand, p_242743_);
        }, this.server);
        return completablefuture;
    }

    private CompletableFuture<Component> getPreviewedArgument(CommandSourceStack pSource, PreviewableCommand<CommandSourceStack> pCommand) {
        List<PreviewableCommand.Argument<CommandSourceStack>> list = pCommand.arguments();
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture((Component) null);
        } else {
            for (int i = list.size() - 1; i >= 0; --i) {
                PreviewableCommand.Argument<CommandSourceStack> argument = list.get(i);

                try {
                    CompletableFuture<Component> completablefuture = argument.previewType().resolvePreview(pSource, argument.parsedValue());
                    if (completablefuture != null) {
                        return completablefuture;
                    }
                } catch (CommandSyntaxException commandsyntaxexception) {
                    return CompletableFuture.completedFuture((Component) null);
                }
            }

            return CompletableFuture.completedFuture((Component) null);
        }
    }

    public void handleChatAck(ServerboundChatAckPacket pPacket) {
        Set<LastSeenMessagesValidator.ErrorCondition> set;
        synchronized (this.lastSeenMessagesValidator) {
            set = this.lastSeenMessagesValidator.validateAndUpdate(pPacket.lastSeenMessages());
        }

        if (!set.isEmpty()) {
            this.handleValidationFailure(set);
        }
    }

    private void handleValidationFailure(Set<LastSeenMessagesValidator.ErrorCondition> pErrorConditions) {
        LOGGER.warn("Failed to validate message from {}, reasons: {}", this.player.getName().getString(), pErrorConditions.stream().map(LastSeenMessagesValidator.ErrorCondition::message).collect(Collectors.joining(",")));
        this.disconnect(Component.translatable("multiplayer.disconnect.chat_validation_failed"));
    }

    public void handleAnimate(ServerboundSwingPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        this.player.swing(pPacket.getHand());
    }

    public void handlePlayerCommand(ServerboundPlayerCommandPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        switch (pPacket.getAction()) {
            case PRESS_SHIFT_KEY:
                this.player.setShiftKeyDown(true);
                break;
            case RELEASE_SHIFT_KEY:
                this.player.setShiftKeyDown(false);
                break;
            case START_SPRINTING:
                this.player.setSprinting(true);
                break;
            case STOP_SPRINTING:
                this.player.setSprinting(false);
                break;
            case STOP_SLEEPING:
                if (this.player.isSleeping()) {
                    this.player.stopSleepInBed(false, true);
                    this.awaitingPositionFromClient = this.player.position();
                    this.awaitingSectorPositionFromClient = this.player.hasSectorPosition()
                            ? this.player.sectorPosition()
                            : null;
                }
                break;
            case START_RIDING_JUMP:
                if (this.player.getVehicle() instanceof PlayerRideableJumping) {
                    PlayerRideableJumping playerrideablejumping1 = (PlayerRideableJumping) this.player.getVehicle();
                    int i = pPacket.getData();
                    if (playerrideablejumping1.canJump() && i > 0) {
                        playerrideablejumping1.handleStartJump(i);
                    }
                }
                break;
            case STOP_RIDING_JUMP:
                if (this.player.getVehicle() instanceof PlayerRideableJumping) {
                    PlayerRideableJumping playerrideablejumping = (PlayerRideableJumping) this.player.getVehicle();
                    playerrideablejumping.handleStopJump();
                }
                break;
            case OPEN_INVENTORY:
                Entity $$2 = this.player.getVehicle();
                if ($$2 instanceof HasCustomInventoryScreen hascustominventoryscreen) {
                    hascustominventoryscreen.openCustomInventoryScreen(this.player);
                }
                break;
            case START_FALL_FLYING:
                if (!this.player.tryToStartFallFlying()) {
                    this.player.stopFallFlying();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid client command!");
        }
    }

    public SignedMessageChain.Decoder signedMessageDecoder() {
        return this.signedMessageDecoder;
    }

    public void addPendingMessage(PlayerChatMessage pMessage) {
        LastSeenMessages.Entry lastseenmessages$entry = pMessage.toLastSeenEntry();
        if (lastseenmessages$entry != null) {
            int i;
            synchronized (this.lastSeenMessagesValidator) {
                this.lastSeenMessagesValidator.addPending(lastseenmessages$entry);
                i = this.lastSeenMessagesValidator.pendingMessagesCount();
            }

            if (i > 4096) {
                this.disconnect(Component.translatable("multiplayer.disconnect.too_many_pending_chats"));
            }
        }
    }

    public void handleInteract(ServerboundInteractPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        ServerLevel serverlevel = this.player.getLevel();
        final Entity entity = pPacket.getTarget(serverlevel);
        this.player.resetLastActionTime();
        this.player.setShiftKeyDown(pPacket.isUsingSecondaryAction());
        if (entity != null) {
            if (!serverlevel.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                return;
            }

            SectorVec3 targetPosition = entity.exactPosition();
            if (targetPosition != null) {
                double targetY = Mth.clamp(this.player.getEyeY(), entity.getBoundingBox().minY,
                        entity.getBoundingBox().maxY);
                targetPosition = targetPosition.withY(targetY);
            }
            if (targetPosition != null
                            ? this.player.exactDistanceToSqr(targetPosition) < MAX_INTERACTION_DISTANCE
                            : entity.distanceToSqr(this.player.getEyePosition()) < MAX_INTERACTION_DISTANCE) {
                pPacket.dispatch(new ServerboundInteractPacket.Handler() {
                    private void performInteraction(InteractionHand p_143679_, ServerGamePacketListenerImpl.EntityInteraction p_143680_) {
                        ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(p_143679_).copy();
                        InteractionResult interactionresult = p_143680_.run(ServerGamePacketListenerImpl.this.player, entity, p_143679_);
                        if (interactionresult.consumesAction()) {
                            CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemstack, entity);
                            if (interactionresult.shouldSwing()) {
                                ServerGamePacketListenerImpl.this.player.swing(p_143679_, true);
                            }
                        }
                    }

                    public void onInteraction(InteractionHand p_143677_) {
                        this.performInteraction(p_143677_, Player::interactOn);
                    }

                    public void onInteraction(InteractionHand p_143682_, Vec3 p_143683_) {
                        this.performInteraction(p_143682_, (p_143686_, p_143687_, p_143688_) -> {
                            return p_143687_.interactAt(p_143686_, p_143683_, p_143688_);
                        });
                    }

                    public void onAttack() {
                        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) && !(entity instanceof AbstractArrow) && entity != ServerGamePacketListenerImpl.this.player) {
                            ServerGamePacketListenerImpl.this.player.attack(entity);
                        } else {
                            ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"));
                            ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", (Object) ServerGamePacketListenerImpl.this.player.getName().getString());
                        }
                    }
                });
            }
        }
    }

    public void handleClientCommand(ServerboundClientCommandPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action serverboundclientcommandpacket$action = pPacket.getAction();
        switch (serverboundclientcommandpacket$action) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true);
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerList().respawn(this.player, false);
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR);
                        this.player.getLevel().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(false, this.server);
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }
    }

    public void handleContainerClose(ServerboundContainerClosePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.doCloseContainer();
    }

    public void handleContainerClick(ServerboundContainerClickPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == pPacket.getContainerId()) {
            if (this.player.isSpectator()) {
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int i = pPacket.getSlotNum();
                if (!this.player.containerMenu.isValidSlotIndex(i)) {
                    LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", this.player.getName(), i, this.player.containerMenu.slots.size());
                } else {
                    boolean flag = pPacket.getStateId() != this.player.containerMenu.getStateId();
                    this.player.containerMenu.suppressRemoteUpdates();
                    this.player.containerMenu.clicked(i, pPacket.getButtonNum(), pPacket.getClickType(), this.player);

                    for (Int2ObjectMap.Entry<ItemStack> entry : Int2ObjectMaps.fastIterable(pPacket.getChangedSlots())) {
                        this.player.containerMenu.setRemoteSlotNoCopy(entry.getIntKey(), entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(pPacket.getCarriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }
                }
            }
        }
    }

    public void handlePlaceRecipe(ServerboundPlaceRecipePacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == pPacket.getContainerId() && this.player.containerMenu instanceof RecipeBookMenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                this.server.getRecipeManager().byKey(pPacket.getRecipe()).ifPresent((p_238200_) -> {
                    ((RecipeBookMenu) this.player.containerMenu).handlePlacement(pPacket.isShiftDown(), p_238200_, this.player);
                });
            }
        }
    }

    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == pPacket.getContainerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, pPacket.getButtonId());
                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }
            }
        }
    }

    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.gameMode.isCreative()) {
            boolean flag = pPacket.getSlotNum() < 0;
            ItemStack itemstack = pPacket.getItem();
            CompoundTag compoundtag = BlockItem.getBlockEntityData(itemstack);
            if (!itemstack.isEmpty() && compoundtag != null && compoundtag.contains("x") && compoundtag.contains("y") && compoundtag.contains("z")) {
                BlockPos blockpos = BlockEntity.getPosFromTag(compoundtag);
                if (this.player.level.isLoaded(blockpos)) {
                    BlockEntity blockentity = this.player.level.getBlockEntity(blockpos);
                    if (blockentity != null) {
                        blockentity.saveToItem(itemstack);
                    }
                }
            }

            boolean flag1 = pPacket.getSlotNum() >= 1 && pPacket.getSlotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getDamageValue() >= 0 && itemstack.getCount() <= 64 && !itemstack.isEmpty();
            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(pPacket.getSlotNum()).set(itemstack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2 && this.dropSpamTickCount < 200) {
                this.dropSpamTickCount += 20;
                this.player.drop(itemstack, true);
            }
        }
    }

    public void handleSignUpdate(ServerboundSignUpdatePacket pPacket) {
        List<String> list = Stream.of(pPacket.getLines()).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
        this.filterTextPacket(list).thenAcceptAsync((p_215245_) -> {
            this.updateSignText(pPacket, p_215245_);
        }, this.server);
    }

    private void updateSignText(ServerboundSignUpdatePacket p_9923_, List<FilteredText> p_9924_) {
        this.player.resetLastActionTime();
        ServerLevel serverlevel = this.player.getLevel();
        BlockPos blockpos = p_9923_.getPos();
        if (serverlevel.hasChunkAt(blockpos)) {
            BlockState blockstate = serverlevel.getBlockState(blockpos);
            BlockEntity blockentity = serverlevel.getBlockEntity(blockpos);
            if (!(blockentity instanceof SignBlockEntity)) {
                return;
            }

            SignBlockEntity signblockentity = (SignBlockEntity) blockentity;
            if (!signblockentity.isEditable() || !this.player.getUUID().equals(signblockentity.getPlayerWhoMayEdit())) {
                LOGGER.warn("Player {} just tried to change non-editable sign", (Object) this.player.getName().getString());
                return;
            }

            for (int i = 0; i < p_9924_.size(); ++i) {
                FilteredText filteredtext = p_9924_.get(i);
                if (this.player.isTextFilteringEnabled()) {
                    signblockentity.setMessage(i, Component.literal(filteredtext.filteredOrEmpty()));
                } else {
                    signblockentity.setMessage(i, Component.literal(filteredtext.raw()), Component.literal(filteredtext.filteredOrEmpty()));
                }
            }

            signblockentity.setChanged();
            serverlevel.sendBlockUpdated(blockpos, blockstate, blockstate, 3);
        }
    }

    public void handleKeepAlive(ServerboundKeepAlivePacket pPacket) {
        if (this.keepAlivePending && pPacket.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);
            this.player.latency = (this.player.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(Component.translatable("disconnect.timeout"));
        }
    }

    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.getAbilities().flying = pPacket.isFlying() && this.player.getAbilities().mayfly;
    }

    public void handleClientInformation(ServerboundClientInformationPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        this.player.updateOptions(pPacket);
    }

    public void handleCustomPayload(ServerboundCustomPayloadPacket pPacket) {
        if (net.minecraft.network.protocol.game.WorldgenDebug.CHANNEL.equals(pPacket.getIdentifier())) {
            PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
            this.worldgenDebugSubscribed = pPacket.getData().readBoolean();
        }
    }

    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficulty(pPacket.getDifficulty(), false);
        }
    }

    public void handleLockDifficulty(ServerboundLockDifficultyPacket pPacket) {
        PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.getLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(pPacket.isLocked());
        }
    }

    public ServerPlayer getPlayer() {
        return this.player;
    }

    @FunctionalInterface
    interface EntityInteraction {
        InteractionResult run(ServerPlayer p_143695_, Entity p_143696_, InteractionHand p_143697_);
    }
}
