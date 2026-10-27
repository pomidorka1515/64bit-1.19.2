package net.minecraft.world.entity.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class WalkTarget {
    private final PositionTracker target;
    private final float speedModifier;
    private final int closeEnoughDist;

    public WalkTarget(BlockPos pPos, float pSpeedModifier, int pCloseEnoughDist) {
        this(new BlockPosTracker(pPos), pSpeedModifier, pCloseEnoughDist);
    }

    public WalkTarget(Vec3 pVectorPos, float pSpeedModifier, int pCloseEnoughDist) {
        this(SectorVec3.fromApproximate(pVectorPos), pSpeedModifier, pCloseEnoughDist);
    }

    public WalkTarget(SectorVec3 position, float speedModifier, int closeEnoughDist) {
        this(new BlockPosTracker(position), speedModifier, closeEnoughDist);
    }

    public WalkTarget(Entity pTargetEntity, float pSpeedModifier, int pCloseEnoughDist) {
        this(new EntityTracker(pTargetEntity, false), pSpeedModifier, pCloseEnoughDist);
    }

    public WalkTarget(PositionTracker pTarget, float pSpeedModifier, int pCloseEnoughDist) {
        this.target = pTarget;
        this.speedModifier = pSpeedModifier;
        this.closeEnoughDist = pCloseEnoughDist;
    }

    public PositionTracker getTarget() {
        return this.target;
    }

    public float getSpeedModifier() {
        return this.speedModifier;
    }

    public int getCloseEnoughDist() {
        return this.closeEnoughDist;
    }
}
