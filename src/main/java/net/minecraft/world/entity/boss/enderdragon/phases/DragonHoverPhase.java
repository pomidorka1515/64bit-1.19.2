package net.minecraft.world.entity.boss.enderdragon.phases;

import javax.annotation.Nullable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

public class DragonHoverPhase extends AbstractDragonPhaseInstance {
    @Nullable
    private net.minecraft.world.phys.SectorVec3 targetLocation;

    public DragonHoverPhase(EnderDragon pDragon) {
        super(pDragon);
    }

    public void doServerTick() {
        if (this.targetLocation == null) {
            this.targetLocation = this.dragon.sectorPosition();
        }
    }

    public boolean isSitting() {
        return true;
    }

    public void begin() {
        this.targetLocation = null;
    }

    public float getFlySpeed() {
        return 1.0F;
    }

    @Nullable
    public Vec3 getFlyTargetLocation() {
        return this.targetLocation == null ? null : this.targetLocation.toExactVec3();
    }

    public net.minecraft.world.phys.SectorVec3 getExactFlyTargetLocation() {
        return this.targetLocation;
    }

    public EnderDragonPhase<DragonHoverPhase> getPhase() {
        return EnderDragonPhase.HOVERING;
    }
}
