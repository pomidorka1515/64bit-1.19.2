package net.minecraft.world.entity.ai.control;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class LookControl implements Control {
    protected final Mob mob;
    protected float yMaxRotSpeed;
    protected float xMaxRotAngle;
    protected int lookAtCooldown;
    protected double wantedY;
    private SectorVec3 exactWantedPosition;

    public LookControl(Mob pMob) {
        this.mob = pMob;
    }

    public void setLookAt(Vec3 pLookVector) {
        this.setLookAt(pLookVector.x, pLookVector.y, pLookVector.z);
    }

    public void setLookAt(Entity pEntity) {
        this.setLookAtExact(pEntity.sectorPosition().withY(getWantedY(pEntity)),
                (float) this.mob.getHeadRotSpeed(), (float) this.mob.getMaxHeadXRot());
    }

    public void setLookAt(Entity pEntity, float pDeltaYaw, float pDeltaPitch) {
        this.setLookAtExact(pEntity.sectorPosition().withY(getWantedY(pEntity)), pDeltaYaw, pDeltaPitch);
    }

    public void setLookAt(double pX, double pY, double pZ) {
        this.setLookAt(pX, pY, pZ, (float) this.mob.getHeadRotSpeed(), (float) this.mob.getMaxHeadXRot());
    }

    public void setLookAt(double pX, double pY, double pZ, float pDeltaYaw, float pDeltaPitch) {
        this.exactWantedPosition = SectorVec3.fromApproximate(pX, pY, pZ);
        this.wantedY = pY;
        this.yMaxRotSpeed = pDeltaYaw;
        this.xMaxRotAngle = pDeltaPitch;
        this.lookAtCooldown = 2;
    }

    public void setLookAtExact(SectorVec3 position, float yMaxRotSpeed, float xMaxRotAngle) {
        this.exactWantedPosition = position;
        this.wantedY = position.y();
        this.yMaxRotSpeed = yMaxRotSpeed;
        this.xMaxRotAngle = xMaxRotAngle;
        this.lookAtCooldown = 2;
    }

    public void tick() {
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0F);
        }

        if (this.lookAtCooldown > 0) {
            --this.lookAtCooldown;
            this.getYRotD().ifPresent((p_181130_) -> {
                this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, p_181130_, this.yMaxRotSpeed);
            });
            this.getXRotD().ifPresent((p_181128_) -> {
                this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), p_181128_, this.xMaxRotAngle));
            });
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
        }

        this.clampHeadRotationToBody();
    }

    protected void clampHeadRotationToBody() {
        if (!this.mob.getNavigation().isDone()) {
            this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, (float) this.mob.getMaxHeadYRot());
        }
    }

    protected boolean resetXRotOnTick() {
        return true;
    }

    public boolean isLookingAtTarget() {
        return this.lookAtCooldown > 0;
    }

    public double getWantedY() {
        return this.wantedY;
    }

    public SectorVec3 getExactWantedPosition() {
        return this.exactWantedPosition != null ? this.exactWantedPosition : this.mob.sectorPosition();
    }

    protected final Vec3 wantedDelta(double originY) {
        return this.getExactWantedPosition().relativeTo(this.mob.sectorPosition().withY(originY));
    }

    protected Optional<Float> getXRotD() {
        Vec3 exactDelta = this.wantedDelta(this.mob.getEyeY());
        double d0 = exactDelta.x;
        double d1 = exactDelta.y;
        double d2 = exactDelta.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        return !(Math.abs(d1) > (double) 1.0E-5F) && !(Math.abs(d3) > (double) 1.0E-5F) ? Optional.empty() : Optional.of((float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI))));
    }

    protected Optional<Float> getYRotD() {
        Vec3 exactDelta = this.wantedDelta(this.mob.getY());
        double d0 = exactDelta.x;
        double d1 = exactDelta.z;
        return !(Math.abs(d1) > (double) 1.0E-5F) && !(Math.abs(d0) > (double) 1.0E-5F) ? Optional.empty() : Optional.of((float) (Mth.atan2(d1, d0) * (double) (180F / (float) Math.PI)) - 90.0F);
    }

    protected float rotateTowards(float pFrom, float pTo, float pMaxDelta) {
        float f = Mth.degreesDifference(pFrom, pTo);
        float f1 = Mth.clamp(f, -pMaxDelta, pMaxDelta);
        return pFrom + f1;
    }

    private static double getWantedY(Entity pEntity) {
        return pEntity instanceof LivingEntity ? pEntity.getEyeY() : (pEntity.exactBoundingBox().minY + pEntity.exactBoundingBox().maxY) / 2.0D;
    }
}
