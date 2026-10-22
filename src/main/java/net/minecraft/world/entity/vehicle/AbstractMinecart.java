package net.minecraft.world.entity.vehicle;

import net.minecraft.world.phys.SectorAABB;

import net.minecraft.world.phys.SectorVec3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractMinecart extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_BLOCK = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_CUSTOM_DISPLAY = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1));
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private boolean flipped;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = Util.make(Maps.newEnumMap(RailShape.class), (p_38135_) -> {
        Vec3i vec3i = Direction.WEST.getNormal();
        Vec3i vec3i1 = Direction.EAST.getNormal();
        Vec3i vec3i2 = Direction.NORTH.getNormal();
        Vec3i vec3i3 = Direction.SOUTH.getNormal();
        Vec3i vec3i4 = vec3i.below();
        Vec3i vec3i5 = vec3i1.below();
        Vec3i vec3i6 = vec3i2.below();
        Vec3i vec3i7 = vec3i3.below();
        p_38135_.put(RailShape.NORTH_SOUTH, Pair.of(vec3i2, vec3i3));
        p_38135_.put(RailShape.EAST_WEST, Pair.of(vec3i, vec3i1));
        p_38135_.put(RailShape.ASCENDING_EAST, Pair.of(vec3i4, vec3i1));
        p_38135_.put(RailShape.ASCENDING_WEST, Pair.of(vec3i, vec3i5));
        p_38135_.put(RailShape.ASCENDING_NORTH, Pair.of(vec3i2, vec3i7));
        p_38135_.put(RailShape.ASCENDING_SOUTH, Pair.of(vec3i6, vec3i3));
        p_38135_.put(RailShape.SOUTH_EAST, Pair.of(vec3i3, vec3i1));
        p_38135_.put(RailShape.SOUTH_WEST, Pair.of(vec3i3, vec3i));
        p_38135_.put(RailShape.NORTH_WEST, Pair.of(vec3i2, vec3i));
        p_38135_.put(RailShape.NORTH_EAST, Pair.of(vec3i2, vec3i1));
    });
    private int lSteps;
    private double lx;
    private double ly;
    private double lz;
    @Nullable
    private SectorVec3 exactLerpTarget;
    private double lyr;
    private double lxr;
    private double lxd;
    private double lyd;
    private double lzd;

    protected AbstractMinecart(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.blocksBuilding = true;
    }

    protected AbstractMinecart(EntityType<?> pEntityType, Level pLevel, double pX, double pY, double pZ) {
        this(pEntityType, pLevel, SectorVec3.fromApproximate(pX, pY, pZ));
    }

    protected AbstractMinecart(EntityType<?> pEntityType, Level pLevel, SectorVec3 position) {
        this(pEntityType, pLevel);
        this.applyExactPosition(position);
        this.setOldPosAndRot();
    }

    public static AbstractMinecart createMinecart(Level pLevel, double pX, double pY, double pZ, AbstractMinecart.Type pType) {
        return createMinecart(pLevel, SectorVec3.fromApproximate(pX, pY, pZ), pType);
    }

    public static AbstractMinecart createMinecart(Level pLevel, SectorVec3 position, AbstractMinecart.Type pType) {
        AbstractMinecart minecart;
        if (pType == AbstractMinecart.Type.CHEST) {
            minecart = new MinecartChest(EntityType.CHEST_MINECART, pLevel);
        } else if (pType == AbstractMinecart.Type.FURNACE) {
            minecart = new MinecartFurnace(EntityType.FURNACE_MINECART, pLevel);
        } else if (pType == AbstractMinecart.Type.TNT) {
            minecart = new MinecartTNT(EntityType.TNT_MINECART, pLevel);
        } else if (pType == AbstractMinecart.Type.SPAWNER) {
            minecart = new MinecartSpawner(EntityType.SPAWNER_MINECART, pLevel);
        } else if (pType == AbstractMinecart.Type.HOPPER) {
            minecart = new MinecartHopper(EntityType.HOPPER_MINECART, pLevel);
        } else if (pType == AbstractMinecart.Type.COMMAND_BLOCK) {
            minecart = new MinecartCommandBlock(EntityType.COMMAND_BLOCK_MINECART, pLevel);
        } else {
            minecart = new Minecart(EntityType.MINECART, pLevel);
        }

        minecart.applyExactPosition(position);
        minecart.setOldPosAndRot();
        return minecart;
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_ID_HURT, 0);
        this.entityData.define(DATA_ID_HURTDIR, 1);
        this.entityData.define(DATA_ID_DAMAGE, 0.0F);
        this.entityData.define(DATA_ID_DISPLAY_BLOCK, Block.getId(Blocks.AIR.defaultBlockState()));
        this.entityData.define(DATA_ID_DISPLAY_OFFSET, 6);
        this.entityData.define(DATA_ID_CUSTOM_DISPLAY, false);
    }

    public boolean canCollideWith(Entity pEntity) {
        return Boat.canVehicleCollide(this, pEntity);
    }

    public boolean isPushable() {
        return true;
    }

    protected Vec3 getRelativePortalPosition(Direction.Axis pAxis, BlockUtil.FoundRectangle pPortal) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(pAxis, pPortal));
    }

    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity pLivingEntity) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(pLivingEntity);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(direction);
            BlockPos blockpos = this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            ImmutableList<Pose> immutablelist = pLivingEntity.getDismountPoses();

            for (Pose pose : immutablelist) {
                EntityDimensions entitydimensions = pLivingEntity.getDimensions(pose);
                float f = Math.min(entitydimensions.width, 1.0F) / 2.0F;

                for (int i : POSE_DISMOUNT_HEIGHTS.get(pose)) {
                    for (int[] aint1 : aint) {
                        blockpos$mutableblockpos.setWithOffset(blockpos, aint1[0], i, aint1[1]);
                        double d0 = this.level.getBlockFloorHeight(DismountHelper.nonClimbableShape(this.level, blockpos$mutableblockpos), () -> {
                            return DismountHelper.nonClimbableShape(this.level, blockpos$mutableblockpos.below());
                        });
                        if (DismountHelper.isBlockFloorValid(d0)) {
                            AABB aabb = new AABB((double) (-f), 0.0D, (double) (-f), (double) f, (double) entitydimensions.height, (double) f);
                            Vec3 vec3 = Vec3.upFromBottomCenterOf(blockpos$mutableblockpos, d0);
                            if (DismountHelper.canDismountTo(this.level, pLivingEntity, aabb.move(vec3))) {
                                pLivingEntity.setPose(pose);
                                return vec3;
                            }
                        }
                    }
                }
            }

            double d1 = this.getBoundingBox().maxY;
            blockpos$mutableblockpos.set(blockpos.getX(), Mth.floor(d1), blockpos.getZ());

            for (Pose pose1 : immutablelist) {
                double d2 = (double) pLivingEntity.getDimensions(pose1).height;
                int j = Mth.ceil(d1 - (double) blockpos$mutableblockpos.getY() + d2);
                double d3 = DismountHelper.findCeilingFrom(blockpos$mutableblockpos, j, (p_38149_) -> {
                    return this.level.getBlockState(p_38149_).getCollisionShape(this.level, p_38149_);
                });
                if (d1 + d2 <= d3) {
                    pLivingEntity.setPose(pose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(pLivingEntity);
        }
    }

    /** Exact split-coordinate minecart dismount target derived from long block offsets only. */
    @Override
    public SectorVec3 getExactDismountLocationForPassenger(LivingEntity passenger) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getExactDismountLocationForPassenger(passenger);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(direction);
            BlockPos blockpos = this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            ImmutableList<Pose> immutablelist = passenger.getDismountPoses();

            for (Pose pose : immutablelist) {
                EntityDimensions entitydimensions = passenger.getDimensions(pose);
                float f = Math.min(entitydimensions.width, 1.0F) / 2.0F;

                for (int i : POSE_DISMOUNT_HEIGHTS.get(pose)) {
                    for (int[] aint1 : aint) {
                        blockpos$mutableblockpos.set(WorldBounds.addBlockOffset(blockpos.getX(), (long) aint1[0]),
                                blockpos.getY() + i, WorldBounds.addBlockOffset(blockpos.getZ(), (long) aint1[1]));
                        double d0 = this.level.getBlockFloorHeight(DismountHelper.nonClimbableShape(this.level, blockpos$mutableblockpos), () -> {
                            return DismountHelper.nonClimbableShape(this.level, blockpos$mutableblockpos.below());
                        });
                        if (DismountHelper.isBlockFloorValid(d0)) {
                            SectorVec3 candidate = SectorVec3.fromBlockAndFraction(
                                    blockpos$mutableblockpos.getX(), 0.5D, (double) blockpos$mutableblockpos.getY() + d0,
                                    blockpos$mutableblockpos.getZ(), 0.5D);
                            if (DismountHelper.canExactDismountTo(this.level, passenger, candidate, pose)) {
                                passenger.setPose(pose);
                                return candidate;
                            }
                        }
                    }
                }
            }

            return super.getExactDismountLocationForPassenger(passenger);
        }
    }

    public boolean hurt(DamageSource pSource, float pAmount) {
        if (!this.level.isClientSide && !this.isRemoved()) {
            if (this.isInvulnerableTo(pSource)) {
                return false;
            } else {
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.markHurt();
                this.setDamage(this.getDamage() + pAmount * 10.0F);
                this.gameEvent(GameEvent.ENTITY_DAMAGE, pSource.getEntity());
                boolean flag = pSource.getEntity() instanceof Player && ((Player) pSource.getEntity()).getAbilities().instabuild;
                if (flag || this.getDamage() > 40.0F) {
                    this.ejectPassengers();
                    if (flag && !this.hasCustomName()) {
                        this.discard();
                    } else {
                        this.destroy(pSource);
                    }
                }

                return true;
            }
        } else {
            return true;
        }
    }

    protected float getBlockSpeedFactor() {
        BlockState blockstate = this.level.getBlockState(this.blockPosition());
        return blockstate.is(BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    public void destroy(DamageSource pDamageSource) {
        this.kill();
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack itemstack = new ItemStack(this.getDropItem());
            if (this.hasCustomName()) {
                itemstack.setHoverName(this.getCustomName());
            }

            this.spawnAtLocation(itemstack);
        }
    }

    abstract Item getDropItem();

    public void animateHurt() {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    public boolean isPickable() {
        return !this.isRemoved();
    }

    private static Pair<Vec3i, Vec3i> exits(RailShape pShape) {
        return EXITS.get(pShape);
    }

    public Direction getMotionDirection() {
        return this.flipped ? this.getDirection().getOpposite().getClockWise() : this.getDirection().getClockWise();
    }

    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkOutOfWorld();
        this.handleNetherPortal();
        if (this.level.isClientSide) {
            if (this.lSteps > 0) {
                SectorVec3 nextPosition;
                if (this.exactLerpTarget != null) {
                    nextPosition = this.sectorPosition().lerpTo(this.exactLerpTarget, 1.0D / (double) this.lSteps);
                } else {
                    nextPosition = SectorVec3.fromApproximate(
                            this.getX() + (this.lx - this.getX()) / (double) this.lSteps,
                            this.getY() + (this.ly - this.getY()) / (double) this.lSteps,
                            this.getZ() + (this.lz - this.getZ()) / (double) this.lSteps);
                }
                double d2 = Mth.wrapDegrees(this.lyr - (double) this.getYRot());
                this.setYRot(this.getYRot() + (float) d2 / (float) this.lSteps);
                this.setXRot(this.getXRot() + (float) (this.lxr - (double) this.getXRot()) / (float) this.lSteps);
                --this.lSteps;
                this.applyExactPosition(nextPosition);
                if (this.lSteps == 0)
                    this.exactLerpTarget = null;
                this.setRot(this.getYRot(), this.getXRot());
            } else {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
            }

        } else {
            if (!this.isNoGravity()) {
                double d0 = this.isInWater() ? -0.005D : -0.04D;
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d0, 0.0D));
            }

            SectorVec3 position = this.sectorPosition();
            var k = position.blockX();
            int i = Mth.floor(position.y());
            var j = position.blockZ();
            if (this.level.getBlockState(new BlockPos(k, i - 1, j)).is(BlockTags.RAILS)) {
                --i;
            }

            BlockPos blockpos = new BlockPos(k, i, j);
            BlockState blockstate = this.level.getBlockState(blockpos);
            if (BaseRailBlock.isRail(blockstate)) {
                this.moveAlongTrack(blockpos, blockstate);
                if (blockstate.is(Blocks.ACTIVATOR_RAIL)) {
                    this.activateMinecart(blockpos, blockstate.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.comeOffTrack();
            }

            this.checkInsideBlocks();
            this.setXRot(0.0F);
            Vec3 positionDelta = this.oldSectorPosition().relativeTo(this.sectorPosition());
            double d1 = positionDelta.x;
            double d3 = positionDelta.z;
            if (d1 * d1 + d3 * d3 > 0.001D) {
                this.setYRot((float) (Mth.atan2(d3, d1) * 180.0D / Math.PI));
                if (this.flipped) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double d4 = (double) Mth.wrapDegrees(this.getYRot() - this.yRotO);
            if (d4 < -170.0D || d4 >= 170.0D) {
                this.setYRot(this.getYRot() + 180.0F);
                this.flipped = !this.flipped;
            }

            this.setRot(this.getYRot(), this.getXRot());
            if (this.getMinecartType() == AbstractMinecart.Type.RIDEABLE && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
                List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate((double) 0.2F, 0.0D, (double) 0.2F), EntitySelector.pushableBy(this));
                if (!list.isEmpty()) {
                    for (int l = 0; l < list.size(); ++l) {
                        Entity entity1 = list.get(l);
                        if (!(entity1 instanceof Player) && !(entity1 instanceof IronGolem) && !(entity1 instanceof AbstractMinecart) && !this.isVehicle() && !entity1.isPassenger()) {
                            entity1.startRiding(this);
                        } else {
                            entity1.push(this);
                        }
                    }
                }
            } else {
                for (Entity entity : this.level.getEntities(this, this.getBoundingBox().inflate((double) 0.2F, 0.0D, (double) 0.2F))) {
                    if (!this.hasPassenger(entity) && entity.isPushable() && entity instanceof AbstractMinecart) {
                        entity.push(this);
                    }
                }
            }

            this.updateInWaterStateAndDoFluidPushing();
            if (this.isInLava()) {
                this.lavaHurt();
                this.fallDistance *= 0.5F;
            }

            this.firstTick = false;
        }
    }

    protected double getMaxSpeed() {
        return (this.isInWater() ? 4.0D : 8.0D) / 20.0D;
    }

    public void activateMinecart(BlockPos position, boolean powered) {
    }

    protected void comeOffTrack() {
        double d0 = this.getMaxSpeed();
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(Mth.clamp(vec3.x, -d0, d0), vec3.y, Mth.clamp(vec3.z, -d0, d0));
        if (this.onGround) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95D));
        }
    }

    protected void moveAlongTrack(BlockPos pPos, BlockState pState) {
        this.resetFallDistance();
        SectorVec3 exactPosition = this.sectorPosition();
        double d0 = exactPosition.subX();
        double d1 = exactPosition.y();
        double d2 = exactPosition.subZ();
        SectorVec3 railPositionBefore = this.getExactPos(exactPosition);
        d1 = (double) pPos.getY();
        boolean flag = false;
        boolean flag1 = false;
        if (pState.is(Blocks.POWERED_RAIL)) {
            flag = pState.getValue(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d3 = 0.0078125D;
        if (this.isInWater()) {
            d3 *= 0.2D;
        }

        Vec3 vec31 = this.getDeltaMovement();
        RailShape railshape = pState.getValue(((BaseRailBlock) pState.getBlock()).getShapeProperty());
        switch (railshape) {
            case ASCENDING_EAST:
                this.setDeltaMovement(vec31.add(-d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(vec31.add(d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(vec31.add(0.0D, 0.0D, d3));
                ++d1;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(vec31.add(0.0D, 0.0D, -d3));
                ++d1;
        }

        vec31 = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = exits(railshape);
        Vec3i vec3i = pair.getFirst();
        Vec3i vec3i1 = pair.getSecond();
        double d4 = WorldBounds.signedDifference(vec3i1.getX(), vec3i.getX());
        double d5 = WorldBounds.signedDifference(vec3i1.getZ(), vec3i.getZ());
        double d6 = Math.sqrt(d4 * d4 + d5 * d5);
        double d7 = vec31.x * d4 + vec31.z * d5;
        if (d7 < 0.0D) {
            d4 = -d4;
            d5 = -d5;
        }

        double d8 = Math.min(2.0D, vec31.horizontalDistance());
        vec31 = new Vec3(d8 * d4 / d6, vec31.y, d8 * d5 / d6);
        this.setDeltaMovement(vec31);
        Entity entity = this.getFirstPassenger();
        if (entity instanceof Player) {
            Vec3 vec32 = entity.getDeltaMovement();
            double d9 = vec32.horizontalDistanceSqr();
            double d11 = this.getDeltaMovement().horizontalDistanceSqr();
            if (d9 > 1.0E-4D && d11 < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(vec32.x * 0.1D, 0.0D, vec32.z * 0.1D));
                flag1 = false;
            }
        }

        if (flag1) {
            double d22 = this.getDeltaMovement().horizontalDistance();
            if (d22 < 0.03D) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
            }
        }

        // Rail exits are local unit offsets, not world coordinates.
        double d23 = 0.5D + vec3i.getXLo() * 0.5D;
        double d10 = 0.5D + vec3i.getZLo() * 0.5D;
        double d12 = 0.5D + vec3i1.getXLo() * 0.5D;
        double d13 = 0.5D + vec3i1.getZLo() * 0.5D;
        d4 = d12 - d23;
        d5 = d13 - d10;
        double d14;
        if (d4 == 0.0D) {
            d14 = d2;
        } else if (d5 == 0.0D) {
            d14 = d0;
        } else {
            double d15 = d0 - d23;
            double d16 = d2 - d10;
            d14 = (d15 * d4 + d16 * d5) * 2.0D;
        }

        d0 = d23 + d4 * d14;
        d2 = d10 + d5 * d14;
        this.applyExactPosition(SectorVec3.fromBlockAndFraction(pPos.getX(), d0, d1, pPos.getZ(), d2));
        double d24 = this.isVehicle() ? 0.75D : 1.0D;
        double d25 = this.getMaxSpeed();
        vec31 = this.getDeltaMovement();
        this.move(MoverType.SELF, new Vec3(Mth.clamp(d24 * vec31.x, -d25, d25), 0.0D, Mth.clamp(d24 * vec31.z, -d25, d25)));
        SectorVec3 postMovePosition = this.sectorPosition();
        if (vec3i.getY() != 0 && postMovePosition.blockX().subtract(pPos.getX()).equals(vec3i.getX())
                && postMovePosition.blockZ().subtract(pPos.getZ()).equals(vec3i.getZ())) {
            this.applyExactPosition(postMovePosition.withY(postMovePosition.y() + (double) vec3i.getY()));
        } else if (vec3i1.getY() != 0 && postMovePosition.blockX().subtract(pPos.getX()).equals(vec3i1.getX())
                && postMovePosition.blockZ().subtract(pPos.getZ()).equals(vec3i1.getZ())) {
            this.applyExactPosition(postMovePosition.withY(postMovePosition.y() + (double) vec3i1.getY()));
        }

        this.applyNaturalSlowdown();
        SectorVec3 railPositionAfter = this.getExactPos(this.sectorPosition());
        if (railPositionAfter != null && railPositionBefore != null) {
            double d17 = (railPositionBefore.y() - railPositionAfter.y()) * 0.05D;
            Vec3 vec34 = this.getDeltaMovement();
            double d18 = vec34.horizontalDistance();
            if (d18 > 0.0D) {
                this.setDeltaMovement(vec34.multiply((d18 + d17) / d18, 1.0D, (d18 + d17) / d18));
            }

            this.applyExactPosition(this.sectorPosition().withY(railPositionAfter.y()));
        }

        SectorVec3 finalPosition = this.sectorPosition();
        var j = finalPosition.blockX();
        var i = finalPosition.blockZ();
        if (!j.equals(pPos.getX()) || !i.equals(pPos.getZ())) {
            Vec3 vec35 = this.getDeltaMovement();
            double d26 = vec35.horizontalDistance();
            this.setDeltaMovement(d26 * WorldBounds.signedDifference(j, pPos.getX()), vec35.y, d26 * WorldBounds.signedDifference(i, pPos.getZ()));
        }

        if (flag) {
            Vec3 vec36 = this.getDeltaMovement();
            double d27 = vec36.horizontalDistance();
            if (d27 > 0.01D) {
                double d19 = 0.06D;
                this.setDeltaMovement(vec36.add(vec36.x / d27 * 0.06D, 0.0D, vec36.z / d27 * 0.06D));
            } else {
                Vec3 vec37 = this.getDeltaMovement();
                double d20 = vec37.x;
                double d21 = vec37.z;
                if (railshape == RailShape.EAST_WEST) {
                    if (this.isRedstoneConductor(pPos.west())) {
                        d20 = 0.02D;
                    } else if (this.isRedstoneConductor(pPos.east())) {
                        d20 = -0.02D;
                    }
                } else {
                    if (railshape != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.isRedstoneConductor(pPos.north())) {
                        d21 = 0.02D;
                    } else if (this.isRedstoneConductor(pPos.south())) {
                        d21 = -0.02D;
                    }
                }

                this.setDeltaMovement(d20, vec37.y, d21);
            }
        }
    }

    private boolean isRedstoneConductor(BlockPos pPos) {
        return this.level.getBlockState(pPos).isRedstoneConductor(this.level, pPos);
    }

    protected void applyNaturalSlowdown() {
        double d0 = this.isVehicle() ? 0.997D : 0.96D;
        Vec3 vec3 = this.getDeltaMovement();
        vec3 = vec3.multiply(d0, 0.0D, d0);
        if (this.isInWater()) {
            vec3 = vec3.scale((double) 0.95F);
        }

        this.setDeltaMovement(vec3);
    }

    @Nullable
    public Vec3 getPosOffs(double x, double y, double z, double offset) {
        SectorVec3 position = this.getExactPosOffs(SectorVec3.fromApproximate(x, y, z), offset);
        return position == null ? null : position.toApproximateVec3();
    }

    /** Calculates an offset rail point without reconstructing an absolute X/Z double. */
    @Nullable
    public SectorVec3 getExactPosOffs(SectorVec3 position, double offset) {
        var blockX = position.blockX();
        int blockY = Mth.floor(position.y());
        var blockZ = position.blockZ();
        if (this.level.getBlockState(new BlockPos(blockX, blockY - 1, blockZ)).is(BlockTags.RAILS)) {
            --blockY;
        }

        BlockState blockstate = this.level.getBlockState(new BlockPos(blockX, blockY, blockZ));
        if (!BaseRailBlock.isRail(blockstate)) {
            return null;
        }

        RailShape railshape = blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());
        double railY = (double) blockY + (railshape.isAscending() ? 1.0D : 0.0D);
        Pair<Vec3i, Vec3i> pair = exits(railshape);
        Vec3i firstExit = pair.getFirst();
        Vec3i secondExit = pair.getSecond();
        double railX = position.subX();
        double railZ = position.subZ();
        double directionX = WorldBounds.signedDifference(secondExit.getX(), firstExit.getX());
        double directionZ = WorldBounds.signedDifference(secondExit.getZ(), firstExit.getZ());
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        railX += directionX / directionLength * offset;
        railZ += directionZ / directionLength * offset;
        SectorVec3 offsetPosition = SectorVec3.fromBlockAndFraction(blockX, railX, railY, blockZ, railZ);
        if (firstExit.getY() != 0 && offsetPosition.blockX().subtract(blockX).equals(firstExit.getX())
                && offsetPosition.blockZ().subtract(blockZ).equals(firstExit.getZ())) {
            offsetPosition = offsetPosition.withY(offsetPosition.y() + (double) firstExit.getY());
        } else if (secondExit.getY() != 0 && offsetPosition.blockX().subtract(blockX).equals(secondExit.getX())
                && offsetPosition.blockZ().subtract(blockZ).equals(secondExit.getZ())) {
            offsetPosition = offsetPosition.withY(offsetPosition.y() + (double) secondExit.getY());
        }

        return this.getExactPos(offsetPosition);
    }

    @Nullable
    public Vec3 getPos(double x, double y, double z) {
        SectorVec3 position = this.getExactPos(SectorVec3.fromApproximate(x, y, z));
        return position == null ? null : position.toApproximateVec3();
    }

    /** Snaps a split-coordinate point to its rail centerline in exact X/Z space. */
    @Nullable
    public SectorVec3 getExactPos(SectorVec3 position) {
        var blockX = position.blockX();
        int blockY = Mth.floor(position.y());
        var blockZ = position.blockZ();
        if (this.level.getBlockState(new BlockPos(blockX, blockY - 1, blockZ)).is(BlockTags.RAILS)) {
            --blockY;
        }

        BlockState blockstate = this.level.getBlockState(new BlockPos(blockX, blockY, blockZ));
        if (!BaseRailBlock.isRail(blockstate)) {
            return null;
        }

        RailShape railshape = blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());
        Pair<Vec3i, Vec3i> pair = exits(railshape);
        Vec3i firstExit = pair.getFirst();
        Vec3i secondExit = pair.getSecond();
        double startX = 0.5D + firstExit.getXLo() * 0.5D;
        double startY = (double) blockY + 0.0625D + (double) firstExit.getY() * 0.5D;
        double startZ = 0.5D + firstExit.getZLo() * 0.5D;
        double endX = 0.5D + secondExit.getXLo() * 0.5D;
        double endY = (double) blockY + 0.0625D + (double) secondExit.getY() * 0.5D;
        double endZ = 0.5D + secondExit.getZLo() * 0.5D;
        double directionX = endX - startX;
        double directionY = (endY - startY) * 2.0D;
        double directionZ = endZ - startZ;
        double railProgress;
        if (directionX == 0.0D) {
            railProgress = position.subZ();
        } else if (directionZ == 0.0D) {
            railProgress = position.subX();
        } else {
            railProgress = ((position.subX() - startX) * directionX + (position.subZ() - startZ) * directionZ) * 2.0D;
        }

        double snappedX = startX + directionX * railProgress;
        double snappedY = startY + directionY * railProgress;
        double snappedZ = startZ + directionZ * railProgress;
        if (directionY < 0.0D) {
            ++snappedY;
        } else if (directionY > 0.0D) {
            snappedY += 0.5D;
        }

        return SectorVec3.fromBlockAndFraction(blockX, snappedX, snappedY, blockZ, snappedZ);
    }

    public AABB getBoundingBoxForCulling() {
        AABB aabb = this.getBoundingBox();
        return this.hasCustomDisplay() ? aabb.inflate((double) Math.abs(this.getDisplayOffset()) / 16.0D) : aabb;
    }

    /**
    * The display block can extend beyond the physical cart. Preserve the exact
    * X/Z metadata when the entity index performs a sector query.
    */
    @Override
    @Nullable
    public SectorAABB getSectorBoundingBoxForCulling() {
        SectorAABB box = super.getSectorBoundingBoxForCulling();
        return box == null || !this.hasCustomDisplay() ? box
                                                       : box.inflate((double) Math.abs(this.getDisplayOffset()) / 16.0D,
                                                                 (double) Math.abs(this.getDisplayOffset()) / 16.0D,
                                                                 (double) Math.abs(this.getDisplayOffset()) / 16.0D);
    }

    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.getBoolean("CustomDisplayTile")) {
            this.setDisplayBlockState(NbtUtils.readBlockState(pCompound.getCompound("DisplayState")));
            this.setDisplayOffset(pCompound.getInt("DisplayOffset"));
        }
    }

    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (this.hasCustomDisplay()) {
            pCompound.putBoolean("CustomDisplayTile", true);
            pCompound.put("DisplayState", NbtUtils.writeBlockState(this.getDisplayBlockState()));
            pCompound.putInt("DisplayOffset", this.getDisplayOffset());
        }
    }

    public void push(Entity pEntity) {
        if (!this.level.isClientSide) {
            if (!pEntity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(pEntity)) {
                    Vec3 collisionDelta = pEntity.sectorPosition().relativeTo(this.sectorPosition());
                    double d0 = collisionDelta.x;
                    double d1 = collisionDelta.z;
                    double d2 = d0 * d0 + d1 * d1;
                    if (d2 >= (double) 1.0E-4F) {
                        d2 = Math.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0D / d2;
                        if (d3 > 1.0D) {
                            d3 = 1.0D;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= (double) 0.1F;
                        d1 *= (double) 0.1F;
                        d0 *= 0.5D;
                        d1 *= 0.5D;
                        if (pEntity instanceof AbstractMinecart) {
                            Vec3 vec3 = (new Vec3(collisionDelta.x, 0.0D, collisionDelta.z)).normalize();
                            Vec3 vec31 = (new Vec3((double) Mth.cos(this.getYRot() * ((float) Math.PI / 180F)), 0.0D, (double) Mth.sin(this.getYRot() * ((float) Math.PI / 180F)))).normalize();
                            double d6 = Math.abs(vec3.dot(vec31));
                            if (d6 < (double) 0.8F) {
                                return;
                            }

                            Vec3 vec32 = this.getDeltaMovement();
                            Vec3 vec33 = pEntity.getDeltaMovement();
                            if (((AbstractMinecart) pEntity).getMinecartType() == AbstractMinecart.Type.FURNACE && this.getMinecartType() != AbstractMinecart.Type.FURNACE) {
                                this.setDeltaMovement(vec32.multiply(0.2D, 1.0D, 0.2D));
                                this.push(vec33.x - d0, 0.0D, vec33.z - d1);
                                pEntity.setDeltaMovement(vec33.multiply(0.95D, 1.0D, 0.95D));
                            } else if (((AbstractMinecart) pEntity).getMinecartType() != AbstractMinecart.Type.FURNACE && this.getMinecartType() == AbstractMinecart.Type.FURNACE) {
                                pEntity.setDeltaMovement(vec33.multiply(0.2D, 1.0D, 0.2D));
                                pEntity.push(vec32.x + d0, 0.0D, vec32.z + d1);
                                this.setDeltaMovement(vec32.multiply(0.95D, 1.0D, 0.95D));
                            } else {
                                double d7 = (vec33.x + vec32.x) / 2.0D;
                                double d8 = (vec33.z + vec32.z) / 2.0D;
                                this.setDeltaMovement(vec32.multiply(0.2D, 1.0D, 0.2D));
                                this.push(d7 - d0, 0.0D, d8 - d1);
                                pEntity.setDeltaMovement(vec33.multiply(0.2D, 1.0D, 0.2D));
                                pEntity.push(d7 + d0, 0.0D, d8 + d1);
                            }
                        } else {
                            this.push(-d0, 0.0D, -d1);
                            pEntity.push(d0 / 4.0D, 0.0D, d1 / 4.0D);
                        }
                    }
                }
            }
        }
    }

    public void lerpTo(double pX, double pY, double pZ, float pYaw, float pPitch, int pPosRotationIncrements, boolean pTeleport) {
        this.exactLerpTarget = null;
        this.lx = pX;
        this.ly = pY;
        this.lz = pZ;
        this.lyr = (double) pYaw;
        this.lxr = (double) pPitch;
        this.lSteps = pPosRotationIncrements + 2;
        this.setDeltaMovement(this.lxd, this.lyd, this.lzd);
    }

    @Override
    public void lerpTo(SectorVec3 position, float yRot, float xRot, int steps, boolean teleport) {
        this.exactLerpTarget = position;
        this.lyr = (double) yRot;
        this.lxr = (double) xRot;
        this.lSteps = steps + 2;
        this.setDeltaMovement(this.lxd, this.lyd, this.lzd);
    }

    public void lerpMotion(double pX, double pY, double pZ) {
        this.lxd = pX;
        this.lyd = pY;
        this.lzd = pZ;
        this.setDeltaMovement(this.lxd, this.lyd, this.lzd);
    }

    public void setDamage(float pDamage) {
        this.entityData.set(DATA_ID_DAMAGE, pDamage);
    }

    public float getDamage() {
        return this.entityData.get(DATA_ID_DAMAGE);
    }

    public void setHurtTime(int pHurtTime) {
        this.entityData.set(DATA_ID_HURT, pHurtTime);
    }

    public int getHurtTime() {
        return this.entityData.get(DATA_ID_HURT);
    }

    public void setHurtDir(int pHurtDirection) {
        this.entityData.set(DATA_ID_HURTDIR, pHurtDirection);
    }

    public int getHurtDir() {
        return this.entityData.get(DATA_ID_HURTDIR);
    }

    public abstract AbstractMinecart.Type getMinecartType();

    public BlockState getDisplayBlockState() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayBlockState() : Block.stateById(this.getEntityData().get(DATA_ID_DISPLAY_BLOCK));
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayOffset() : this.getEntityData().get(DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setDisplayBlockState(BlockState pDisplayState) {
        this.getEntityData().set(DATA_ID_DISPLAY_BLOCK, Block.getId(pDisplayState));
        this.setCustomDisplay(true);
    }

    public void setDisplayOffset(int pDisplayOffset) {
        this.getEntityData().set(DATA_ID_DISPLAY_OFFSET, pDisplayOffset);
        this.setCustomDisplay(true);
    }

    public boolean hasCustomDisplay() {
        return this.getEntityData().get(DATA_ID_CUSTOM_DISPLAY);
    }

    public void setCustomDisplay(boolean pCustomDisplay) {
        this.getEntityData().set(DATA_ID_CUSTOM_DISPLAY, pCustomDisplay);
    }

    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public ItemStack getPickResult() {
        Item item;
        switch (this.getMinecartType()) {
            case FURNACE:
                item = Items.FURNACE_MINECART;
                break;
            case CHEST:
                item = Items.CHEST_MINECART;
                break;
            case TNT:
                item = Items.TNT_MINECART;
                break;
            case HOPPER:
                item = Items.HOPPER_MINECART;
                break;
            case COMMAND_BLOCK:
                item = Items.COMMAND_BLOCK_MINECART;
                break;
            default:
                item = Items.MINECART;
        }

        return new ItemStack(item);
    }

    public static enum Type {
        RIDEABLE,
        CHEST,
        FURNACE,
        TNT,
        SPAWNER,
        HOPPER,
        COMMAND_BLOCK;
    }
}
