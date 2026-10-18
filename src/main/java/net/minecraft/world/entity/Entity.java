package net.minecraft.world.entity;

import net.minecraft.world.phys.SectorPhysicsOrigin;

import net.minecraft.world.phys.SectorAABB;

import net.minecraft.world.phys.SectorVec3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ChatSender;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class Entity implements Nameable, EntityAccess, CommandSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    private static final List<ItemStack> EMPTY_LIST = Collections.emptyList();
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW = 0.5000001D;
    public static final float BREATHING_DISTANCE_BELOW_EYES = 0.11111111F;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    private static final AABB INITIAL_AABB = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final double WATER_FLOW_SCALE = 0.014D;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007D;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335D;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0D;
    private final EntityType<?> type;
    private int id = ENTITY_COUNTER.incrementAndGet();
    public boolean blocksBuilding;
    private ImmutableList<Entity> passengers = ImmutableList.of();
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    public Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    /** Exact X/Z state for every entity. Legacy position remains a compatibility mirror. */
    @Nullable
    private SectorVec3 sectorPosition;
    @Nullable
    private SectorVec3 sectorPositionOld;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement = Vec3.ZERO;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb = INITIAL_AABB;
    protected boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier = Vec3.ZERO;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float walkDistO;
    public float walkDist;
    public float moveDist;
    public float flyDist;
    public float fallDistance;
    private float nextStep = 1.0F;
    public double xOld;
    public double yOld;
    public double zOld;
    public float maxUpStep;
    public boolean noPhysics;
    protected final RandomSource random = RandomSource.create();
    public int tickCount;
    private int remainingFireTicks = -this.getFireImmuneTicks();
    protected boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes = new HashSet<>();
    public int invulnerableTime;
    protected boolean firstTick = true;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback = EntityInLevelCallback.NULL;
    private final VecDeltaCodec packetPositionCodec = new VecDeltaCodec();
    public boolean noCulling;
    public boolean hasImpulse;
    private int portalCooldown;
    protected boolean isInsidePortal;
    protected int portalTime;
    protected BlockPos portalEntrancePos;
    private boolean invulnerable;
    protected UUID uuid = Mth.createInsecureUUID(this.random);
    protected String stringUUID = this.uuid.toString();
    private boolean hasGlowingTag;
    private final Set<String> tags = Sets.newHashSet();
    private final double[] pistonDeltas = new double[] {0.0D, 0.0D, 0.0D};
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public boolean wasOnFire;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    private boolean hasVisualFire;
    @Nullable
    private BlockState feetBlockState = null;

    public Entity(EntityType<?> pEntityType, Level pLevel) {
        this.type = pEntityType;
        this.level = pLevel;
        this.dimensions = pEntityType.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        this.entityData = new SynchedEntityData(this);
        this.entityData.define(DATA_SHARED_FLAGS_ID, (byte) 0);
        this.entityData.define(DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        this.entityData.define(DATA_CUSTOM_NAME_VISIBLE, false);
        this.entityData.define(DATA_CUSTOM_NAME, Optional.empty());
        this.entityData.define(DATA_SILENT, false);
        this.entityData.define(DATA_NO_GRAVITY, false);
        this.entityData.define(DATA_POSE, Pose.STANDING);
        this.entityData.define(DATA_TICKS_FROZEN, 0);
        this.defineSynchedData();
        this.enableSectorPosition(SectorVec3.fromApproximate(0.0D, 0.0D, 0.0D));
        this.eyeHeight = this.getEyeHeight(Pose.STANDING, this.dimensions);
    }

    public boolean isColliding(BlockPos pPos, BlockState pState) {
        VoxelShape voxelshape = pState.getCollisionShape(this.level, pPos, CollisionContext.of(this));
        if (this.hasSectorPosition()) {
            SectorPhysicsOrigin origin = new SectorPhysicsOrigin(pPos.getX(), pPos.getY(), pPos.getZ());
            return Shapes.joinIsNotEmpty(voxelshape, Shapes.create(this.getSectorBoundingBox().toLocalAABB(origin)), BooleanOp.AND);
        }
        VoxelShape voxelshape1 = voxelshape.move((double) pPos.getX(), (double) pPos.getY(), (double) pPos.getZ());
        return Shapes.joinIsNotEmpty(voxelshape1, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        Team team = this.getTeam();
        return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }
    }

    public void syncPacketPositionCodec(double pX, double pY, double pZ) {
        this.packetPositionCodec.setBase(new Vec3(pX, pY, pZ));
    }

    /** Updates the packet delta codec with the authoritative split-coordinate position. */
    public final void syncSectorPacketPositionCodec() {
        if (this.sectorPosition != null) {
            this.packetPositionCodec.setBase(this.sectorPosition);
        }
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int pId) {
        this.id = pId;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String pTag) {
        return this.tags.size() >= 1024 ? false : this.tags.add(pTag);
    }

    public boolean removeTag(String pTag) {
        return this.tags.remove(pTag);
    }

    public void kill() {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    protected abstract void defineSynchedData();

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    public boolean equals(Object pObject) {
        if (pObject instanceof Entity) {
            return ((Entity) pObject).id == this.id;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason pReason) {
        this.setRemoved(pReason);
    }

    public void onClientRemoval() {
    }

    public void setPose(Pose pPose) {
        this.entityData.set(DATA_POSE, pPose);
    }

    public Pose getPose() {
        return this.entityData.get(DATA_POSE);
    }

    public boolean hasPose(Pose p_217004_) {
        return this.getPose() == p_217004_;
    }

    public boolean closerThan(Entity pEntity, double pDistance) {
        return this.distanceToSqr(pEntity) < pDistance * pDistance;
    }

    public boolean closerThan(Entity pEntity, double pHorizontalDistance, double pVerticalDistance) {
        Vec3 delta = pEntity.sectorPosition().relativeTo(this.sectorPosition());
        return Mth.lengthSquared(delta.x, delta.z) < Mth.square(pHorizontalDistance)
                && Mth.square(delta.y) < Mth.square(pVerticalDistance);
    }

    protected void setRot(float pYRot, float pXRot) {
        this.setYRot(pYRot % 360.0F);
        this.setXRot(pXRot % 360.0F);
    }

    public final void setPos(Vec3 pPos) {
        if (pPos instanceof net.minecraft.world.phys.ExactVec3) {
            this.applyExactPosition(net.minecraft.world.phys.ExactVec3.position(pPos));
            return;
        }
        this.setPos(pPos.x(), pPos.y(), pPos.z());
    }

    public void setPos(double p_20210_, double p_20211_, double p_20212_) {
        // Legacy absolute-double ingress. Exact internal player movement must use setSectorPositionRaw.
        if (this.usesSectorPhysics()) {
            this.setSectorPosition(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(p_20210_),
                    WorldBounds.clampVerticalDouble(p_20211_), WorldBounds.clampAbsoluteDouble(p_20212_)));
        } else {
            this.setPosRaw(p_20210_, p_20211_, p_20212_);
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    protected AABB makeBoundingBox() {
        return this.sectorPosition == null ? this.dimensions.makeBoundingBox(this.position)
                                           : AABB.fromSectorBounds(SectorAABB.around(this.sectorPosition,
                                                     (double) this.dimensions.width, (double) this.dimensions.height));
    }

    protected void reapplyPosition() {
        if (this.usesSectorPhysics()) {
            this.refreshLegacyPositionMirror();
        } else {
            this.setPos(this.position.x, this.position.y, this.position.z);
        }
    }

    public final boolean hasSectorPosition() {
        return this.sectorPosition != null;
    }

    public final SectorVec3 sectorPosition() {
        if (this.sectorPosition == null) {
            throw new IllegalStateException("Sector position is not enabled for this entity");
        }
        return this.sectorPosition;
    }

    protected final void enableSectorPosition(SectorVec3 initial) {
        if (initial == null)
            throw new NullPointerException("initial");
        this.sectorPosition = initial;
        this.sectorPositionOld = initial;
        this.applySectorPosition(initial);
    }

    protected final void setSectorPosition(SectorVec3 position) {
        if (!this.usesSectorPhysics()) {
            throw new IllegalStateException("Sector physics is not enabled for this entity");
        }
        this.setSectorPositionRaw(position);
    }

    /** Updates authoritative exact state and mirrors it to legacy caches without using global X/Z arithmetic. */
    protected final void setSectorPositionRaw(SectorVec3 position) {
        if (position == null)
            throw new NullPointerException("position");
        if (!this.usesSectorPhysics()) {
            throw new IllegalStateException("Sector physics is not enabled for this entity");
        }
        if (!position.equals(this.sectorPosition)) {
            this.applySectorPosition(position);
        }
    }

    protected final SectorVec3 oldSectorPosition() {
        if (this.sectorPositionOld == null) {
            throw new IllegalStateException("Sector position is not enabled for this entity");
        }
        return this.sectorPositionOld;
    }

    public final Vec3 sectorPositionDelta() {
        return this.sectorPosition().relativeTo(this.oldSectorPosition());
    }

    /** Exact eye position for range checks and other world interactions. */
    @Nullable
    public final SectorVec3 exactEyePosition() {
        return this.sectorPosition == null ? null : this.sectorPosition.withY(this.getEyeY());
    }

    /** Squared eye-to-point distance in a split-coordinate frame, avoiding absolute X/Z doubles. */
    public final double exactDistanceToSqr(SectorVec3 point) {
        if (point == null)
            throw new NullPointerException("point");
        if (this.sectorPosition != null) {
            return this.exactEyePosition().relativeTo(point).lengthSqr();
        }
        return this.getEyePosition().distanceToSqr(point.toApproximateVec3());
    }

    /** Squared feet-to-point distance in a split-coordinate frame. */
    public final double exactPositionDistanceToSqr(SectorVec3 point) {
        if (point == null)
            throw new NullPointerException("point");
        if (this.sectorPosition != null) {
            return this.sectorPosition.relativeTo(point).lengthSqr();
        }
        return this.position().distanceToSqr(point.toApproximateVec3());
    }

    protected boolean usesSectorPhysics() {
        return true;
    }

    private void applySectorPosition(SectorVec3 position) {
        SectorVec3 previous = this.sectorPosition;
        this.sectorPosition = position;
        this.position = position.toApproximateVec3();
        BlockPos exactBlockPosition = position.blockPosition();
        if (!exactBlockPosition.equals(this.blockPosition)) {
            this.blockPosition = exactBlockPosition;
            this.feetBlockState = null;
            this.chunkPosition = new ChunkPos(exactBlockPosition);
        }
        this.setBoundingBox(this.makeBoundingBox());
        // The packet delta base is the last network target, not the entity's
        // current (possibly interpolated) position. Network handlers update that
        // codec explicitly when they consume a position packet.
        if (previous == null || !previous.equals(position)) {
            this.levelCallback.onMove();
        }
    }

    private void refreshLegacyPositionMirror() {
        if (this.sectorPosition != null) {
            this.position = this.sectorPosition.toApproximateVec3();
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    public void turn(double pYRot, double pXRot) {
        float f = (float) pXRot * 0.15F;
        float f1 = (float) pYRot * 0.15F;
        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }
    }

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        this.level.getProfiler().push("entityBaseTick");
        this.feetBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        this.walkDistO = this.walkDist;
        if (this.sectorPosition != null) {
            this.sectorPositionOld = this.sectorPosition;
        }
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.handleNetherPortal();
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level.isClientSide) {
            this.clearFire();
        } else if (this.remainingFireTicks > 0) {
            if (this.fireImmune()) {
                this.setRemainingFireTicks(this.remainingFireTicks - 4);
                if (this.remainingFireTicks < 0) {
                    this.clearFire();
                }
            } else {
                if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                    this.hurt(DamageSource.ON_FIRE, 1.0F);
                }

                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            }

            if (this.getTicksFrozen() > 0) {
                this.setTicksFrozen(0);
                this.level.levelEvent((Player) null, 1009, this.blockPosition, 1);
            }
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
        }

        this.checkOutOfWorld();
        if (!this.level.isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        this.level.getProfiler().pop();
    }

    public void setSharedFlagOnFire(boolean pIsOnFire) {
        this.setSharedFlag(0, pIsOnFire || this.hasVisualFire);
    }

    public void checkOutOfWorld() {
        if (this.getY() < (double) (this.level.getMinBuildHeight() - 64)) {
            this.outOfWorld();
        }
    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            --this.portalCooldown;
        }
    }

    public int getPortalWaitTime() {
        return 0;
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            this.setSecondsOnFire(15);
            if (this.hurt(DamageSource.LAVA, 4.0F)) {
                this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
            }
        }
    }

    public void setSecondsOnFire(int pSeconds) {
        int i = pSeconds * 20;
        if (this instanceof LivingEntity) {
            i = ProtectionEnchantment.getFireAfterDampener((LivingEntity) this, i);
        }

        if (this.remainingFireTicks < i) {
            this.setRemainingFireTicks(i);
        }
    }

    public void setRemainingFireTicks(int pRemainingFireTicks) {
        this.remainingFireTicks = pRemainingFireTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void outOfWorld() {
        this.discard();
    }

    public boolean isFree(double pX, double pY, double pZ) {
        if (this.usesSectorPhysics()) {
            SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
            SectorAABB exactBox = this.getSectorBoundingBox().move(pX, pY, pZ);
            AABB localBox = exactBox.toLocalAABB(origin);
            return this.sectorNoCollision(exactBox, localBox, origin) && !this.sectorContainsAnyLiquid(exactBox);
        }
        return this.isFree(this.getBoundingBox().move(pX, pY, pZ));
    }

    /** Exact sector equivalent of LevelReader.containsAnyLiquid. */
    protected final boolean sectorContainsAnyLiquid(SectorAABB exactBox) {
        for (long x = exactBox.minBlockXForRange();; x = WorldBounds.addBlockOffset(x, 1L)) {
            for (int y = exactBox.minBlockYForRange(); y < exactBox.maxBlockYExclusive(); ++y) {
                for (long z = exactBox.minBlockZForRange();; z = WorldBounds.addBlockOffset(z, 1L)) {
                    if (!this.level.getFluidState(new BlockPos(x, y, z)).isEmpty())
                        return true;
                    if (z == exactBox.maxBlockZForRangeInclusive() || z == Long.MAX_VALUE)
                        break;
                }
            }
            if (x == exactBox.maxBlockXForRangeInclusive() || x == Long.MAX_VALUE)
                break;
        }
        return false;
    }

    private boolean isFree(AABB pBox) {
        return this.level.noCollision(this, pBox) && !this.level.containsAnyLiquid(pBox);
    }

    /** Origin for one player physics operation; this is not the camera origin. */
    protected final SectorPhysicsOrigin sectorPhysicsOrigin() {
        return SectorPhysicsOrigin.from(this.sectorPosition());
    }

    /** Exact world-space player box. X/Z endpoints remain split coordinates. */
    public final SectorAABB getSectorBoundingBox() {
        SectorAABB exactBox = this.bb.getSectorBounds();
        return exactBox != null ? exactBox : SectorAABB.around(this.sectorPosition(), (double) this.getBbWidth(), (double) this.getBbHeight());
    }

    public final AABB exactUnitCubeAtPosition() {
        return AABB.fromSectorBounds(SectorAABB.around(this.sectorPosition().add(0.5D, 0.0D, 0.5D), 1.0D, 1.0D));
    }

    /** Local physics box. Values are small; the exact world position is never converted to a double here. */
    protected final AABB getLocalBoundingBox(SectorPhysicsOrigin origin) {
        return this.getSectorBoundingBox().toLocalAABB(origin);
    }

    protected final SectorAABB getSectorBoundingBoxForPose(Pose pose) {
        EntityDimensions dimensions = this.getDimensions(pose);
        SectorVec3 position = this.sectorPosition();
        return new SectorAABB(position.blockX(), position.subX(), position.y(),
                position.blockZ(), position.subZ(), position.blockX(), position.subX(),
                position.y() + (double) dimensions.height, position.blockZ(), position.subZ())
                .inflate((double) dimensions.width * 0.5D, 0.0D, (double) dimensions.width * 0.5D);
    }

    protected final AABB getLocalBoundingBoxForPose(Pose pose, SectorPhysicsOrigin origin) {
        return this.getSectorBoundingBoxForPose(pose).toLocalAABB(origin);
    }

    /** Exact/local replacement for level.noCollision; callers must not pass localBox to legacy APIs. */
    protected final boolean sectorNoCollision(SectorAABB exactBox, AABB localBox, SectorPhysicsOrigin origin) {
        for (VoxelShape shape : this.level.getSectorBlockCollisions(this, exactBox, localBox, origin)) {
            if (!shape.isEmpty())
                return false;
        }
        return this.level.getSectorEntityCollisions(this, exactBox, localBox, origin).isEmpty();
    }

    private boolean hasSectorBlockCollision(SectorAABB exactBox, AABB localBox, SectorPhysicsOrigin origin) {
        return !this.sectorNoCollision(exactBox, localBox, origin);
    }

    /** Tests a sector player's current box after a local movement without reconstructing global doubles. */
    protected final boolean sectorNoCollisionAt(double dx, double dy, double dz) {
        SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
        SectorAABB box = this.getSectorBoundingBox().move(dx, dy, dz);
        return this.sectorNoCollision(box, box.toLocalAABB(origin), origin);
    }

    /** Exact collision check for integrated-server movement validation. */
    public final boolean hasExactNoCollision() {
        if (!this.usesSectorPhysics())
            return this.level.noCollision(this);
        SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
        SectorAABB box = this.getSectorBoundingBox();
        return this.sectorNoCollision(box, box.toLocalAABB(origin), origin);
    }

    /** Applies an exact network/teleport position without passing through legacy doubles. */
    public final void applyExactPosition(SectorVec3 position) {
        if (!this.usesSectorPhysics())
            throw new IllegalStateException("Entity does not use sector physics");
        this.setSectorPositionRaw(position);
    }

    /** Public only for the client player's exact auto-jump scan. Returned shapes are local to {@code origin}. */
    public final Iterable<VoxelShape> getSectorBlockCollisions(SectorAABB box, SectorPhysicsOrigin origin) {
        return this.level.getSectorBlockCollisions(this, box, box.toLocalAABB(origin), origin);
    }

    protected final boolean sectorCollidesWithSuffocatingBlock(SectorAABB box) {
        SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
        return new net.minecraft.world.level.SectorBlockCollisions(this.level, this, box, box.toLocalAABB(origin),
                                                    origin, true)
                .hasNext();
    }

    public void setOnGround(boolean pOnGround) {
        this.onGround = pOnGround;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void move(MoverType pType, Vec3 pPos) {
        if (this.usesSectorPhysics()) {
            this.moveWithSectorPhysics(pType, pPos);
            return;
        }
        if (this.noPhysics) {
            this.setPos(this.getX() + pPos.x, this.getY() + pPos.y, this.getZ() + pPos.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (pType == MoverType.PISTON) {
                pPos = this.limitPistonMovement(pPos);
                if (pPos.equals(Vec3.ZERO)) {
                    return;
                }
            }

            this.level.getProfiler().push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                pPos = pPos.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }

            pPos = this.maybeBackOffFromEdge(pPos, pType);
            Vec3 vec3 = this.collide(pPos);
            double d0 = vec3.lengthSqr();
            if (d0 > 1.0E-7D) {
                if (this.fallDistance != 0.0F && d0 >= 1.0D) {
                    BlockHitResult blockhitresult = this.level.clip(new ClipContext(this.position(), this.position().add(vec3), ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this));
                    if (blockhitresult.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                this.setPos(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z);
            }

            this.level.getProfiler().pop();
            this.level.getProfiler().push("rest");
            boolean flag2 = !Mth.equal(pPos.x, vec3.x);
            boolean flag = !Mth.equal(pPos.z, vec3.z);
            this.horizontalCollision = flag2 || flag;
            this.verticalCollision = pPos.y != vec3.y;
            this.verticalCollisionBelow = this.verticalCollision && pPos.y < 0.0D;
            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3);
            } else {
                this.minorHorizontalCollision = false;
            }

            this.onGround = this.verticalCollision && pPos.y < 0.0D;
            BlockPos blockpos = this.getOnPosLegacy();
            BlockState blockstate = this.level.getBlockState(blockpos);
            this.checkFallDamage(vec3.y, this.onGround, blockstate, blockpos);
            if (this.isRemoved()) {
                this.level.getProfiler().pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 vec31 = this.getDeltaMovement();
                    this.setDeltaMovement(flag2 ? 0.0D : vec31.x, vec31.y, flag ? 0.0D : vec31.z);
                }

                Block block = blockstate.getBlock();
                if (pPos.y != vec3.y) {
                    block.updateEntityAfterFallOn(this.level, this);
                }

                if (this.onGround) {
                    block.stepOn(this.level, blockpos, blockstate, this);
                }

                Entity.MovementEmission entity$movementemission = this.getMovementEmission();
                if (entity$movementemission.emitsAnything() && !this.isPassenger()) {
                    double d1 = vec3.x;
                    double d2 = vec3.y;
                    double d3 = vec3.z;
                    this.flyDist += (float) (vec3.length() * 0.6D);
                    boolean flag1 = blockstate.is(BlockTags.CLIMBABLE) || blockstate.is(Blocks.POWDER_SNOW);
                    if (!flag1) {
                        d2 = 0.0D;
                    }

                    this.walkDist += (float) vec3.horizontalDistance() * 0.6F;
                    this.moveDist += (float) Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3) * 0.6F;
                    if (this.moveDist > this.nextStep && !blockstate.isAir()) {
                        this.nextStep = this.nextStep();
                        if (this.isInWater()) {
                            if (entity$movementemission.emitsSounds()) {
                                Entity entity = this.isVehicle() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;
                                float f = entity == this ? 0.35F : 0.4F;
                                Vec3 vec32 = entity.getDeltaMovement();
                                float f1 = Math.min(1.0F, (float) Math.sqrt(vec32.x * vec32.x * (double) 0.2F + vec32.y * vec32.y + vec32.z * vec32.z * (double) 0.2F) * f);
                                this.playSwimSound(f1);
                            }

                            if (entity$movementemission.emitsEvents()) {
                                this.gameEvent(GameEvent.SWIM);
                            }
                        } else {
                            if (entity$movementemission.emitsSounds()) {
                                this.playAmethystStepSound(blockstate);
                                this.playStepSound(blockpos, blockstate);
                            }

                            if (entity$movementemission.emitsEvents() && (this.onGround || pPos.y == 0.0D || this.isInPowderSnow || flag1)) {
                                this.level.gameEvent(GameEvent.STEP, this.position, GameEvent.Context.of(this, this.getBlockStateOn()));
                            }
                        }
                    } else if (blockstate.isAir()) {
                        this.processFlappingMovement();
                    }
                }

                this.tryCheckInsideBlocks();
                float f2 = this.getBlockSpeedFactor();
                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f2, 1.0D, (double) f2));
                if (this.level.getBlockStatesIfLoaded(this.getBoundingBox().deflate(1.0E-6D)).noneMatch((p_20127_) -> {
                        return p_20127_.is(BlockTags.FIRE) || p_20127_.is(Blocks.LAVA);
                    })) {
                    if (this.remainingFireTicks <= 0) {
                        this.setRemainingFireTicks(-this.getFireImmuneTicks());
                    }

                    if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                        this.playEntityOnFireExtinguishedSound();
                    }
                }

                if (this.isOnFire() && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                    this.setRemainingFireTicks(-this.getFireImmuneTicks());
                }

                this.level.getProfiler().pop();
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 pDeltaMovement) {
        return false;
    }

    protected void tryCheckInsideBlocks() {
        try {
            this.checkInsideBlocks();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Checking entity block collision");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being checked for collision");
            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    protected void playEntityOnFireExtinguishedSound() {
        this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }
    }

    /** @deprecated */
    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    private BlockPos getOnPos(float pYOffset) {
        if (this.usesSectorPhysics()) {
            SectorVec3 position = this.sectorPosition();
            int y = Mth.floor(position.y() - (double) pYOffset);
            BlockPos blockpos = new BlockPos(position.blockX(), y, position.blockZ());
            if (this.level.getBlockState(blockpos).isAir()) {
                BlockPos below = blockpos.below();
                BlockState belowState = this.level.getBlockState(below);
                if (belowState.is(BlockTags.FENCES) || belowState.is(BlockTags.WALLS)
                        || belowState.getBlock() instanceof FenceGateBlock)
                    return below;
            }
            return blockpos;
        }
        int i = Mth.floor(this.position.x);
        int j = Mth.floor(this.position.y - (double) pYOffset);
        int k = Mth.floor(this.position.z);
        BlockPos blockpos = new BlockPos(i, j, k);
        if (this.level.getBlockState(blockpos).isAir()) {
            BlockPos blockpos1 = blockpos.below();
            BlockState blockstate = this.level.getBlockState(blockpos1);
            if (blockstate.is(BlockTags.FENCES) || blockstate.is(BlockTags.WALLS) || blockstate.getBlock() instanceof FenceGateBlock) {
                return blockpos1;
            }
        }

        return blockpos;
    }

    protected float getBlockJumpFactor() {
        float f = this.level.getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level.getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        BlockState blockstate = this.level.getBlockState(this.blockPosition());
        float f = blockstate.getBlock().getSpeedFactor();
        if (!blockstate.is(Blocks.WATER) && !blockstate.is(Blocks.BUBBLE_COLUMN)) {
            return (double) f == 1.0D ? this.level.getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f;
        } else {
            return f;
        }
    }

    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        if (this.usesSectorPhysics()) {
            SectorVec3 position = this.sectorPosition();
            return new BlockPos(position.blockX(), Mth.floor(position.y() - 0.5000001D), position.blockZ());
        }
        return new BlockPos(this.position.x, this.getBoundingBox().minY - 0.5000001D, this.position.z);
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 pVec, MoverType pMover) {
        return pVec;
    }

    protected Vec3 limitPistonMovement(Vec3 pPos) {
        if (pPos.lengthSqr() <= 1.0E-7D) {
            return pPos;
        } else {
            long i = this.level.getGameTime();
            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            if (pPos.x != 0.0D) {
                double d2 = this.applyPistonMovementRestriction(Direction.Axis.X, pPos.x);
                return Math.abs(d2) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(d2, 0.0D, 0.0D);
            } else if (pPos.y != 0.0D) {
                double d1 = this.applyPistonMovementRestriction(Direction.Axis.Y, pPos.y);
                return Math.abs(d1) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(0.0D, d1, 0.0D);
            } else if (pPos.z != 0.0D) {
                double d0 = this.applyPistonMovementRestriction(Direction.Axis.Z, pPos.z);
                return Math.abs(d0) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(0.0D, 0.0D, d0);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis pAxis, double pDistance) {
        int i = pAxis.ordinal();
        double d0 = Mth.clamp(pDistance + this.pistonDeltas[i], -0.51D, 0.51D);
        pDistance = d0 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d0;
        return pDistance;
    }

    private void moveWithSectorPhysics(MoverType moverType, Vec3 movement) {
        if (!Double.isFinite(movement.x) || !Double.isFinite(movement.y) || !Double.isFinite(movement.z)) {
            movement = new Vec3(finiteMovementComponent(movement.x), finiteMovementComponent(movement.y),
                    finiteMovementComponent(movement.z));
            this.setDeltaMovement(this.deltaMovement);
        }
        if (this.noPhysics) {
            this.setSectorPositionRaw(this.sectorPosition().add(movement.x, movement.y, movement.z));
            return;
        }
        this.wasOnFire = this.isOnFire();
        if (moverType == MoverType.PISTON) {
            movement = this.limitPistonMovement(movement);
            if (movement.equals(Vec3.ZERO))
                return;
        }
        this.level.getProfiler().push("move");
        if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
            movement = movement.multiply(this.stuckSpeedMultiplier);
            this.stuckSpeedMultiplier = Vec3.ZERO;
            this.setDeltaMovement(Vec3.ZERO);
        }
        movement = this.maybeBackOffFromEdge(movement, moverType);
        SectorVec3 before = this.sectorPosition();
        Vec3 resolved = this.collideSector(movement);
        if (resolved.lengthSqr() != 0.0D) {
            this.setSectorPositionRaw(before.add(resolved.x, resolved.y, resolved.z));
        }
        this.level.getProfiler().pop();
        this.level.getProfiler().push("rest");
        boolean horizontalX = !Mth.equal(movement.x, resolved.x);
        boolean horizontalZ = !Mth.equal(movement.z, resolved.z);
        this.horizontalCollision = horizontalX || horizontalZ;
        this.verticalCollision = movement.y != resolved.y;
        this.verticalCollisionBelow = this.verticalCollision && movement.y < 0.0D;
        this.minorHorizontalCollision = this.horizontalCollision && this.isHorizontalCollisionMinor(resolved);
        this.onGround = this.verticalCollision && movement.y < 0.0D;
        BlockPos onPos = this.getOnPosLegacy();
        BlockState state = this.level.getBlockState(onPos);
        this.checkFallDamage(resolved.y, this.onGround, state, onPos);
        if (!this.isRemoved()) {
            if (this.horizontalCollision) {
                Vec3 delta = this.getDeltaMovement();
                this.setDeltaMovement(horizontalX ? 0.0D : delta.x, delta.y, horizontalZ ? 0.0D : delta.z);
            }
            if (movement.y != resolved.y)
                state.getBlock().updateEntityAfterFallOn(this.level, this);
            if (this.onGround)
                state.getBlock().stepOn(this.level, onPos, state, this);
            this.tryCheckInsideBlocks();
            float speed = this.getBlockSpeedFactor();
            this.setDeltaMovement(this.getDeltaMovement().multiply((double) speed, 1.0D, (double) speed));
        }
        this.level.getProfiler().pop();
    }

    private Vec3 collideSector(Vec3 movement) {
        SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
        SectorAABB exactBox = this.getSectorBoundingBox();
        AABB localBox = exactBox.toLocalAABB(origin);
        Vec3 resolved = this.collideSectorBoundingBox(movement, exactBox, localBox, origin);
        boolean xBlocked = movement.x != resolved.x;
        boolean yBlocked = movement.y != resolved.y;
        boolean zBlocked = movement.z != resolved.z;
        boolean canStep = this.onGround || yBlocked && movement.y < 0.0D;
        if (this.maxUpStep > 0.0F && canStep && (xBlocked || zBlocked)) {
            Vec3 raised = this.collideSectorBoundingBox(new Vec3(movement.x, (double) this.maxUpStep, movement.z),
                    exactBox, localBox, origin);
            SectorAABB horizontalBox = exactBox.expandTowards(movement.x, 0.0D, movement.z);
            Vec3 step = this.collideSectorBoundingBox(new Vec3(0.0D, (double) this.maxUpStep, 0.0D),
                    horizontalBox, horizontalBox.toLocalAABB(origin), origin);
            if (step.y < (double) this.maxUpStep) {
                // Vanilla tests the horizontal leg from the original box moved by the
                // vertical step. Using horizontalBox here expands the player twice and
                // changes wall/step contact behavior, especially against a two-block wall.
                SectorAABB steppedBox = exactBox.move(step.x, step.y, step.z);
                Vec3 across = this.collideSectorBoundingBox(new Vec3(movement.x, 0.0D, movement.z),
                                          steppedBox, steppedBox.toLocalAABB(origin), origin)
                                      .add(step);
                if (across.horizontalDistanceSqr() > raised.horizontalDistanceSqr())
                    raised = across;
            }
            if (raised.horizontalDistanceSqr() > resolved.horizontalDistanceSqr()) {
                return raised.add(this.collideSectorBoundingBox(new Vec3(0.0D, -raised.y + movement.y, 0.0D),
                        exactBox.move(raised.x, raised.y, raised.z), exactBox.move(raised.x, raised.y, raised.z).toLocalAABB(origin), origin));
            }
        }
        return resolved;
    }

    private Vec3 collideSectorBoundingBox(Vec3 movement, SectorAABB exactBox, AABB localBox,
            SectorPhysicsOrigin origin) {
        if (movement.lengthSqr() == 0.0D)
            return movement;
        SectorAABB sweptBox = exactBox.expandTowards(movement.x, movement.y, movement.z);
        AABB sweptLocalBox = localBox.expandTowards(movement);
        List<VoxelShape> entityShapes = this.level.getSectorEntityCollisions(this, sweptBox, sweptLocalBox, origin);
        return collideSectorBoundingBox(this, movement, exactBox, localBox, this.level, entityShapes, origin);
    }

    private Vec3 collide(Vec3 pVec) {
        AABB aabb = this.getBoundingBox();
        List<VoxelShape> list = this.level.getEntityCollisions(this, aabb.expandTowards(pVec));
        Vec3 vec3 = pVec.lengthSqr() == 0.0D ? pVec : collideBoundingBox(this, pVec, aabb, this.level, list);
        boolean flag = pVec.x != vec3.x;
        boolean flag1 = pVec.y != vec3.y;
        boolean flag2 = pVec.z != vec3.z;
        boolean flag3 = this.onGround || flag1 && pVec.y < 0.0D;
        if (this.maxUpStep > 0.0F && flag3 && (flag || flag2)) {
            Vec3 vec31 = collideBoundingBox(this, new Vec3(pVec.x, (double) this.maxUpStep, pVec.z), aabb, this.level, list);
            Vec3 vec32 = collideBoundingBox(this, new Vec3(0.0D, (double) this.maxUpStep, 0.0D), aabb.expandTowards(pVec.x, 0.0D, pVec.z), this.level, list);
            if (vec32.y < (double) this.maxUpStep) {
                Vec3 vec33 = collideBoundingBox(this, new Vec3(pVec.x, 0.0D, pVec.z), aabb.move(vec32), this.level, list).add(vec32);
                if (vec33.horizontalDistanceSqr() > vec31.horizontalDistanceSqr()) {
                    vec31 = vec33;
                }
            }

            if (vec31.horizontalDistanceSqr() > vec3.horizontalDistanceSqr()) {
                return vec31.add(collideBoundingBox(this, new Vec3(0.0D, -vec31.y + pVec.y, 0.0D), aabb.move(vec31), this.level, list));
            }
        }

        return vec3;
    }

    /**
    * Resolves a small movement against blocks addressed by an exact sector box.
    * The movement and returned Vec3 are local deltas; no horizontal world
    * coordinate is represented as a double.
    */
    public static Vec3 collideSectorBoundingBox(@Nullable Entity entity, Vec3 movement, SectorAABB exactBox,
            AABB localBox, Level level, List<VoxelShape> entityShapes,
            SectorPhysicsOrigin origin) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(entityShapes.size() + 1);
        if (!entityShapes.isEmpty()) {
            builder.addAll(entityShapes);
        }
        builder.addAll(level.getSectorBlockCollisions(entity, exactBox.expandTowards(movement.x, movement.y, movement.z),
                localBox.expandTowards(movement), origin));
        return collideWithShapes(movement, localBox, builder.build(), true);
    }

    public static Vec3 collideBoundingBox(@Nullable Entity pEntity, Vec3 pVec, AABB pCollisionBox, Level pLevel, List<VoxelShape> pPotentialHits) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(pPotentialHits.size() + 1);
        if (!pPotentialHits.isEmpty()) {
            builder.addAll(pPotentialHits);
        }

        //      WorldBorder worldborder = p_198898_.getWorldBorder();
        //      boolean flag = p_198895_ != null && worldborder.isInsideCloseToBorder(p_198895_, p_198897_.expandTowards(p_198896_));
        //      if (flag) {
        //         builder.add(worldborder.getCollisionShape());
        //      }

        builder.addAll(pLevel.getBlockCollisions(pEntity, pCollisionBox.expandTowards(pVec)));
        return collideWithShapes(pVec, pCollisionBox, builder.build(), false);
    }

    private static Vec3 collideWithShapes(Vec3 pDeltaMovement, AABB pEntityBB, List<VoxelShape> pShapes, boolean exactSector) {
        if (pShapes.isEmpty()) {
            return pDeltaMovement;
        } else {
            double d0 = pDeltaMovement.x;
            double d1 = pDeltaMovement.y;
            double d2 = pDeltaMovement.z;
            if (d1 != 0.0D) {
                d1 = Shapes.collide(Direction.Axis.Y, pEntityBB, pShapes, d1);
                if (d1 != 0.0D) {
                    pEntityBB = pEntityBB.move(0.0D, d1, 0.0D);
                }
            }

            boolean flag = Math.abs(d0) < Math.abs(d2);
            if (flag && d2 != 0.0D) {
                d2 = exactSector ? safeCollisionDelta(d2, Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2))
                                 : Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2);
                if (d2 != 0.0D) {
                    pEntityBB = pEntityBB.move(0.0D, 0.0D, d2);
                }
            }

            if (d0 != 0.0D) {
                d0 = exactSector ? safeCollisionDelta(d0, Shapes.collide(Direction.Axis.X, pEntityBB, pShapes, d0))
                                 : Shapes.collide(Direction.Axis.X, pEntityBB, pShapes, d0);
                if (!flag && d0 != 0.0D) {
                    pEntityBB = pEntityBB.move(d0, 0.0D, 0.0D);
                }
            }

            if (!flag && d2 != 0.0D) {
                d2 = exactSector ? safeCollisionDelta(d2, Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2))
                                 : Shapes.collide(Direction.Axis.Z, pEntityBB, pShapes, d2);
            }

            return new Vec3(d0, d1, d2);
        }
    }

    /**
    * Vanilla's voxel resolver intentionally keeps a tiny residual at contact.
    * In the sector path the local frame is precise enough to retain it, but
    * clamp a shape result that has crossed the requested direction due to a
    * boundary round-off. This prevents a full block face from being crossed.
    */
    private static double safeCollisionDelta(double requested, double resolved) {
        // Shapes.collide normally returns a delta on the requested side of zero.
        // At very large coordinates a local shape can nevertheless leave a tiny
        // crossed-face result after the X/Z axis has been resolved. Never allow a
        // resolver result to move farther than requested or to cross the origin
        // direction; otherwise the second block in a wall can be skipped.
        if (requested > 0.0D) {
            return resolved < 0.0D ? 0.0D : Math.min(requested, resolved);
        } else {
            return resolved > 0.0D ? 0.0D : Math.max(requested, resolved);
        }
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected void checkInsideBlocks() {
        if (this.usesSectorPhysics()) {
            this.checkInsideSectorBlocks();
            return;
        }
        AABB aabb = this.getBoundingBox();
        BlockPos blockpos = new BlockPos(aabb.minX + 0.001D, aabb.minY + 0.001D, aabb.minZ + 0.001D);
        BlockPos blockpos1 = new BlockPos(aabb.maxX - 0.001D, aabb.maxY - 0.001D, aabb.maxZ - 0.001D);
        if (this.level.hasChunksAt(blockpos, blockpos1)) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (long i = blockpos.getX();; i = WorldBounds.addBlockOffset(i, 1L)) {
                for (int j = blockpos.getY(); j <= blockpos1.getY(); ++j) {
                    for (long k = blockpos.getZ();; k = WorldBounds.addBlockOffset(k, 1L)) {
                        blockpos$mutableblockpos.set(i, j, k);
                        BlockState blockstate = this.level.getBlockState(blockpos$mutableblockpos);

                        try {
                            blockstate.entityInside(this.level, blockpos$mutableblockpos, this);
                            this.onInsideBlock(blockstate);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashreportcategory, this.level, blockpos$mutableblockpos, blockstate);
                            throw new ReportedException(crashreport);
                        }
                        if (k == blockpos1.getZ() || k == Long.MAX_VALUE)
                            break;
                    }
                }
                if (i == blockpos1.getX() || i == Long.MAX_VALUE)
                    break;
            }
        }
    }

    /** Scans exact block addresses derived from the player's split-coordinate box. */
    private void checkInsideSectorBlocks() {
        SectorAABB exact = this.getSectorBoundingBox();
        // The small inset preserves vanilla's boundary behavior without converting the world X/Z to doubles.
        SectorAABB scan = exact.inflate(-0.001D, -0.001D, -0.001D);
        if (!this.level.hasChunksAt(scan.minBlockXForRange(), scan.minBlockYForRange(), scan.minBlockZForRange(),
                    scan.maxBlockXForRangeInclusive(), scan.maxBlockYExclusive(), scan.maxBlockZForRangeInclusive()))
            return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long x = scan.minBlockXForRange();; x = WorldBounds.addBlockOffset(x, 1L)) {
            for (int y = scan.minBlockYForRange(); y < scan.maxBlockYExclusive(); ++y) {
                for (long z = scan.minBlockZForRange();; z = WorldBounds.addBlockOffset(z, 1L)) {
                    pos.set(x, y, z);
                    BlockState state = this.level.getBlockState(pos);
                    try {
                        state.entityInside(this.level, pos, this);
                        this.onInsideBlock(state);
                    } catch (Throwable throwable) {
                        CrashReport report = CrashReport.forThrowable(throwable, "Colliding entity with block");
                        CrashReportCategory category = report.addCategory("Block being collided with");
                        CrashReportCategory.populateBlockDetails(category, this.level, pos, state);
                        throw new ReportedException(report);
                    }
                    if (z == scan.maxBlockZForRangeInclusive() || z == Long.MAX_VALUE)
                        break;
                }
            }
            if (x == scan.maxBlockXForRangeInclusive() || x == Long.MAX_VALUE)
                break;
        }
    }

    protected void onInsideBlock(BlockState pState) {
    }

    public void gameEvent(GameEvent pEvent, @Nullable Entity pEntity) {
        this.level.gameEvent(pEntity, pEvent, this.position);
    }

    public void gameEvent(GameEvent pEvent) {
        this.gameEvent(pEvent, this);
    }

    protected void playStepSound(BlockPos pPos, BlockState pState) {
        if (!pState.getMaterial().isLiquid()) {
            BlockState blockstate = this.level.getBlockState(pPos.above());
            SoundType soundtype = blockstate.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) ? blockstate.getSoundType() : pState.getSoundType();
            this.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
        }
    }

    private void playAmethystStepSound(BlockState pState) {
        if (pState.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20) {
            this.crystalSoundIntensity *= (float) Math.pow(0.997D, (double) (this.tickCount - this.lastCrystalSoundPlayTick));
            this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
            float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
            float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, f1, f);
            this.lastCrystalSoundPlayTick = this.tickCount;
        }
    }

    protected void playSwimSound(float pVolume) {
        this.playSound(this.getSwimSound(), pVolume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {
    }

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent pSound, float pVolume, float pPitch) {
        if (!this.isSilent()) {
            if (this.sectorPosition != null) {
                this.level.playSeededSound((Player) null, this.sectorPosition, pSound, this.getSoundSource(), pVolume,
                        pPitch, this.level.getRandom().nextLong());
            } else {
                this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), pSound, this.getSoundSource(), pVolume, pPitch);
            }
        }
    }

    public void playSound(SoundEvent pSound) {
        if (!this.isSilent()) {
            this.playSound(pSound, 1.0F, 1.0F);
        }
    }

    public boolean isSilent() {
        return this.entityData.get(DATA_SILENT);
    }

    public void setSilent(boolean pIsSilent) {
        this.entityData.set(DATA_SILENT, pIsSilent);
    }

    public boolean isNoGravity() {
        return this.entityData.get(DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean pNoGravity) {
        this.entityData.set(DATA_NO_GRAVITY, pNoGravity);
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    protected void checkFallDamage(double pY, boolean pOnGround, BlockState pState, BlockPos pPos) {
        if (pOnGround) {
            if (this.fallDistance > 0.0F) {
                pState.getBlock().fallOn(this.level, pState, pPos, this, this.fallDistance);
                this.level.gameEvent(GameEvent.HIT_GROUND, this.position, GameEvent.Context.of(this, this.getBlockStateOn()));
            }

            this.resetFallDistance();
        } else if (pY < 0.0D) {
            this.fallDistance -= (float) pY;
        }
    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
        if (this.isVehicle()) {
            for (Entity entity : this.getPassengers()) {
                entity.causeFallDamage(pFallDistance, pMultiplier, pSource);
            }
        }

        return false;
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    private boolean isInRain() {
        BlockPos blockpos = this.blockPosition();
        return this.level.isRainingAt(blockpos) || this.level.isRainingAt(new BlockPos(blockpos.getX(), Mth.floor(this.getBoundingBox().maxY), blockpos.getZ()));
    }

    private boolean isInBubbleColumn() {
        return this.level.getBlockState(this.blockPosition()).is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public ChatSender asChatSender() {
        return ChatSender.SYSTEM;
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level.getFluidState(this.blockPosition).is(FluidTags.WATER));
        }
    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d0 = this.level.dimensionType().ultraWarm() ? 0.007D : 0.0023333333333333335D;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d0);
        return this.isInWater() || flag;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        if (this.getVehicle() instanceof Boat) {
            this.wasTouchingWater = false;
        } else if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }
    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double d0 = this.getEyeY() - (double) 0.11111111F;
        Entity entity = this.getVehicle();
        if (entity instanceof Boat boat) {
            if (!boat.isUnderWater() && boat.getBoundingBox().maxY >= d0 && boat.getBoundingBox().minY <= d0) {
                return;
            }
        }

        // Sector players address the eye fluid using exact X/Z and ordinary small-height Y.
        BlockPos blockpos = this.usesSectorPhysics()
                ? new BlockPos(this.sectorPosition().blockX(), Mth.floor(d0), this.sectorPosition().blockZ())
                : new BlockPos(this.getX(), d0, this.getZ());
        FluidState fluidstate = this.level.getFluidState(blockpos);
        double d1 = (double) ((float) blockpos.getY() + fluidstate.getHeight(this.level, blockpos));
        if (d1 > d0) {
            fluidstate.getTags().forEach(this.fluidOnEyes::add);
        }
    }

    /** Exact particle origin for sector entities; ordinary entities retain their legacy double position. */
    public final SectorVec3 particlePosition(double dx, double dy, double dz) {
        return this.sectorPosition != null ? this.sectorPosition.add(dx, dy, dz)
                                           : SectorVec3.fromApproximate(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    protected void doWaterSplashEffect() {
        Entity entity = this.isVehicle() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3 = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3.x * vec3.x * (double) 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * (double) 0.2F) * f);
        if (f1 < 0.25F) {
            this.playSound(this.getSwimSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) Mth.floor(this.getY());

        for (int i = 0; (float) i < 1.0F + this.dimensions.width * 20.0F; ++i) {
            double d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            double d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level.addParticle(ParticleTypes.BUBBLE, this.particlePosition(d0, (double) (f2 + 1.0F) - this.getY(), d1),
                    vec3.x, vec3.y - this.random.nextDouble() * (double) 0.2F, vec3.z);
        }

        for (int j = 0; (float) j < 1.0F + this.dimensions.width * 20.0F; ++j) {
            double d2 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            double d3 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level.addParticle(ParticleTypes.SPLASH, this.particlePosition(d2, (double) (f2 + 1.0F) - this.getY(), d3), vec3.x, vec3.y, vec3.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    /** @deprecated */
    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level.getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level.getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        SectorVec3 particleOrigin = this.particlePosition(0.0D, -0.2D, 0.0D);
        BlockPos blockpos = particleOrigin.blockPosition();
        BlockState blockstate = this.level.getBlockState(blockpos);
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3 = this.getDeltaMovement();
            this.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), this.particlePosition((this.random.nextDouble() - 0.5D) * (double) this.dimensions.width, 0.1D, (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width), vec3.x * -4.0D, 1.5D, vec3.z * -4.0D);
        }
    }

    public boolean isEyeInFluid(TagKey<Fluid> pFluidTag) {
        return this.fluidOnEyes.contains(pFluidTag);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public void moveRelative(float pAmount, Vec3 pRelative) {
        Vec3 vec3 = getInputVector(pRelative, pAmount, this.getYRot());
        this.setDeltaMovement(this.getDeltaMovement().add(vec3));
    }

    private static Vec3 getInputVector(Vec3 pRelative, float pMotionScaler, float pFacing) {
        double d0 = pRelative.lengthSqr();
        if (d0 < 1.0E-7D) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3 = (d0 > 1.0D ? pRelative.normalize() : pRelative).scale((double) pMotionScaler);
            float f = Mth.sin(pFacing * ((float) Math.PI / 180F));
            float f1 = Mth.cos(pFacing * ((float) Math.PI / 180F));
            return new Vec3(vec3.x * (double) f1 - vec3.z * (double) f, vec3.y, vec3.z * (double) f1 + vec3.x * (double) f);
        }
    }

    /** @deprecated */
    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level.hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level.getLightLevelDependentMagicValue(this.sectorPosition().withY(this.getEyeY()).blockPosition()) : 0.0F;
    }

    public void absMoveTo(double pX, double pY, double pZ, float pYRot, float pXRot) {
        this.absMoveTo(pX, pY, pZ);
        this.setYRot(pYRot % 360.0F);
        this.setXRot(Mth.clamp(pXRot, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void absMoveTo(double pX, double pY, double pZ) {
        this.absMoveTo(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(pX),
                               WorldBounds.clampVerticalDouble(pY), WorldBounds.clampAbsoluteDouble(pZ)),
                this.getYRot(), this.getXRot());
    }

    /** Applies a split-coordinate absolute position while preserving the exact X/Z fractions. */
    public final void absMoveTo(SectorVec3 position, float yRot, float xRot) {
        if (!this.usesSectorPhysics()) {
            throw new IllegalStateException("Entity does not use sector physics");
        }
        Vec3 approximate = position.toApproximateVec3();
        this.xo = approximate.x;
        this.yo = position.y();
        this.zo = approximate.z;
        this.applyExactPosition(position);
        this.setYRot(yRot % 360.0F);
        this.setXRot(Mth.clamp(xRot, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void moveTo(Vec3 pVec) {
        if (pVec instanceof net.minecraft.world.phys.ExactVec3) {
            this.moveTo(net.minecraft.world.phys.ExactVec3.position(pVec), this.getYRot(), this.getXRot());
            return;
        }
        this.moveTo(pVec.x, pVec.y, pVec.z);
    }

    public void moveTo(double p_20105_, double p_20106_, double p_20107_) {
        this.moveTo(p_20105_, p_20106_, p_20107_, this.getYRot(), this.getXRot());
    }

    public void moveTo(BlockPos pPos, float pYRot, float pXRot) {
        this.moveTo(SectorVec3.fromBlockAndFraction(pPos.getX(), 0.5D, (double) pPos.getY(),
                            pPos.getZ(), 0.5D),
                pYRot, pXRot);
    }

    /** Moves to an exact split-coordinate location and resets the interpolation baseline. */
    public final void moveTo(SectorVec3 position, float yRot, float xRot) {
        if (!this.usesSectorPhysics()) {
            throw new IllegalStateException("Entity does not use sector physics");
        }
        this.applyExactPosition(position);
        this.setYRot(yRot);
        this.setXRot(xRot);
        this.setOldPosAndRot();
        this.reapplyPosition();
    }

    public void moveTo(double pX, double pY, double pZ, float pYRot, float pXRot) {
        this.moveTo(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(pX),
                            WorldBounds.clampVerticalDouble(pY), WorldBounds.clampAbsoluteDouble(pZ)),
                pYRot, pXRot);
    }

    public final void setOldPosAndRot() {
        if (this.usesSectorPhysics()) {
            SectorVec3 current = this.sectorPosition();
            this.sectorPositionOld = current;
            // xo/yo/zo and xOld/yOld/zOld remain lossy legacy mirrors for rendering and compatibility.
            Vec3 approximate = current.toApproximateVec3();
            this.xo = approximate.x;
            this.yo = approximate.y;
            this.zo = approximate.z;
            this.xOld = approximate.x;
            this.yOld = approximate.y;
            this.zOld = approximate.z;
        } else {
            double d0 = this.getX();
            double d1 = this.getY();
            double d2 = this.getZ();
            this.xo = d0;
            this.yo = d1;
            this.zo = d2;
            this.xOld = d0;
            this.yOld = d1;
            this.zOld = d2;
        }
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public float distanceTo(Entity pEntity) {
        return (float) Math.sqrt(this.distanceToSqr(pEntity));
    }

    public double distanceToSqr(double pX, double pY, double pZ) {
        return this.distanceToSqr(SectorVec3.fromApproximate(pX, pY, pZ));
    }

    public double distanceToSqr(Entity pEntity) {
        return pEntity.sectorPosition().relativeTo(this.sectorPosition()).lengthSqr();
    }

    public double distanceToSqr(Vec3 pVec) {
        return this.distanceToSqr(SectorVec3.fromApproximate(pVec));
    }

    public double distanceToSqr(SectorVec3 position) {
        return position.relativeTo(this.sectorPosition()).lengthSqr();
    }

    public void playerTouch(Player pPlayer) {
    }

    public void push(Entity pEntity) {
        if (!this.isPassengerOfSameVehicle(pEntity)) {
            if (!pEntity.noPhysics && !this.noPhysics) {
                Vec3 exactDelta = this.sectorPosition != null && pEntity.sectorPosition != null
                        ? pEntity.sectorPosition.relativeTo(this.sectorPosition)
                        : null;
                double d0 = exactDelta != null ? exactDelta.x : pEntity.getX() - this.getX();
                double d1 = exactDelta != null ? exactDelta.z : pEntity.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);
                if (d2 >= (double) 0.01F) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;
                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= (double) 0.05F;
                    d1 *= (double) 0.05F;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!pEntity.isVehicle() && pEntity.isPushable()) {
                        pEntity.push(d0, 0.0D, d1);
                    }
                }
            }
        }
    }

    public void push(double pX, double pY, double pZ) {
        this.setDeltaMovement(this.getDeltaMovement().add(pX, pY, pZ));
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    public boolean hurt(DamageSource pSource, float pAmount) {
        if (this.isInvulnerableTo(pSource)) {
            return false;
        } else {
            this.markHurt();
            return false;
        }
    }

    public final Vec3 getViewVector(float pPartialTicks) {
        return this.calculateViewVector(this.getViewXRot(pPartialTicks), this.getViewYRot(pPartialTicks));
    }

    public float getViewXRot(float pPartialTicks) {
        return pPartialTicks == 1.0F ? this.getXRot() : Mth.lerp(pPartialTicks, this.xRotO, this.getXRot());
    }

    public float getViewYRot(float pPartialTick) {
        return pPartialTick == 1.0F ? this.getYRot() : Mth.lerp(pPartialTick, this.yRotO, this.getYRot());
    }

    protected final Vec3 calculateViewVector(float pXRot, float pYRot) {
        float f = pXRot * ((float) Math.PI / 180F);
        float f1 = -pYRot * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3((double) (f3 * f4), (double) (-f5), (double) (f2 * f4));
    }

    public final Vec3 getUpVector(float pPartialTicks) {
        return this.calculateUpVector(this.getViewXRot(pPartialTicks), this.getViewYRot(pPartialTicks));
    }

    protected final Vec3 calculateUpVector(float pXRot, float pYRot) {
        return this.calculateViewVector(pXRot - 90.0F, pYRot);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float pPartialTicks) {
        if (this.sectorPosition != null) {
            SectorVec3 interpolated = this.interpolatedExactPosition(pPartialTicks);
            return interpolated.withY(interpolated.y() + (double) this.getEyeHeight()).toApproximateVec3();
        }
        double d0 = Mth.lerp((double) pPartialTicks, this.xo, this.getX());
        double d1 = Mth.lerp((double) pPartialTicks, this.yo, this.getY()) + (double) this.getEyeHeight();
        double d2 = Mth.lerp((double) pPartialTicks, this.zo, this.getZ());
        return new Vec3(d0, d1, d2);
    }

    public Vec3 getLightProbePosition(float pPartialTicks) {
        return this.getEyePosition(pPartialTicks);
    }

    /** Exact block used for entity lighting; avoids converting sector X/Z to doubles. */
    public final BlockPos getLightProbeBlockPosition(float partialTick) {
        if (this.sectorPosition != null) {
            SectorVec3 interpolated = this.interpolatedExactPosition(partialTick);
            return interpolated.withY(interpolated.y() + (double) this.getEyeHeight()).blockPosition();
        }
        return new BlockPos(this.getLightProbePosition(partialTick));
    }

    public final Vec3 getPosition(float pPartialTicks) {
        if (this.hasSectorPosition()) {
            return new net.minecraft.world.phys.ExactVec3(this.interpolatedExactPosition(pPartialTicks));
        }
        double d0 = Mth.lerp((double) pPartialTicks, this.xo, this.getX());
        double d1 = Mth.lerp((double) pPartialTicks, this.yo, this.getY());
        double d2 = Mth.lerp((double) pPartialTicks, this.zo, this.getZ());
        return new Vec3(d0, d1, d2);
    }

    public HitResult pick(double pHitDistance, float pPartialTicks, boolean pHitFluids) {
        return this.pick(pHitDistance, pPartialTicks, pHitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE);
    }

    public BlockHitResult pick(double range, float partialTick, ClipContext.Fluid fluidMode) {
        if (this.sectorPosition != null) {
            SectorVec3 interpolated = this.interpolatedExactPosition(partialTick);
            SectorVec3 from = interpolated.withY(interpolated.y() + (double) this.getEyeHeight());
            Vec3 view = this.getViewVector(partialTick);
            SectorVec3 to = from.add(view.x * range, view.y * range, view.z * range);
            return net.minecraft.world.level.SectorClipper.clip(this.level, from, to, this,
                    ClipContext.Block.OUTLINE, fluidMode);
        }
        Vec3 vec3 = this.getEyePosition(partialTick);
        Vec3 vec31 = this.getViewVector(partialTick);
        Vec3 vec32 = vec3.add(vec31.x * range, vec31.y * range, vec31.z * range);
        return this.level.clip(new ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, fluidMode, this));
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public void awardKillScore(Entity pKilled, int pScoreValue, DamageSource pSource) {
        if (pKilled instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer) pKilled, this, pSource);
        }
    }

    public boolean shouldRender(double pX, double pY, double pZ) {
        return this.shouldRender(SectorVec3.fromApproximate(pX, pY, pZ));
    }

    public boolean shouldRender(SectorVec3 camera) {
        return this.shouldRenderAtSqrDistance(this.sectorPosition().relativeTo(camera).lengthSqr());
    }

    public boolean shouldRenderAtSqrDistance(double pDistance) {
        double d0 = this.getBoundingBox().getSize();
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }

        d0 *= 64.0D * viewScale;
        return pDistance < d0 * d0;
    }

    public boolean saveAsPassenger(CompoundTag pCompound) {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            String s = this.getEncodeId();
            if (s == null) {
                return false;
            } else {
                pCompound.putString("id", s);
                this.saveWithoutId(pCompound);
                return true;
            }
        }
    }

    public boolean save(CompoundTag pCompound) {
        return this.isPassenger() ? false : this.saveAsPassenger(pCompound);
    }

    public CompoundTag saveWithoutId(CompoundTag pCompound) {
        try {
            // Keep vanilla's Pos tag as a compatibility mirror, but never use it as
            // the source of truth for sector-aware entities.  Reconstructing a
            // coordinate near or beyond 2^53 through a double permanently drops
            // its block and/or sub-block bits.
            SectorVec3 exactPosition = this.sectorPosition;
            if (this.vehicle != null) {
                pCompound.put("Pos", this.newDoubleList(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
            } else if (exactPosition != null) {
                Vec3 approximate = exactPosition.toApproximateVec3();
                pCompound.put("Pos", this.newDoubleList(approximate.x, approximate.y, approximate.z));
            } else {
                pCompound.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
            }

            if (exactPosition != null) {
                CompoundTag exactTag = new CompoundTag();
                exactTag.putLong("BlockX", exactPosition.blockX());
                exactTag.putDouble("SubX", exactPosition.subX());
                exactTag.putDouble("Y", exactPosition.y());
                exactTag.putLong("BlockZ", exactPosition.blockZ());
                exactTag.putDouble("SubZ", exactPosition.subZ());
                pCompound.put("ExactPos", exactTag);
            }

            Vec3 vec3 = this.getDeltaMovement();
            pCompound.put("Motion", this.newDoubleList(vec3.x, vec3.y, vec3.z));
            pCompound.put("Rotation", this.newFloatList(this.getYRot(), this.getXRot()));
            pCompound.putFloat("FallDistance", this.fallDistance);
            pCompound.putShort("Fire", (short) this.remainingFireTicks);
            pCompound.putShort("Air", (short) this.getAirSupply());
            pCompound.putBoolean("OnGround", this.onGround);
            pCompound.putBoolean("Invulnerable", this.invulnerable);
            pCompound.putInt("PortalCooldown", this.portalCooldown);
            pCompound.putUUID("UUID", this.getUUID());
            Component component = this.getCustomName();
            if (component != null) {
                pCompound.putString("CustomName", Component.Serializer.toJson(component));
            }

            if (this.isCustomNameVisible()) {
                pCompound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                pCompound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                pCompound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                pCompound.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();
            if (i > 0) {
                pCompound.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                pCompound.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            if (!this.tags.isEmpty()) {
                ListTag listtag = new ListTag();

                for (String s : this.tags) {
                    listtag.add(StringTag.valueOf(s));
                }

                pCompound.put("Tags", listtag);
            }

            this.addAdditionalSaveData(pCompound);
            if (this.isVehicle()) {
                ListTag listtag1 = new ListTag();

                for (Entity entity : this.getPassengers()) {
                    CompoundTag compoundtag = new CompoundTag();
                    if (entity.saveAsPassenger(compoundtag)) {
                        listtag1.add(compoundtag);
                    }
                }

                if (!listtag1.isEmpty()) {
                    pCompound.put("Passengers", listtag1);
                }
            }

            return pCompound;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being saved");
            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    public void load(CompoundTag pCompound) {
        try {
            ListTag listtag = pCompound.getList("Pos", 6);
            ListTag listtag1 = pCompound.getList("Motion", 6);
            ListTag listtag2 = pCompound.getList("Rotation", 5);
            double d0 = listtag1.getDouble(0);
            double d1 = listtag1.getDouble(1);
            double d2 = listtag1.getDouble(2);
            this.setDeltaMovement(!Double.isFinite(d0) || Math.abs(d0) > 10.0D ? 0.0D : d0,
                    !Double.isFinite(d1) || Math.abs(d1) > 10.0D ? 0.0D : d1,
                    !Double.isFinite(d2) || Math.abs(d2) > 10.0D ? 0.0D : d2);
            //         double d3 = 3.0000512E7D;
            if (this.usesSectorPhysics() && pCompound.contains("ExactPos", 10)) {
                CompoundTag exactTag = pCompound.getCompound("ExactPos");
                this.applyExactPosition(SectorVec3.fromBlockAndFraction(exactTag.getLong("BlockX"),
                        exactTag.getDouble("SubX"), exactTag.getDouble("Y"),
                        exactTag.getLong("BlockZ"), exactTag.getDouble("SubZ")));
            } else {
                // Legacy saves and non-sector entities continue to use vanilla Pos.
                this.setPosRaw(listtag.getDouble(0), listtag.getDouble(1), listtag.getDouble(2));
            }
            //         this.setPosRaw(Mth.clamp(listtag.getDouble(0), -3.0000512E7D, 3.0000512E7D), Mth.clamp(listtag.getDouble(1), -2.0E7D, 2.0E7D), Mth.clamp(listtag.getDouble(2), -3.0000512E7D, 3.0000512E7D));
            this.setYRot(listtag2.getFloat(0));
            this.setXRot(listtag2.getFloat(1));
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = pCompound.getFloat("FallDistance");
            this.remainingFireTicks = pCompound.getShort("Fire");
            if (pCompound.contains("Air")) {
                this.setAirSupply(pCompound.getShort("Air"));
            }

            this.onGround = pCompound.getBoolean("OnGround");
            this.invulnerable = pCompound.getBoolean("Invulnerable");
            this.portalCooldown = pCompound.getInt("PortalCooldown");
            if (pCompound.hasUUID("UUID")) {
                this.uuid = pCompound.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.getYRot()) && Double.isFinite((double) this.getXRot())) {
                    this.reapplyPosition();
                    this.setRot(this.getYRot(), this.getXRot());
                    if (pCompound.contains("CustomName", 8)) {
                        String s = pCompound.getString("CustomName");

                        try {
                            this.setCustomName(Component.Serializer.fromJson(s));
                        } catch (Exception exception) {
                            LOGGER.warn("Failed to parse entity custom name {}", s, exception);
                        }
                    }

                    this.setCustomNameVisible(pCompound.getBoolean("CustomNameVisible"));
                    this.setSilent(pCompound.getBoolean("Silent"));
                    this.setNoGravity(pCompound.getBoolean("NoGravity"));
                    this.setGlowingTag(pCompound.getBoolean("Glowing"));
                    this.setTicksFrozen(pCompound.getInt("TicksFrozen"));
                    this.hasVisualFire = pCompound.getBoolean("HasVisualFire");
                    if (pCompound.contains("Tags", 9)) {
                        this.tags.clear();
                        ListTag listtag3 = pCompound.getList("Tags", 8);
                        int i = Math.min(listtag3.size(), 1024);

                        for (int j = 0; j < i; ++j) {
                            this.tags.add(listtag3.getString(j));
                        }
                    }

                    this.readAdditionalSaveData(pCompound);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being loaded");
            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    protected final String getEncodeId() {
        EntityType<?> entitytype = this.getType();
        ResourceLocation resourcelocation = EntityType.getKey(entitytype);
        return entitytype.canSerialize() && resourcelocation != null ? resourcelocation.toString() : null;
    }

    protected abstract void readAdditionalSaveData(CompoundTag pCompound);

    protected abstract void addAdditionalSaveData(CompoundTag pCompound);

    protected ListTag newDoubleList(double... pNumbers) {
        ListTag listtag = new ListTag();

        for (double d0 : pNumbers) {
            listtag.add(DoubleTag.valueOf(d0));
        }

        return listtag;
    }

    protected ListTag newFloatList(float... pNumbers) {
        ListTag listtag = new ListTag();

        for (float f : pNumbers) {
            listtag.add(FloatTag.valueOf(f));
        }

        return listtag;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike pItem) {
        return this.spawnAtLocation(pItem, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike pItem, int pOffsetY) {
        return this.spawnAtLocation(new ItemStack(pItem), (float) pOffsetY);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack pStack) {
        return this.spawnAtLocation(pStack, 0.0F);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack pStack, float pOffsetY) {
        if (pStack.isEmpty()) {
            return null;
        } else if (this.level.isClientSide) {
            return null;
        } else {
            SectorVec3 sourcePosition = this.sectorPosition().add(0.0D, (double) pOffsetY, 0.0D);
            ItemEntity itementity = new ItemEntity(this.level, sourcePosition, pStack);
            itementity.setDefaultPickUpDelay();
            this.level.addFreshEntity(itementity);
            return itementity;
        }
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else if (this.usesSectorPhysics()) {
            float width = this.dimensions.width * 0.8F;
            SectorVec3 eye = this.sectorPosition().withY(this.getEyeY());
            SectorAABB exactEyeBox = SectorAABB.around(eye, (double) width, 1.0E-6D);
            SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(eye);
            AABB localEyeBox = exactEyeBox.toLocalAABB(origin);
            return new net.minecraft.world.level.SectorBlockCollisions(this.level, this, exactEyeBox,
                                                        localEyeBox, origin, true)
                    .hasNext();
        } else {
            float f = this.dimensions.width * 0.8F;
            AABB aabb = AABB.ofSize(this.getEyePosition(), (double) f, 1.0E-6D, (double) f);
            return BlockPos.betweenClosedStream(aabb).anyMatch((p_201942_) -> {
                BlockState blockstate = this.level.getBlockState(p_201942_);
                return !blockstate.isAir() && blockstate.isSuffocating(this.level, p_201942_) && Shapes.joinIsNotEmpty(blockstate.getCollisionShape(this.level, p_201942_).move((double) p_201942_.getX(), (double) p_201942_.getY(), (double) p_201942_.getZ()), Shapes.create(aabb), BooleanOp.AND);
            });
        }
    }

    public InteractionResult interact(Player pPlayer, InteractionHand pHand) {
        return InteractionResult.PASS;
    }

    public boolean canCollideWith(Entity pEntity) {
        return pEntity.canBeCollidedWith() && !this.isPassengerOfSameVehicle(pEntity);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public void positionRider(Entity pPassenger) {
        this.positionRider(pPassenger, Entity::setPos);
    }

    private void positionRider(Entity pPassenger, Entity.MoveFunction pCallback) {
        if (this.hasPassenger(pPassenger)) {
            double d0 = this.getY() + this.getPassengersRidingOffset() + pPassenger.getMyRidingOffset();
            if (this.hasSectorPosition() && pPassenger.hasSectorPosition()) {
                pPassenger.applyExactPosition(this.sectorPosition().withY(d0));
            } else {
                pCallback.accept(pPassenger, this.getX(), d0, this.getZ());
            }
        }
    }

    public void onPassengerTurned(Entity pEntityToUpdate) {
    }

    public double getMyRidingOffset() {
        return 0.0D;
    }

    public double getPassengersRidingOffset() {
        return (double) this.dimensions.height * 0.75D;
    }

    public boolean startRiding(Entity pVehicle) {
        return this.startRiding(pVehicle, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof LivingEntity;
    }

    public boolean startRiding(Entity pVehicle, boolean pForce) {
        if (pVehicle == this.vehicle) {
            return false;
        } else {
            for (Entity entity = pVehicle; entity.vehicle != null; entity = entity.vehicle) {
                if (entity.vehicle == this) {
                    return false;
                }
            }

            if (pForce || this.canRide(pVehicle) && pVehicle.canAddPassenger(this)) {
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(Pose.STANDING);
                this.vehicle = pVehicle;
                this.vehicle.addPassenger(this);
                pVehicle.getIndirectPassengersStream().filter((p_185984_) -> {
                                                          return p_185984_ instanceof ServerPlayer;
                                                      })
                        .forEach((p_185982_) -> {
                            CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer) p_185982_);
                        });
                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean canRide(Entity pVehicle) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    protected boolean canEnterPose(Pose pPose) {
        if (this.usesSectorPhysics()) {
            SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
            SectorAABB exact = this.getSectorBoundingBoxForPose(pPose).inflate(-1.0E-7D, -1.0E-7D, -1.0E-7D);
            return this.sectorNoCollision(exact, exact.toLocalAABB(origin), origin);
        }
        return this.level.noCollision(this, this.getBoundingBoxForPose(pPose).deflate(1.0E-7D));
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            this.passengers.get(i).stopRiding();
        }
    }

    public void removeVehicle() {
        if (this.vehicle != null) {
            Entity entity = this.vehicle;
            this.vehicle = null;
            entity.removePassenger(this);
        }
    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected void addPassenger(Entity pPassenger) {
        if (pPassenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(pPassenger);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);
                if (!this.level.isClientSide && pPassenger instanceof Player && !(this.getControllingPassenger() instanceof Player)) {
                    list.add(0, pPassenger);
                } else {
                    list.add(pPassenger);
                }

                this.passengers = ImmutableList.copyOf(list);
            }
        }
    }

    protected void removePassenger(Entity pPassenger) {
        if (pPassenger.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            if (this.passengers.size() == 1 && this.passengers.get(0) == pPassenger) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = this.passengers.stream().filter((p_185980_) -> {
                                                              return p_185980_ != pPassenger;
                                                          })
                                          .collect(ImmutableList.toImmutableList());
            }

            pPassenger.boardingCooldown = 60;
        }
    }

    protected boolean canAddPassenger(Entity pPassenger) {
        return this.passengers.isEmpty();
    }

    public void lerpTo(double pX, double pY, double pZ, float pYRot, float pXRot, int pLerpSteps, boolean pTeleport) {
        this.setPos(pX, pY, pZ);
        this.setRot(pYRot, pXRot);
    }

    /** Applies an exact network target while retaining vanilla's override point. */
    public void lerpTo(SectorVec3 position, float yRot, float xRot, int steps, boolean teleport) {
        this.applyExactPosition(position);
        this.setRot(yRot, xRot);
    }

    public void lerpHeadTo(float pYaw, int pPitch) {
        this.setYHeadRot(pYaw);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHandHoldingItemAngle(Item pItem) {
        if (!(this instanceof Player player)) {
            return Vec3.ZERO;
        } else {
            boolean flag = player.getOffhandItem().is(pItem) && !player.getMainHandItem().is(pItem);
            HumanoidArm humanoidarm = flag ? player.getMainArm().getOpposite() : player.getMainArm();
            return this.calculateViewVector(0.0F, this.getYRot() + (float) (humanoidarm == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5D);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public void handleInsidePortal(BlockPos pPos) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (!this.level.isClientSide && !pPos.equals(this.portalEntrancePos)) {
                this.portalEntrancePos = pPos.immutable();
            }

            this.isInsidePortal = true;
        }
    }

    protected void handleNetherPortal() {
        if (this.level instanceof ServerLevel) {
            int i = this.getPortalWaitTime();
            ServerLevel serverlevel = (ServerLevel) this.level;
            if (this.isInsidePortal) {
                MinecraftServer minecraftserver = serverlevel.getServer();
                ResourceKey<Level> resourcekey = this.level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
                ServerLevel serverlevel1 = minecraftserver.getLevel(resourcekey);
                if (serverlevel1 != null && minecraftserver.isNetherEnabled() && !this.isPassenger() && this.portalTime++ >= i) {
                    this.level.getProfiler().push("portal");
                    this.portalTime = i;
                    this.setPortalCooldown();
                    this.changeDimension(serverlevel1);
                    this.level.getProfiler().pop();
                }

                this.isInsidePortal = false;
            } else {
                if (this.portalTime > 0) {
                    this.portalTime -= 4;
                }

                if (this.portalTime < 0) {
                    this.portalTime = 0;
                }
            }

            this.processPortalCooldown();
        }
    }

    public int getDimensionChangingDelay() {
        return 300;
    }

    public void lerpMotion(double pX, double pY, double pZ) {
        this.setDeltaMovement(pX, pY, pZ);
    }

    public void handleEntityEvent(byte pId) {
        switch (pId) {
            case 53:
                HoneyBlock.showSlideParticles(this);
            default:
        }
    }

    public void animateHurt() {
    }

    public Iterable<ItemStack> getHandSlots() {
        return EMPTY_LIST;
    }

    public Iterable<ItemStack> getArmorSlots() {
        return EMPTY_LIST;
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorSlots());
    }

    public void setItemSlot(EquipmentSlot pSlot, ItemStack pStack) {
    }

    public boolean isOnFire() {
        boolean flag = this.level != null && this.level.isClientSide;
        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean rideableUnderWater() {
        return true;
    }

    public void setShiftKeyDown(boolean pKeyDown) {
        this.setSharedFlag(1, pKeyDown);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean pSprinting) {
        this.setSharedFlag(3, pSprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean pSwimming) {
        this.setSharedFlag(4, pSwimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean pHasGlowingTag) {
        this.hasGlowingTag = pHasGlowingTag;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level.isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(Player pPlayer) {
        if (pPlayer.isSpectator()) {
            return false;
        } else {
            Team team = this.getTeam();
            return team != null && pPlayer != null && pPlayer.getTeam() == team && team.canSeeFriendlyInvisibles() ? false : this.isInvisible();
        }
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> pListenerConsumer) {
    }

    @Nullable
    public Team getTeam() {
        return this.level.getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public boolean isAlliedTo(Entity pEntity) {
        return this.isAlliedTo(pEntity.getTeam());
    }

    public boolean isAlliedTo(Team pTeam) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(pTeam) : false;
    }

    public void setInvisible(boolean pInvisible) {
        this.setSharedFlag(5, pInvisible);
    }

    protected boolean getSharedFlag(int pFlag) {
        return (this.entityData.get(DATA_SHARED_FLAGS_ID) & 1 << pFlag) != 0;
    }

    protected void setSharedFlag(int pFlag, boolean pSet) {
        byte b0 = this.entityData.get(DATA_SHARED_FLAGS_ID);
        if (pSet) {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << pFlag));
        } else {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << pFlag)));
        }
    }

    public int getMaxAirSupply() {
        return 300;
    }

    public int getAirSupply() {
        return this.entityData.get(DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int pAir) {
        this.entityData.set(DATA_AIR_SUPPLY_ID, pAir);
    }

    public int getTicksFrozen() {
        return this.entityData.get(DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int pTicksFrozen) {
        this.entityData.set(DATA_TICKS_FROZEN, pTicksFrozen);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();
        return (float) Math.min(this.getTicksFrozen(), i) / (float) i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel pLevel, LightningBolt pLightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        if (this.remainingFireTicks == 0) {
            this.setSecondsOnFire(8);
        }

        this.hurt(DamageSource.LIGHTNING_BOLT, 5.0F);
    }

    public void onAboveBubbleCol(boolean pDownwards) {
        Vec3 vec3 = this.getDeltaMovement();
        double d0;
        if (pDownwards) {
            d0 = Math.max(-0.9D, vec3.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3.y + 0.1D);
        }

        this.setDeltaMovement(vec3.x, d0, vec3.z);
    }

    public void onInsideBubbleColumn(boolean pDownwards) {
        Vec3 vec3 = this.getDeltaMovement();
        double d0;
        if (pDownwards) {
            d0 = Math.max(-0.3D, vec3.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3.y + 0.06D);
        }

        this.setDeltaMovement(vec3.x, d0, vec3.z);
        this.resetFallDistance();
    }

    public boolean wasKilled(ServerLevel pLevel, LivingEntity pEntity) {
        return true;
    }

    public void resetFallDistance() {
        this.fallDistance = 0.0F;
    }

    protected void moveTowardsClosestSpace(double pX, double pY, double pZ) {
        this.moveTowardsClosestSpace(SectorVec3.fromApproximate(pX, pY, pZ));
    }

    protected void moveTowardsClosestSpace(SectorVec3 position) {
        BlockPos blockpos = position.blockPosition();
        Vec3 vec3 = new Vec3(position.subX(), position.y() - (double) blockpos.getY(), position.subZ());
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.UP;
        double d0 = Double.MAX_VALUE;

        for (Direction direction1 : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            blockpos$mutableblockpos.setWithOffset(blockpos, direction1);
            if (!this.level.getBlockState(blockpos$mutableblockpos).isCollisionShapeFullBlock(this.level, blockpos$mutableblockpos)) {
                double d1 = vec3.get(direction1.getAxis());
                double d2 = direction1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d1 : d1;
                if (d2 < d0) {
                    d0 = d2;
                    direction = direction1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) direction.getAxisDirection().getStep();
        Vec3 vec31 = this.getDeltaMovement().scale(0.75D);
        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement((double) (f1 * f), vec31.y, vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec31.x, (double) (f1 * f), vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec31.x, vec31.y, (double) (f1 * f));
        }
    }

    public void makeStuckInBlock(BlockState pState, Vec3 pMotionMultiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = pMotionMultiplier;
    }

    private static Component removeAction(Component pName) {
        MutableComponent mutablecomponent = pName.plainCopy().setStyle(pName.getStyle().withClickEvent((ClickEvent) null));

        for (Component component : pName.getSiblings()) {
            mutablecomponent.append(removeAction(component));
        }

        return mutablecomponent;
    }

    public Component getName() {
        Component component = this.getCustomName();
        return component != null ? removeAction(component) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity pEntity) {
        return this == pEntity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float pYHeadRot) {
    }

    public void setYBodyRot(float pYBodyRot) {
    }

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity pEntity) {
        return false;
    }

    public String toString() {
        String s = this.level == null ? "~NULL~" : this.level.toString();
        return this.removalReason != null ? String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f, removed=%s]", this.getClass().getSimpleName(), this.getName().getString(), this.id, s, this.getX(), this.getY(), this.getZ(), this.removalReason) : String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]", this.getClass().getSimpleName(), this.getName().getString(), this.id, s, this.getX(), this.getY(), this.getZ());
    }

    public boolean isInvulnerableTo(DamageSource pSource) {
        return this.isRemoved() || this.invulnerable && pSource != DamageSource.OUT_OF_WORLD && !pSource.isCreativePlayer() || pSource.isFire() && this.fireImmune();
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean pIsInvulnerable) {
        this.invulnerable = pIsInvulnerable;
    }

    public void copyPosition(Entity pEntity) {
        SectorVec3 exactPosition = pEntity.exactPosition();
        if (this.usesSectorPhysics() && exactPosition != null) {
            this.moveTo(exactPosition, pEntity.getYRot(), pEntity.getXRot());
        } else {
            this.moveTo(pEntity.sectorPosition(), pEntity.getYRot(), pEntity.getXRot());
        }
    }

    public void restoreFrom(Entity pEntity) {
        CompoundTag compoundtag = pEntity.saveWithoutId(new CompoundTag());
        compoundtag.remove("Dimension");
        this.load(compoundtag);
        this.portalCooldown = pEntity.portalCooldown;
        this.portalEntrancePos = pEntity.portalEntrancePos;
    }

    @Nullable
    public Entity changeDimension(ServerLevel pDestination) {
        if (this.level instanceof ServerLevel && !this.isRemoved()) {
            this.level.getProfiler().push("changeDimension");
            this.unRide();
            this.level.getProfiler().push("reposition");
            PortalInfo portalinfo = this.findDimensionEntryPoint(pDestination);
            if (portalinfo == null) {
                return null;
            } else {
                this.level.getProfiler().popPush("reloading");
                Entity entity = this.getType().create(pDestination);
                if (entity != null) {
                    entity.restoreFrom(this);
                    entity.moveTo(portalinfo.exactPos, portalinfo.yRot, entity.getXRot());
                    entity.setDeltaMovement(portalinfo.speed);
                    pDestination.addDuringTeleport(entity);
                    if (pDestination.dimension() == Level.END) {
                        ServerLevel.makeObsidianPlatform(pDestination);
                    }
                }

                this.removeAfterChangingDimensions();
                this.level.getProfiler().pop();
                ((ServerLevel) this.level).resetEmptyTime();
                pDestination.resetEmptyTime();
                this.level.getProfiler().pop();
                return entity;
            }
        } else {
            return null;
        }
    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
    }

    @Nullable
    protected PortalInfo findDimensionEntryPoint(ServerLevel pDestination) {
        boolean flag = this.level.dimension() == Level.END && pDestination.dimension() == Level.OVERWORLD;
        boolean flag1 = pDestination.dimension() == Level.END;
        if (!flag && !flag1) {
            boolean flag2 = pDestination.dimension() == Level.NETHER;
            if (this.level.dimension() != Level.NETHER && !flag2) {
                return null;
            } else {
                WorldBorder worldborder = pDestination.getWorldBorder();
                double d0 = DimensionType.getTeleportationScale(this.level.dimensionType(), pDestination.dimensionType());
                SectorVec3 origin = this.sectorPosition();
                java.math.BigDecimal scale = java.math.BigDecimal.valueOf(d0);
                SectorVec3 destination = SectorVec3.fromDecimal(
                        java.math.BigDecimal.valueOf(origin.blockX()).add(java.math.BigDecimal.valueOf(origin.subX())).multiply(scale).max(java.math.BigDecimal.valueOf(Long.MIN_VALUE)).min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).toPlainString(),
                        origin.y(),
                        java.math.BigDecimal.valueOf(origin.blockZ()).add(java.math.BigDecimal.valueOf(origin.subZ())).multiply(scale).max(java.math.BigDecimal.valueOf(Long.MIN_VALUE)).min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).toPlainString());
                BlockPos blockpos1 = destination.blockPosition();
                return this.getExitPortal(pDestination, blockpos1, flag2, worldborder).map((p_185941_) -> {
                                                                                      BlockState blockstate = this.level.getBlockState(this.portalEntrancePos);
                                                                                      Direction.Axis direction$axis;
                                                                                      Vec3 vec3;
                                                                                      if (blockstate.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                                                                                          direction$axis = blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                                                                                          BlockUtil.FoundRectangle blockutil$foundrectangle = BlockUtil.getLargestRectangleAround(this.portalEntrancePos, direction$axis, 21, Direction.Axis.Y, 21, (p_185959_) -> {
                                                                                              return this.level.getBlockState(p_185959_) == blockstate;
                                                                                          });
                                                                                          vec3 = this.getRelativePortalPosition(direction$axis, blockutil$foundrectangle);
                                                                                      } else {
                                                                                          direction$axis = Direction.Axis.X;
                                                                                          vec3 = new Vec3(0.5D, 0.0D, 0.0D);
                                                                                      }

                                                                                      return PortalShape.createPortalInfo(pDestination, p_185941_, direction$axis, vec3, this.getDimensions(this.getPose()), this.getDeltaMovement(), this.getYRot(), this.getXRot());
                                                                                  })
                        .orElse((PortalInfo) null);
            }
        } else {
            BlockPos blockpos;
            if (flag1) {
                blockpos = ServerLevel.END_SPAWN_POINT;
            } else {
                blockpos = pDestination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pDestination.getSharedSpawnPos());
            }

            return new PortalInfo(SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, blockpos.getY(), blockpos.getZ(), 0.5D), this.getDeltaMovement(), this.getYRot(), this.getXRot());
        }
    }

    protected Vec3 getRelativePortalPosition(Direction.Axis pAxis, BlockUtil.FoundRectangle pPortal) {
        return PortalShape.getRelativePosition(pPortal, pAxis, this.sectorPosition(), this.getDimensions(this.getPose()));
    }

    protected Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel pDestination, BlockPos pFindFrom, boolean pIsToNether, WorldBorder pWorldBorder) {
        return pDestination.getPortalForcer().findPortalAround(pFindFrom, pIsToNether, pWorldBorder);
    }

    public boolean canChangeDimensions() {
        return true;
    }

    public float getBlockExplosionResistance(Explosion pExplosion, BlockGetter pLevel, BlockPos pPos, BlockState pBlockState, FluidState pFluidState, float pExplosionPower) {
        return pExplosionPower;
    }

    public boolean shouldBlockExplode(Explosion pExplosion, BlockGetter pLevel, BlockPos pPos, BlockState pBlockState, float pExplosionPower) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory pCategory) {
        pCategory.setDetail("Entity Type", () -> {
            return EntityType.getKey(this.getType()) + " (" + this.getClass().getCanonicalName() + ")";
        });
        pCategory.setDetail("Entity ID", this.id);
        pCategory.setDetail("Entity Name", () -> {
            return this.getName().getString();
        });
        pCategory.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        pCategory.setDetail("Entity's Block location", CrashReportCategory.formatLocation(this.level, this.getBlockX(), this.getBlockY(), this.getBlockZ()));
        Vec3 vec3 = this.getDeltaMovement();
        pCategory.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3.x, vec3.y, vec3.z));
        pCategory.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        pCategory.setDetail("Entity's Vehicle", () -> {
            return String.valueOf((Object) this.getVehicle());
        });
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID pUniqueId) {
        this.uuid = pUniqueId;
        this.stringUUID = this.uuid.toString();
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return viewScale;
    }

    public static void setViewScale(double pRenderDistWeight) {
        viewScale = pRenderDistWeight;
    }

    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((p_185975_) -> {
            return p_185975_.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable Component pName) {
        this.entityData.set(DATA_CUSTOM_NAME, Optional.ofNullable(pName));
    }

    @Nullable
    public Component getCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).orElse((Component) null);
    }

    public boolean hasCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).isPresent();
    }

    public void setCustomNameVisible(boolean pAlwaysRenderNameTag) {
        this.entityData.set(DATA_CUSTOM_NAME_VISIBLE, pAlwaysRenderNameTag);
    }

    public boolean isCustomNameVisible() {
        return this.entityData.get(DATA_CUSTOM_NAME_VISIBLE);
    }

    public final void teleportToWithTicket(double pX, double pY, double pZ) {
        this.teleportToWithTicket(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(pX),
                WorldBounds.clampVerticalDouble(pY), WorldBounds.clampAbsoluteDouble(pZ)));
    }

    /** Loads the target chunk and teleports without reconstructing large horizontal doubles. */
    public final void teleportToWithTicket(SectorVec3 position) {
        if (this.level instanceof ServerLevel) {
            ChunkPos chunkpos = new ChunkPos(position.blockPosition());
            ((ServerLevel) this.level).getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 0, this.getId());
            this.level.getChunk(chunkpos.x, chunkpos.z);
            this.teleportTo(position);
        }
    }

    public void dismountTo(double pX, double pY, double pZ) {
        this.teleportTo(pX, pY, pZ);
    }

    /** Exact split-coordinate dismount ingress; never reconstructs a lossy global X/Z double. */
    public void dismountToExact(SectorVec3 position) {
        this.teleportTo(position);
    }

    public void teleportTo(double pX, double pY, double pZ) {
        this.teleportTo(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(pX),
                WorldBounds.clampVerticalDouble(pY), WorldBounds.clampAbsoluteDouble(pZ)));
    }

    /** Teleports this entity and riders to an exact split-coordinate target. */
    public void teleportTo(SectorVec3 position) {
        if (this.level instanceof ServerLevel) {
            this.moveTo(position, this.getYRot(), this.getXRot());
            this.getSelfAndPassengers().forEach((p_185977_) -> {
                for (Entity entity : p_185977_.passengers) {
                    p_185977_.positionRider(entity);
                }
            });
        }
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if (DATA_POSE.equals(pKey)) {
            this.refreshDimensions();
        }
    }

    public void refreshDimensions() {
        EntityDimensions entitydimensions = this.dimensions;
        Pose pose = this.getPose();
        EntityDimensions entitydimensions1 = this.getDimensions(pose);
        this.dimensions = entitydimensions1;
        this.eyeHeight = this.getEyeHeight(pose, entitydimensions1);
        this.reapplyPosition();
        boolean flag = (double) entitydimensions1.width <= 4.0D && (double) entitydimensions1.height <= 4.0D;
        if (!this.level.isClientSide && !this.firstTick && !this.noPhysics && flag && (entitydimensions1.width > entitydimensions.width || entitydimensions1.height > entitydimensions.height) && !(this instanceof Player)) {
            if (this.usesSectorPhysics()) {
                SectorPhysicsOrigin origin = this.sectorPhysicsOrigin();
                SectorVec3 center = this.sectorPosition().add(0.0D, (double) entitydimensions.height / 2.0D, 0.0D);
                Vec3 localCenter = center.toLocal(origin.originBlockX(), origin.originBlockY(), origin.originBlockZ());
                double width = (double) Math.max(0.0F, entitydimensions1.width - entitydimensions.width) + 1.0E-6D;
                double height = (double) Math.max(0.0F, entitydimensions1.height - entitydimensions.height) + 1.0E-6D;
                VoxelShape candidate = Shapes.create(AABB.ofSize(localCenter, width, height, width));
                SectorAABB search = SectorAABB.around(center.add(0.0D, -height / 2.0D, 0.0D), width, height)
                                            .inflate((double) entitydimensions1.width, (double) entitydimensions1.height, (double) entitydimensions1.width);
                VoxelShape obstacles = Shapes.empty();
                for (VoxelShape shape : this.level.getSectorBlockCollisions(this, search, search.toLocalAABB(origin), origin)) {
                    for (AABB box : shape.toAabbs()) {
                        obstacles = Shapes.or(obstacles, Shapes.create(box.inflate((double) entitydimensions1.width / 2.0D, (double) entitydimensions1.height / 2.0D, (double) entitydimensions1.width / 2.0D)));
                    }
                }
                Shapes.join(candidate, obstacles, BooleanOp.ONLY_FIRST).closestPointTo(localCenter).ifPresent(point -> {
                    Vec3 delta = point.subtract(localCenter);
                    this.setSectorPosition(center.add(delta.x, delta.y - (double) entitydimensions1.height / 2.0D, delta.z));
                });
                return;
            }
            Vec3 vec3 = this.position().add(0.0D, (double) entitydimensions.height / 2.0D, 0.0D);
            double d0 = (double) Math.max(0.0F, entitydimensions1.width - entitydimensions.width) + 1.0E-6D;
            double d1 = (double) Math.max(0.0F, entitydimensions1.height - entitydimensions.height) + 1.0E-6D;
            VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3, d0, d1, d0));
            this.level.findFreePosition(this, voxelshape, vec3, (double) entitydimensions1.width, (double) entitydimensions1.height, (double) entitydimensions1.width).ifPresent((p_185956_) -> {
                this.setPos(p_185956_.add(0.0D, (double) (-entitydimensions1.height) / 2.0D, 0.0D));
            });
        }
    }

    public Direction getDirection() {
        return Direction.fromYRot((double) this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer pPlayer) {
        return true;
    }

    public final AABB getBoundingBox() {
        return this.bb;
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox();
    }

    /** Exact X/Z culling bounds for sector-enabled entities. */
    @Nullable
    public SectorAABB getSectorBoundingBoxForCulling() {
        return this.sectorPosition == null ? null : this.getSectorBoundingBox();
    }

    protected AABB getBoundingBoxForPose(Pose pPose) {
        if (this.usesSectorPhysics())
            return AABB.fromSectorBounds(this.getSectorBoundingBoxForPose(pPose));
        EntityDimensions entitydimensions = this.getDimensions(pPose);
        float f = entitydimensions.width / 2.0F;
        Vec3 vec3 = new Vec3(this.getX() - (double) f, this.getY(), this.getZ() - (double) f);
        Vec3 vec31 = new Vec3(this.getX() + (double) f, this.getY() + (double) entitydimensions.height, this.getZ() + (double) f);
        return new AABB(vec3, vec31);
    }

    public final void setBoundingBox(AABB pBb) {
        this.bb = pBb;
    }

    protected float getEyeHeight(Pose pPose, EntityDimensions pDimensions) {
        return pDimensions.height * 0.85F;
    }

    public float getEyeHeight(Pose pPose) {
        return this.getEyeHeight(pPose, this.getDimensions(pPose));
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) this.getEyeHeight(), (double) (this.getBbWidth() * 0.4F));
    }

    public SlotAccess getSlot(int pSlot) {
        return SlotAccess.NULL;
    }

    public void sendSystemMessage(Component pComponent) {
    }

    public Level getCommandSenderWorld() {
        return this.level;
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level.getServer();
    }

    public InteractionResult interactAt(Player pPlayer, Vec3 pVec, InteractionHand pHand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion() {
        return false;
    }

    public void doEnchantDamageEffects(LivingEntity pAttacker, Entity pTarget) {
        if (pTarget instanceof LivingEntity) {
            EnchantmentHelper.doPostHurtEffects((LivingEntity) pTarget, pAttacker);
        }

        EnchantmentHelper.doPostDamageEffects(pAttacker, pTarget);
    }

    public void startSeenByPlayer(ServerPlayer pServerPlayer) {
    }

    public void stopSeenByPlayer(ServerPlayer pServerPlayer) {
    }

    public float rotate(Rotation pTransformRotation) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (pTransformRotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(Mirror pTransformMirror) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (pTransformMirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    @Nullable
    public Entity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : this.passengers.get(0);
    }

    public boolean hasPassenger(Entity pEntity) {
        return this.passengers.contains(pEntity);
    }

    public boolean hasPassenger(Predicate<Entity> pPredicate) {
        for (Entity entity : this.passengers) {
            if (pPredicate.test(entity)) {
                return true;
            }
        }

        return false;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    public Stream<Entity> getSelfAndPassengers() {
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    public Stream<Entity> getPassengersAndSelf() {
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        return () -> {
            return this.getIndirectPassengersStream().iterator();
        };
    }

    public boolean hasExactlyOnePlayerPassenger() {
        return this.getIndirectPassengersStream().filter((p_185943_) -> {
                                                     return p_185943_ instanceof Player;
                                                 })
                       .count()
                == 1L;
    }

    public Entity getRootVehicle() {
        Entity entity;
        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity pEntity) {
        return this.getRootVehicle() == pEntity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity pEntity) {
        return this.getIndirectPassengersStream().anyMatch((p_185946_) -> {
            return p_185946_ == pEntity;
        });
    }

    public boolean isControlledByLocalInstance() {
        Entity entity = this.getControllingPassenger();
        if (entity instanceof Player) {
            return ((Player) entity).isLocalPlayer();
        } else {
            return !this.level.isClientSide;
        }
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double pVehicleWidth, double pPassengerWidth, float pYRot) {
        double d0 = (pVehicleWidth + pPassengerWidth + (double) 1.0E-5F) / 2.0D;
        float f = -Mth.sin(pYRot * ((float) Math.PI / 180F));
        float f1 = Mth.cos(pYRot * ((float) Math.PI / 180F));
        float f2 = Math.max(Math.abs(f), Math.abs(f1));
        return new Vec3((double) f * d0 / (double) f2, 0.0D, (double) f1 * d0 / (double) f2);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity pPassenger) {
        return new net.minecraft.world.phys.ExactVec3(this.getExactDismountLocationForPassenger(pPassenger));
    }

    /**
    * Exact split-coordinate dismount target.  The default preserves the
    * passenger's own sub-block X/Z instead of reconstructing a global double
    * that would snap onto the coarse double grid near 2^63.
    */
    public SectorVec3 getExactDismountLocationForPassenger(LivingEntity p_20124_) {
        return p_20124_.sectorPosition().withY(this.getBoundingBox().maxY);
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    protected int getFireImmuneTicks() {
        return 1;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(this, this.position(), this.getRotationVector(), this.level instanceof ServerLevel ? (ServerLevel) this.level : null, this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.level.getServer(), this);
    }

    protected int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int pLevel) {
        return this.getPermissionLevel() >= pLevel;
    }

    public boolean acceptsSuccess() {
        return this.level.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    public boolean acceptsFailure() {
        return true;
    }

    public boolean shouldInformAdmins() {
        return true;
    }

    public void lookAt(EntityAnchorArgument.Anchor pAnchor, Vec3 pTarget) {
        this.lookAt(pAnchor, SectorVec3.fromApproximate(pTarget));
    }

    public void lookAt(EntityAnchorArgument.Anchor pAnchor, SectorVec3 pTarget) {
        Vec3 delta = pTarget.relativeTo(pAnchor.applyExact(this));
        double d0 = delta.x;
        double d1 = delta.y;
        double d2 = delta.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        this.setXRot(Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)))));
        this.setYRot(Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> pFluidTag, double pMotionScale) {
        if (this.usesSectorPhysics()) {
            return this.updateSectorFluidHeightAndDoFluidPushing(pFluidTag, pMotionScale);
        }
        if (this.touchingUnloadedChunk()) {
            return false;
        } else {
            AABB aabb = this.getBoundingBox().deflate(0.001D);
            int i = Mth.floor(aabb.minX);
            int j = Mth.ceil(aabb.maxX);
            int k = Mth.floor(aabb.minY);
            int l = Mth.ceil(aabb.maxY);
            int i1 = Mth.floor(aabb.minZ);
            int j1 = Mth.ceil(aabb.maxZ);
            double d0 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3 = Vec3.ZERO;
            int k1 = 0;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockpos$mutableblockpos.set(l1, i2, j2);
                        FluidState fluidstate = this.level.getFluidState(blockpos$mutableblockpos);
                        if (fluidstate.is(pFluidTag)) {
                            double d1 = (double) ((float) i2 + fluidstate.getHeight(this.level, blockpos$mutableblockpos));
                            if (d1 >= aabb.minY) {
                                flag1 = true;
                                d0 = Math.max(d1 - aabb.minY, d0);
                                if (flag) {
                                    Vec3 vec31 = fluidstate.getFlow(this.level, blockpos$mutableblockpos);
                                    if (d0 < 0.4D) {
                                        vec31 = vec31.scale(d0);
                                    }

                                    vec3 = vec3.add(vec31);
                                    ++k1;
                                }
                            }
                        }
                    }
                }
            }

            if (vec3.length() > 0.0D) {
                if (k1 > 0) {
                    vec3 = vec3.scale(1.0D / (double) k1);
                }

                if (!(this instanceof Player)) {
                    vec3 = vec3.normalize();
                }

                Vec3 vec32 = this.getDeltaMovement();
                vec3 = vec3.scale(pMotionScale * 1.0D);
                double d2 = 0.003D;
                if (Math.abs(vec32.x) < 0.003D && Math.abs(vec32.z) < 0.003D && vec3.length() < 0.0045000000000000005D) {
                    vec3 = vec3.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3));
            }

            this.fluidHeight.put(pFluidTag, d0);
            return flag1;
        }
    }

    /** Fluid scan over exact world block addresses; flow vectors remain local deltas. */
    private boolean updateSectorFluidHeightAndDoFluidPushing(TagKey<Fluid> tag, double flowScale) {
        if (this.touchingUnloadedChunk())
            return false;
        SectorAABB box = this.getSectorBoundingBox().inflate(-0.001D, -0.001D, -0.001D);
        double maxDepth = 0.0D;
        boolean pushed = this.isPushedByFluid();
        boolean found = false;
        Vec3 flow = Vec3.ZERO;
        int flowCount = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long x = box.minBlockXForRange();; x = WorldBounds.addBlockOffset(x, 1L)) {
            for (int y = box.minBlockYForRange(); y < box.maxBlockYExclusive(); ++y) {
                for (long z = box.minBlockZForRange();; z = WorldBounds.addBlockOffset(z, 1L)) {
                    pos.set(x, y, z);
                    FluidState state = this.level.getFluidState(pos);
                    if (state.is(tag)) {
                        double surface = (double) ((float) y + state.getHeight(this.level, pos));
                        if (surface >= box.minY()) {
                            found = true;
                            maxDepth = Math.max(surface - box.minY(), maxDepth);
                            if (pushed) {
                                Vec3 blockFlow = state.getFlow(this.level, pos);
                                if (maxDepth < 0.4D)
                                    blockFlow = blockFlow.scale(maxDepth);
                                flow = flow.add(blockFlow);
                                ++flowCount;
                            }
                        }
                    }
                    if (z == box.maxBlockZForRangeInclusive() || z == Long.MAX_VALUE)
                        break;
                }
            }
            if (x == box.maxBlockXForRangeInclusive() || x == Long.MAX_VALUE)
                break;
        }
        if (flow.length() > 0.0D) {
            if (flowCount > 0)
                flow = flow.scale(1.0D / (double) flowCount);
            Vec3 velocity = this.getDeltaMovement();
            flow = flow.scale(flowScale);
            if (Math.abs(velocity.x) < 0.003D && Math.abs(velocity.z) < 0.003D && flow.length() < 0.0045000000000000005D) {
                flow = flow.normalize().scale(0.0045000000000000005D);
            }
            this.setDeltaMovement(velocity.add(flow));
        }
        this.fluidHeight.put(tag, maxDepth);
        return found;
    }

    public boolean touchingUnloadedChunk() {
        if (this.usesSectorPhysics()) {
            SectorAABB box = this.getSectorBoundingBox().inflate(1.0D, 1.0D, 1.0D);
            return !this.level.hasChunksAt(box.minBlockXForRange(), box.minBlockZForRange(),
                    box.maxBlockXForRangeInclusive(), box.maxBlockZForRangeInclusive());
        }
        AABB aabb = this.getBoundingBox().inflate(1.0D);
        int i = Mth.floor(aabb.minX);
        int j = Mth.ceil(aabb.maxX);
        int k = Mth.floor(aabb.minZ);
        int l = Mth.ceil(aabb.maxZ);
        return !this.level.hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<Fluid> pFluidTag) {
        return this.fluidHeight.getDouble(pFluidTag);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width;
    }

    public final float getBbHeight() {
        return this.dimensions.height;
    }

    public abstract Packet<?> getAddEntityPacket();

    public EntityDimensions getDimensions(Pose pPose) {
        return this.type.getDimensions();
    }

    public Vec3 position() {
        return this.position;
    }

    /** Exact position for sector-aware callers; null for vanilla entities. */
    @Nullable
    public final SectorVec3 exactPosition() {
        return this.sectorPosition;
    }

    /** Exact position from the previous tick, when this entity uses sector physics. */
    @Nullable
    public final SectorVec3 oldExactPosition() {
        return this.sectorPositionOld;
    }

    /** Exact interpolation without subtracting large doubles. */
    @Nullable
    public final SectorVec3 interpolatedExactPosition(float partialTick) {
        if (this.sectorPosition == null)
            return null;
        SectorVec3 old = this.sectorPositionOld;
        return old == null ? this.sectorPosition : old.lerpTo(this.sectorPosition, (double) partialTick);
    }

    /** Sets the exact previous position for custom entities whose tick is driven by another entity. */
    public final void setOldExactPosition(SectorVec3 position) {
        if (!this.usesSectorPhysics())
            throw new IllegalStateException("Entity does not use sector physics");
        if (position == null)
            throw new NullPointerException("position");
        this.sectorPositionOld = position;
        Vec3 approximate = position.toApproximateVec3();
        this.xo = approximate.x;
        this.yo = approximate.y;
        this.zo = approximate.z;
        this.xOld = approximate.x;
        this.yOld = approximate.y;
        this.zOld = approximate.z;
    }

    /** Returns a small interpolation frame for this entity without subtracting large doubles. */
    public final Vec3 exactPositionRelativeTo(SectorPhysicsOrigin origin) {
        return this.sectorPosition == null ? this.position : this.sectorPosition.toLocal(origin.originBlockX(), origin.originBlockY(), origin.originBlockZ());
    }

    /** Exact local-frame entity query for gameplay ranges. */
    public final <T extends Entity> List<T> getEntitiesInExactRange(Class<T> type, SectorAABB query,
            Predicate<? super T> predicate) {
        SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(this.sectorPosition());
        List<T> result = new java.util.ArrayList<>();
        for (Entity candidate : this.level.getSectorEntities(this, query, query.toLocalAABB(origin), origin,
                     candidate -> candidate != this && type.isInstance(candidate))) {
            T cast = type.cast(candidate);
            if (predicate.test(cast))
                result.add(cast);
        }
        return result;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    /** Updates exact block/chunk bookkeeping without reconstructing a large coordinate as a double. */
    public final void setExactBlockPosition(BlockPos p_217013_) {
        this.blockPosition = p_217013_;
        this.feetBlockState = null;
        this.chunkPosition = new ChunkPos(p_217013_);
    }

    public BlockState getFeetBlockState() {
        if (this.feetBlockState == null) {
            this.feetBlockState = this.level.getBlockState(this.blockPosition());
        }

        return this.feetBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 pMotion) {
        // Malformed AI targets, hostile packets, and zero-length normalizations must
        // never poison an entity for every subsequent tick. Vanilla movement can
        // safely recover by dropping only the non-finite components.
        this.deltaMovement = new Vec3(finiteMovementComponent(pMotion.x),
                finiteMovementComponent(pMotion.y), finiteMovementComponent(pMotion.z));
    }

    private static double finiteMovementComponent(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    public void setDeltaMovement(double pX, double pY, double pZ) {
        this.setDeltaMovement(new Vec3(pX, pY, pZ));
    }

    public final long getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double pScale) {
        return this.sectorPosition().add((double) this.getBbWidth() * pScale, 0.0D, 0.0D).toApproximateVec3().x;
    }

    public double getRandomX(double pScale) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * pScale);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double pScale) {
        return this.position.y + (double) this.getBbHeight() * pScale;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final long getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double pScale) {
        return this.sectorPosition().add(0.0D, 0.0D, (double) this.getBbWidth() * pScale).toApproximateVec3().z;
    }

    public double getRandomZ(double pScale) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * pScale);
    }

    public final void setPosRaw(double pX, double pY, double pZ) {
        // This is still a legacy approximate absolute-double ingress for sector entities.
        // Exact movement must call setSectorPositionRaw(SectorVec3) directly.
        if (this.usesSectorPhysics()) {
            this.setSectorPositionRaw(SectorVec3.fromApproximate(WorldBounds.clampAbsoluteDouble(pX),
                    WorldBounds.clampVerticalDouble(pY), WorldBounds.clampAbsoluteDouble(pZ)));
            return;
        }
        pX = WorldBounds.clampAbsoluteDouble(pX);
        pY = WorldBounds.clampVerticalDouble(pY);
        pZ = WorldBounds.clampAbsoluteDouble(pZ);
        if (this.position.x != pX || this.position.y != pY || this.position.z != pZ) {
            this.position = new Vec3(pX, pY, pZ);
            long i = Mth.lfloor(pX);
            long j = Mth.lfloor(pY);
            long k = Mth.lfloor(pZ);
            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
                this.feetBlockState = null;
                if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }
    }

    public void checkDespawn() {
    }

    public Vec3 getRopeHoldPosition(float pPartialTicks) {
        return this.getPosition(pPartialTicks).add(0.0D, (double) this.eyeHeight * 0.7D, 0.0D);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket pPacket) {
        int i = pPacket.getId();
        SectorVec3 exactPosition = pPacket.getExactPosition();
        this.applyExactPosition(exactPosition);
        this.setOldPosAndRot();
        this.syncSectorPacketPositionCodec();
        this.setXRot(pPacket.getXRot());
        this.setYRot(pPacket.getYRot());
        this.setId(i);
        this.setUUID(pPacket.getUUID());
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean pIsInPowderSnow) {
        this.isInPowderSnow = pIsInPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return (this.isInPowderSnow || this.wasInPowderSnow) && this.canFreeze();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float pYRot) {
        if (!Float.isFinite(pYRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + pYRot + ", discarding.");
        } else {
            this.yRot = pYRot;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float pXRot) {
        if (!Float.isFinite(pXRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + pXRot + ", discarding.");
        } else {
            this.xRot = pXRot;
        }
    }

    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    public final void setRemoved(Entity.RemovalReason pRemovalReason) {
        if (this.removalReason == null) {
            this.removalReason = pRemovalReason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(pRemovalReason);
    }

    protected void unsetRemoved() {
        this.removalReason = null;
    }

    public void setLevelCallback(EntityInLevelCallback pLevelCallback) {
        this.levelCallback = pLevelCallback;
    }

    public boolean shouldBeSaved() {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else if (this.isPassenger()) {
            return false;
        } else {
            return !this.isVehicle() || !this.hasExactlyOnePlayerPassenger();
        }
    }

    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(Level pLevel, BlockPos pPos) {
        return true;
    }

    public Level getLevel() {
        return this.level;
    }

    @FunctionalInterface
    public interface MoveFunction {
        void accept(Entity pEntity, double pX, double pY, double pZ);
    }

    public static enum MovementEmission {
        NONE(false, false),
        SOUNDS(true, false),
        EVENTS(false, true),
        ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(boolean p_146942_, boolean p_146943_) {
            this.sounds = p_146942_;
            this.events = p_146943_;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    public static enum RemovalReason {
        KILLED(true, false),
        DISCARDED(true, false),
        UNLOADED_TO_CHUNK(false, true),
        UNLOADED_WITH_PLAYER(false, false),
        CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(boolean p_146963_, boolean p_146964_) {
            this.destroy = p_146963_;
            this.save = p_146964_;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }
}
