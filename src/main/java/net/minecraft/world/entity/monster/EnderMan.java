package net.minecraft.world.entity.monster;

import net.minecraft.world.phys.SectorVec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class EnderMan extends Monster implements NeutralMob {
    private static final UUID SPEED_MODIFIER_ATTACKING_UUID = UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(SPEED_MODIFIER_ATTACKING_UUID, "Attacking speed boost", (double) 0.15F, AttributeModifier.Operation.ADDITION);
    private static final int DELAY_BETWEEN_CREEPY_STARE_SOUND = 400;
    private static final int MIN_DEAGGRESSION_TIME = 600;
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRY_STATE = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DATA_CREEPY = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STARED_AT = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

    public EnderMan(EntityType<? extends EnderMan> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.maxUpStep = 1.0F;
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnderMan.EndermanFreezeWhenLookedAt(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new EnderMan.EndermanLeaveBlockGoal(this));
        this.goalSelector.addGoal(11, new EnderMan.EndermanTakeBlockGoal(this));
        this.targetSelector.addGoal(1, new EnderMan.EndermanLookForPlayerGoal(this, this::isAngryAt));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Endermite.class, true, false));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.ATTACK_DAMAGE, 7.0D).add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    public void setTarget(@Nullable LivingEntity pLivingEntity) {
        super.setTarget(pLivingEntity);
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (pLivingEntity == null) {
            this.targetChangeTime = 0;
            this.entityData.set(DATA_CREEPY, false);
            this.entityData.set(DATA_STARED_AT, false);
            attributeinstance.removeModifier(SPEED_MODIFIER_ATTACKING);
        } else {
            this.targetChangeTime = this.tickCount;
            this.entityData.set(DATA_CREEPY, true);
            if (!attributeinstance.hasModifier(SPEED_MODIFIER_ATTACKING)) {
                attributeinstance.addTransientModifier(SPEED_MODIFIER_ATTACKING);
            }
        }
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CARRY_STATE, Optional.empty());
        this.entityData.define(DATA_CREEPY, false);
        this.entityData.define(DATA_STARED_AT, false);
    }

    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    public void setRemainingPersistentAngerTime(int pTime) {
        this.remainingPersistentAngerTime = pTime;
    }

    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    public void setPersistentAngerTarget(@Nullable UUID pTarget) {
        this.persistentAngerTarget = pTarget;
    }

    @Nullable
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void playStareSound() {
        if (this.tickCount >= this.lastStareSound + 400) {
            this.lastStareSound = this.tickCount;
            if (!this.isSilent()) {
                this.level.playLocalSound(this.exactEyePosition(), SoundEvents.ENDERMAN_STARE, this.getSoundSource(), 2.5F, 1.0F, false);
            }
        }
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if (DATA_CREEPY.equals(pKey) && this.hasBeenStaredAt() && this.level.isClientSide) {
            this.playStareSound();
        }

        super.onSyncedDataUpdated(pKey);
    }

    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        BlockState blockstate = this.getCarriedBlock();
        if (blockstate != null) {
            pCompound.put("carriedBlockState", NbtUtils.writeBlockState(blockstate));
        }

        this.addPersistentAngerSaveData(pCompound);
    }

    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        BlockState blockstate = null;
        if (pCompound.contains("carriedBlockState", 10)) {
            blockstate = NbtUtils.readBlockState(pCompound.getCompound("carriedBlockState"));
            if (blockstate.isAir()) {
                blockstate = null;
            }
        }

        this.setCarriedBlock(blockstate);
        this.readPersistentAngerSaveData(this.level, pCompound);
    }

    boolean isLookingAtMe(Player pPlayer) {
        ItemStack itemstack = pPlayer.getInventory().armor.get(3);
        if (itemstack.is(Blocks.CARVED_PUMPKIN.asItem())) {
            return false;
        } else {
            Vec3 vec3 = pPlayer.getViewVector(1.0F).normalize();
            Vec3 vec31 = this.sectorPosition().relativeTo(pPlayer.sectorPosition()).add(0.0D, this.getEyeY() - pPlayer.getEyeY(), 0.0D);
            double d0 = vec31.length();
            vec31 = vec31.normalize();
            double d1 = vec3.dot(vec31);
            return d1 > 1.0D - 0.025D / d0 ? pPlayer.hasLineOfSight(this) : false;
        }
    }

    protected float getStandingEyeHeight(Pose pPose, EntityDimensions pSize) {
        return 2.55F;
    }

    public void aiStep() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level.addParticle(ParticleTypes.PORTAL, this.particlePosition((this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(), this.random.nextDouble() * (double) this.getBbHeight() - 0.25D, (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth()),
                        (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(),
                        (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        }

        this.jumping = false;
        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, true);
        }

        super.aiStep();
    }

    public boolean isSensitiveToWater() {
        return true;
    }

    protected void customServerAiStep() {
        if (this.level.isDay() && this.tickCount >= this.targetChangeTime + 600) {
            float f = this.getLightLevelDependentMagicValue();
            if (f > 0.5F && this.level.canSeeSky(this.blockPosition()) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                this.setTarget((LivingEntity) null);
                this.teleport();
            }
        }

        super.customServerAiStep();
    }

    protected boolean teleport() {
        if (!this.level.isClientSide() && this.isAlive()) {
            double d0 = (this.random.nextDouble() - 0.5D) * 64.0D;
            double d1 = (double) (this.random.nextInt(64) - 32);
            double d2 = (this.random.nextDouble() - 0.5D) * 64.0D;
            return this.teleportExact(this.sectorPosition().add(d0, d1, d2));
        } else {
            return false;
        }
    }

    boolean teleportTowards(Entity pTarget) {
        Vec3 away = this.sectorPosition().relativeTo(pTarget.sectorPosition()).add(0.0D, this.getY(0.5D) - this.getY() - (pTarget.getEyeY() - pTarget.getY()), 0.0D).normalize();
        // Build the teleport target in split-coordinate space; a global-double
        // target near 2^63 would snap the enderman onto the coarse double grid.
        SectorVec3 exact = this.sectorPosition()
                                   .add((this.random.nextDouble() - 0.5D) * 8.0D - away.x * 16.0D, 0.0D,
                                           (this.random.nextDouble() - 0.5D) * 8.0D - away.z * 16.0D)
                                   .withY(this.getY() + (double) (this.random.nextInt(16) - 8) - away.y * 16.0D);
        return this.teleportExact(exact);
    }

    private boolean teleportExact(SectorVec3 target) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(
                target.blockX(), Mth.floor(target.y()), target.blockZ());

        while (blockpos$mutableblockpos.getY() > this.level.getMinBuildHeight() && !this.level.getBlockState(blockpos$mutableblockpos).getMaterial().blocksMotion()) {
            blockpos$mutableblockpos.move(Direction.DOWN);
        }

        BlockState blockstate = this.level.getBlockState(blockpos$mutableblockpos);
        boolean flag = blockstate.getMaterial().blocksMotion();
        boolean flag1 = blockstate.getFluidState().is(FluidTags.WATER);
        if (flag && !flag1) {
            SectorVec3 before = this.sectorPosition();
            SectorVec3 landing = target.withY((double) blockpos$mutableblockpos.getY() + 1.0D);
            this.teleportTo(landing);
            if (this.hasExactNoCollision() && !this.sectorContainsAnyLiquid(this.getSectorBoundingBox())) {
                this.level.gameEvent(GameEvent.TELEPORT, before.toApproximateVec3(), GameEvent.Context.of(this));
                if (!this.isSilent()) {
                    this.level.playSound((Player) null, before, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }

                return true;
            }

            this.teleportTo(before);
            return false;
        } else {
            return false;
        }
    }

    protected SoundEvent getAmbientSound() {
        return this.isCreepy() ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    protected void dropCustomDeathLoot(DamageSource pSource, int pLooting, boolean pRecentlyHit) {
        super.dropCustomDeathLoot(pSource, pLooting, pRecentlyHit);
        BlockState blockstate = this.getCarriedBlock();
        if (blockstate != null) {
            this.spawnAtLocation(blockstate.getBlock());
        }
    }

    public void setCarriedBlock(@Nullable BlockState pState) {
        this.entityData.set(DATA_CARRY_STATE, Optional.ofNullable(pState));
    }

    @Nullable
    public BlockState getCarriedBlock() {
        return this.entityData.get(DATA_CARRY_STATE).orElse((BlockState) null);
    }

    public boolean hurt(DamageSource pSource, float pAmount) {
        if (this.isInvulnerableTo(pSource)) {
            return false;
        } else if (pSource instanceof IndirectEntityDamageSource) {
            Entity entity = pSource.getDirectEntity();
            boolean flag1;
            if (entity instanceof ThrownPotion) {
                flag1 = this.hurtWithCleanWater(pSource, (ThrownPotion) entity, pAmount);
            } else {
                flag1 = false;
            }

            for (int i = 0; i < 64; ++i) {
                if (this.teleport()) {
                    return true;
                }
            }

            return flag1;
        } else {
            boolean flag = super.hurt(pSource, pAmount);
            if (!this.level.isClientSide() && !(pSource.getEntity() instanceof LivingEntity) && this.random.nextInt(10) != 0) {
                this.teleport();
            }

            return flag;
        }
    }

    private boolean hurtWithCleanWater(DamageSource pSource, ThrownPotion pPotion, float pAmount) {
        ItemStack itemstack = pPotion.getItem();
        Potion potion = PotionUtils.getPotion(itemstack);
        List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);
        boolean flag = potion == Potions.WATER && list.isEmpty();
        return flag ? super.hurt(pSource, pAmount) : false;
    }

    public boolean isCreepy() {
        return this.entityData.get(DATA_CREEPY);
    }

    public boolean hasBeenStaredAt() {
        return this.entityData.get(DATA_STARED_AT);
    }

    public void setBeingStaredAt() {
        this.entityData.set(DATA_STARED_AT, true);
    }

    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCarriedBlock() != null;
    }

    static class EndermanFreezeWhenLookedAt extends Goal {
        private final EnderMan enderman;
        @Nullable
        private LivingEntity target;

        public EndermanFreezeWhenLookedAt(EnderMan pEnderman) {
            this.enderman = pEnderman;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        public boolean canUse() {
            this.target = this.enderman.getTarget();
            if (!(this.target instanceof Player)) {
                return false;
            } else {
                double d0 = this.target.distanceToSqr(this.enderman);
                return d0 > 256.0D ? false : this.enderman.isLookingAtMe((Player) this.target);
            }
        }

        public void start() {
            this.enderman.getNavigation().stop();
        }

        public void tick() {
            this.enderman.getLookControl().setLookAt(this.target);
        }
    }

    static class EndermanLeaveBlockGoal extends Goal {
        private final EnderMan enderman;

        public EndermanLeaveBlockGoal(EnderMan pEnderman) {
            this.enderman = pEnderman;
        }

        public boolean canUse() {
            if (this.enderman.getCarriedBlock() == null) {
                return false;
            } else if (!this.enderman.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return false;
            } else {
                return this.enderman.getRandom().nextInt(reducedTickDelay(2000)) == 0;
            }
        }

        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level level = this.enderman.level;
            BlockPos blockpos = this.enderman.sectorPosition().add(-1.0D + randomsource.nextDouble() * 2.0D,
                                                                      randomsource.nextDouble() * 2.0D, -1.0D + randomsource.nextDouble() * 2.0D)
                                        .blockPosition();
            BlockState blockstate = level.getBlockState(blockpos);
            BlockPos blockpos1 = blockpos.below();
            BlockState blockstate1 = level.getBlockState(blockpos1);
            BlockState blockstate2 = this.enderman.getCarriedBlock();
            if (blockstate2 != null) {
                blockstate2 = Block.updateFromNeighbourShapes(blockstate2, this.enderman.level, blockpos);
                if (this.canPlaceBlock(level, blockpos, blockstate2, blockstate, blockstate1, blockpos1)) {
                    level.setBlock(blockpos, blockstate2, 3);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(this.enderman, blockstate2));
                    this.enderman.setCarriedBlock((BlockState) null);
                }
            }
        }

        private boolean canPlaceBlock(Level pLevel, BlockPos pDestinationPos, BlockState pCarriedState, BlockState pDestinationState, BlockState pBelowDestinationState, BlockPos pBelowDestinationPos) {
            SectorVec3 center = SectorVec3.fromBlockAndFraction(pDestinationPos.getX(), 0.5D, (double) pDestinationPos.getY(), pDestinationPos.getZ(), 0.5D);
            return pDestinationState.isAir() && !pBelowDestinationState.isAir() && !pBelowDestinationState.is(Blocks.BEDROCK) && pBelowDestinationState.isCollisionShapeFullBlock(pLevel, pBelowDestinationPos) && pCarriedState.canSurvive(pLevel, pDestinationPos) && pLevel.getEntities(this.enderman, AABB.fromSectorBounds(net.minecraft.world.phys.SectorAABB.around(center, 1.0D, 1.0D))).isEmpty();
        }
    }

    static class EndermanLookForPlayerGoal extends NearestAttackableTargetGoal<Player> {
        private final EnderMan enderman;
        @Nullable
        private Player pendingTarget;
        private int aggroTime;
        private int teleportTime;
        private final TargetingConditions startAggroTargetConditions;
        private final TargetingConditions continueAggroTargetConditions = TargetingConditions.forCombat().ignoreLineOfSight();

        public EndermanLookForPlayerGoal(EnderMan pEnderman, @Nullable Predicate<LivingEntity> pSelectionPredicate) {
            super(pEnderman, Player.class, 10, false, false, pSelectionPredicate);
            this.enderman = pEnderman;
            this.startAggroTargetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector((p_32578_) -> {
                return pEnderman.isLookingAtMe((Player) p_32578_);
            });
        }

        public boolean canUse() {
            this.pendingTarget = this.enderman.level.getNearestPlayer(this.startAggroTargetConditions, this.enderman);
            return this.pendingTarget != null;
        }

        public void start() {
            this.aggroTime = this.adjustedTickDelay(5);
            this.teleportTime = 0;
            this.enderman.setBeingStaredAt();
        }

        public void stop() {
            this.pendingTarget = null;
            super.stop();
        }

        public boolean canContinueToUse() {
            if (this.pendingTarget != null) {
                if (!this.enderman.isLookingAtMe(this.pendingTarget)) {
                    return false;
                } else {
                    this.enderman.lookAt(this.pendingTarget, 10.0F, 10.0F);
                    return true;
                }
            } else {
                return this.target != null && this.continueAggroTargetConditions.test(this.enderman, this.target) ? true : super.canContinueToUse();
            }
        }

        public void tick() {
            if (this.enderman.getTarget() == null) {
                super.setTarget((LivingEntity) null);
            }

            if (this.pendingTarget != null) {
                if (--this.aggroTime <= 0) {
                    this.target = this.pendingTarget;
                    this.pendingTarget = null;
                    super.start();
                }
            } else {
                if (this.target != null && !this.enderman.isPassenger()) {
                    if (this.enderman.isLookingAtMe((Player) this.target)) {
                        if (this.target.distanceToSqr(this.enderman) < 16.0D) {
                            this.enderman.teleport();
                        }

                        this.teleportTime = 0;
                    } else if (this.target.distanceToSqr(this.enderman) > 256.0D && this.teleportTime++ >= this.adjustedTickDelay(30) && this.enderman.teleportTowards(this.target)) {
                        this.teleportTime = 0;
                    }
                }

                super.tick();
            }
        }
    }

    static class EndermanTakeBlockGoal extends Goal {
        private final EnderMan enderman;

        public EndermanTakeBlockGoal(EnderMan pEnderman) {
            this.enderman = pEnderman;
        }

        public boolean canUse() {
            if (this.enderman.getCarriedBlock() != null) {
                return false;
            } else if (!this.enderman.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return false;
            } else {
                return this.enderman.getRandom().nextInt(reducedTickDelay(20)) == 0;
            }
        }

        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level level = this.enderman.level;
            BlockPos blockpos = this.enderman.sectorPosition().add(-2.0D + randomsource.nextDouble() * 4.0D,
                                                                      randomsource.nextDouble() * 3.0D, -2.0D + randomsource.nextDouble() * 4.0D)
                                        .blockPosition();
            BlockState blockstate = level.getBlockState(blockpos);
            SectorVec3 vec3 = SectorVec3.fromBlockAndFraction(this.enderman.getBlockX(), 0.5D, (double) blockpos.getY() + 0.5D, this.enderman.getBlockZ(), 0.5D);
            SectorVec3 vec31 = SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, (double) blockpos.getY() + 0.5D, blockpos.getZ(), 0.5D);
            BlockHitResult blockhitresult = net.minecraft.world.level.SectorClipper.clip(level, vec3, vec31, this.enderman, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE);
            boolean flag = blockhitresult.getBlockPos().equals(blockpos);
            if (blockstate.is(BlockTags.ENDERMAN_HOLDABLE) && flag) {
                level.removeBlock(blockpos, false);
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockpos, GameEvent.Context.of(this.enderman, blockstate));
                this.enderman.setCarriedBlock(blockstate.getBlock().defaultBlockState());
            }
        }
    }
}
