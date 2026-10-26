package net.minecraft.world.entity.vehicle;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MinecartHopper extends AbstractMinecartContainer implements Hopper {
    public static final int MOVE_ITEM_SPEED = 4;
    private boolean enabled = true;
    private int cooldownTime = -1;
    private final BlockPos lastPosition = BlockPos.ZERO;

    public MinecartHopper(EntityType<? extends MinecartHopper> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public MinecartHopper(Level pLevel, double pX, double pY, double pZ) {
        super(EntityType.HOPPER_MINECART, pX, pY, pZ, pLevel);
    }

    public AbstractMinecart.Type getMinecartType() {
        return AbstractMinecart.Type.HOPPER;
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.HOPPER.defaultBlockState();
    }

    public int getDefaultDisplayOffset() {
        return 1;
    }

    public int getContainerSize() {
        return 5;
    }

    public void activateMinecart(BlockPos position, boolean powered) {
        boolean flag = !powered;
        if (flag != this.isEnabled()) {
            this.setEnabled(flag);
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean pEnabled) {
        this.enabled = pEnabled;
    }

    public net.minecraft.world.phys.SectorVec3 getExactHopperPosition() {
        return this.sectorPosition().add(0.0D, 0.5D, 0.0D);
    }

    public double getLevelY() {
        return this.getY() + 0.5D;
    }

    public void tick() {
        super.tick();
        if (!this.level.isClientSide && this.isAlive() && this.isEnabled()) {
            BlockPos blockpos = this.blockPosition();
            if (blockpos.equals(this.lastPosition)) {
                --this.cooldownTime;
            } else {
                this.setCooldown(0);
            }

            if (!this.isOnCooldown()) {
                this.setCooldown(0);
                if (this.suckInItems()) {
                    this.setCooldown(4);
                    this.setChanged();
                }
            }
        }
    }

    public boolean suckInItems() {
        if (HopperBlockEntity.suckInItems(this.level, this)) {
            return true;
        } else {
            List<ItemEntity> list = this.level.getEntitiesOfClass(ItemEntity.class, this.exactBoundingBox().inflate(0.25D, 0.0D, 0.25D), EntitySelector.ENTITY_STILL_ALIVE);
            if (!list.isEmpty()) {
                HopperBlockEntity.addItem(this, list.get(0));
            }

            return false;
        }
    }

    protected Item getDropItem() {
        return Items.HOPPER_MINECART;
    }

    protected void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        pCompound.putInt("TransferCooldown", this.cooldownTime);
        pCompound.putBoolean("Enabled", this.enabled);
    }

    protected void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.cooldownTime = pCompound.getInt("TransferCooldown");
        this.enabled = pCompound.contains("Enabled") ? pCompound.getBoolean("Enabled") : true;
    }

    public void setCooldown(int pCooldownTime) {
        this.cooldownTime = pCooldownTime;
    }

    public boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    public AbstractContainerMenu createMenu(int pId, Inventory pPlayerInventory) {
        return new HopperMenu(pId, pPlayerInventory, this);
    }
}
