package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class WaterBoundPathNavigation extends PathNavigation {
    private boolean allowBreaching;

    public WaterBoundPathNavigation(Mob pMob, Level pLevel) {
        super(pMob, pLevel);
    }

    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        this.allowBreaching = this.mob.getType() == EntityType.DOLPHIN;
        this.nodeEvaluator = new SwimNodeEvaluator(this.allowBreaching);
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    protected boolean canUpdatePath() {
        return this.allowBreaching || this.isInLiquid();
    }

    @Override
    protected net.minecraft.world.phys.SectorVec3 getTempMobSectorPos() {
        return this.mob.sectorPosition().add(0.0D, (double) this.mob.getBbHeight() * 0.5D, 0.0D);
    }

    protected double getGroundY(Vec3 pVec) {
        return pVec.y;
    }

    @Override
    protected double getGroundY(BlockPos blockPos, double targetY) {
        return targetY;
    }

    protected boolean canMoveDirectly(Vec3 pPosVec31, Vec3 pPosVec32) {
        return isClearForMovementBetween(this.mob, pPosVec31, pPosVec32);
    }

    @Override
    protected boolean canMoveDirectlyLocal(net.minecraft.world.phys.SectorVec3 from,
            net.minecraft.world.phys.SectorVec3 to) {
        net.minecraft.world.phys.SectorVec3 target = to.add(0.0D,
                (double) this.mob.getBbHeight() * 0.5D, 0.0D);
        return net.minecraft.world.level.SectorClipper.clip(this.level, from, target, this.mob,
                                                              net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                                              net.minecraft.world.level.ClipContext.Fluid.NONE)
                       .getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pPos) {
        return !this.level.getBlockState(pPos).isSolidRender(this.level, pPos);
    }

    public void setCanFloat(boolean pCanSwim) {
    }
}
