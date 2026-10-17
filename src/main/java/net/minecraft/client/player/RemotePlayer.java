package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RemotePlayer extends AbstractClientPlayer {
    public RemotePlayer(ClientLevel pClientLevel, GameProfile pGameProfile, @Nullable ProfilePublicKey pProfilePublicKey) {
        super(pClientLevel, pGameProfile, pProfilePublicKey);
        this.maxUpStep = 1.0F;
        this.noPhysics = true;
    }

    public boolean shouldRenderAtSqrDistance(double pDistance) {
        double d0 = this.getBoundingBox().getSize() * 10.0D;
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }

        d0 *= 64.0D * getViewScale();
        return pDistance < d0 * d0;
    }

    public boolean hurt(DamageSource pSource, float pAmount) {
        return true;
    }

    public void tick() {
        super.tick();
        this.calculateEntityAnimation(this, false);
    }

    public void aiStep() {
        if (this.lerpSteps > 0) {
            this.setYRot(this.getYRot() + (float) Mth.wrapDegrees(this.lerpYRot - (double) this.getYRot()) / (float) this.lerpSteps);
            this.setXRot(this.getXRot() + (float) (this.lerpXRot - (double) this.getXRot()) / (float) this.lerpSteps);
            if (this.exactLerpTarget != null) {
                net.minecraft.world.phys.Vec3 delta = this.exactLerpTarget.relativeTo(this.sectorPosition());
                this.applyExactPosition(this.sectorPosition().add(delta.x / (double) this.lerpSteps,
                        delta.y / (double) this.lerpSteps, delta.z / (double) this.lerpSteps));
            } else {
                double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
                double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
                double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
                this.setPos(d0, d1, d2);
            }
            --this.lerpSteps;
            if (this.lerpSteps == 0)
                this.exactLerpTarget = null;
            this.setRot(this.getYRot(), this.getXRot());
        }

        if (this.lerpHeadSteps > 0) {
            this.yHeadRot += (float) (Mth.wrapDegrees(this.lyHeadRot - (double) this.yHeadRot) / (double) this.lerpHeadSteps);
            --this.lerpHeadSteps;
        }

        this.oBob = this.bob;
        this.updateSwingTime();
        float f;
        if (this.onGround && !this.isDeadOrDying()) {
            f = (float) Math.min(0.1D, this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;
        this.level.getProfiler().push("push");
        this.pushEntities();
        this.level.getProfiler().pop();
    }

    protected void updatePlayerPose() {
    }

    public void sendSystemMessage(Component pComponent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addMessage(pComponent);
    }
}
