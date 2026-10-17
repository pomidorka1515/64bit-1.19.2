package net.minecraft.world.level.portal;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.SectorVec3;

public class PortalInfo {
    public final Vec3 pos;
    public final SectorVec3 exactPos;
    public final Vec3 speed;
    public final float yRot;
    public final float xRot;

    public PortalInfo(Vec3 pPos, Vec3 pSpeed, float pYRot, float pXRot) {
        this(SectorVec3.fromApproximate(pPos), pSpeed, pYRot, pXRot);
    }

    public PortalInfo(SectorVec3 pPos, Vec3 pSpeed, float pYRot, float pXRot) {
        this.exactPos = pPos;
        this.pos = pPos.toApproximateVec3();
        this.speed = pSpeed;
        this.yRot = pYRot;
        this.xRot = pXRot;
    }
}
