package net.minecraft.world.entity.decoration;

import net.minecraft.world.phys.SectorAABB;

import net.minecraft.world.phys.SectorVec3;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class LeashFenceKnotEntity extends HangingEntity {
    public static final double OFFSET_Y = 0.375D;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public LeashFenceKnotEntity(Level pLevel, BlockPos pPos) {
        super(EntityType.LEASH_KNOT, pLevel, pPos);
        this.applyExactPosition(net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(pPos.getX(), 0.0D, (double) pPos.getY(), pPos.getZ(), 0.0D));
    }

    protected void recalculateBoundingBox() {
        this.applyExactPosition(SectorVec3.fromBlockAndFraction(
                this.pos.getX(), 0.5D, (double) this.pos.getY() + 0.375D, this.pos.getZ(), 0.5D));
        double d0 = (double) this.getType().getWidth() / 2.0D;
        double d1 = (double) this.getType().getHeight();
        this.setBoundingBox(AABB.fromSectorBounds(net.minecraft.world.phys.SectorAABB.around(this.sectorPosition(), d0 * 2.0D, d1)));
    }

    public void setDirection(Direction pFacingDirection) {
    }

    public int getWidth() {
        return 9;
    }

    public int getHeight() {
        return 9;
    }

    protected float getEyeHeight(Pose pPose, EntityDimensions pSize) {
        return 0.0625F;
    }

    public boolean shouldRenderAtSqrDistance(double pDistance) {
        return pDistance < 1024.0D;
    }

    public void dropItem(@Nullable Entity pBrokenEntity) {
        this.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
    }

    public void addAdditionalSaveData(CompoundTag pCompound) {
    }

    public void readAdditionalSaveData(CompoundTag pCompound) {
    }

    public InteractionResult interact(Player pPlayer, InteractionHand pHand) {
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            boolean flag = false;
            double d0 = 7.0D;
            // Vanilla box spans [getY-7, getY+7] in Y and 14 blocks in X/Z; build
            // the same span in split coordinates so the entity index query stays exact.
            SectorAABB exact = SectorAABB.around(
                                                 this.sectorPosition(), 14.0D, 14.0D)
                                       .move(0.0D, -7.0D, 0.0D);
            List<Mob> list = this.level.getEntitiesOfClass(Mob.class, AABB.fromSectorBounds(exact));

            for (Mob mob : list) {
                if (mob.getLeashHolder() == pPlayer) {
                    mob.setLeashedTo(this, true);
                    flag = true;
                }
            }

            if (!flag) {
                this.discard();
                if (pPlayer.getAbilities().instabuild) {
                    for (Mob mob1 : list) {
                        if (mob1.isLeashed() && mob1.getLeashHolder() == this) {
                            mob1.dropLeash(true, false);
                        }
                    }
                }
            }

            return InteractionResult.CONSUME;
        }
    }

    public boolean survives() {
        return this.level.getBlockState(this.pos).is(BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level pLevel, BlockPos pPos) {
        long i = pPos.getX();
        int j = pPos.getY();
        long k = pPos.getZ();

        for (LeashFenceKnotEntity leashfenceknotentity : pLevel.getEntitiesOfClass(LeashFenceKnotEntity.class, AABB.fromSectorBounds(net.minecraft.world.phys.SectorAABB.around(net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(i, 0.0D, j - 1.0D, k, 0.0D), 2.0D, 2.0D)))) {
            if (leashfenceknotentity.getPos().equals(pPos)) {
                return leashfenceknotentity;
            }
        }

        LeashFenceKnotEntity leashfenceknotentity1 = new LeashFenceKnotEntity(pLevel, pPos);
        pLevel.addFreshEntity(leashfenceknotentity1);
        return leashfenceknotentity1;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
    }

    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    public Vec3 getRopeHoldPosition(float pPartialTicks) {
        return this.getPosition(pPartialTicks).add(0.0D, 0.2D, 0.0D);
    }

    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
