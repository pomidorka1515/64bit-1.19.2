package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

public class FlyingPathNavigation extends PathNavigation {
    public FlyingPathNavigation(Mob pMob, Level pLevel) {
        super(pMob, pLevel);
    }

    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        this.nodeEvaluator = new FlyNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    protected boolean canUpdatePath() {
        return this.canFloat() && this.isInLiquid() || !this.mob.isPassenger();
    }

    @Override
    protected net.minecraft.world.phys.SectorVec3 getTempMobSectorPos() {
        return this.mob.sectorPosition();
    }

    public Path createPath(Entity pEntity, int pAccuracy) {
        return this.createPath(pEntity.blockPosition(), pAccuracy);
    }

    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                net.minecraft.world.phys.SectorVec3 next = this.path.getNextExactEntityPos(this.mob);
                if (this.mob.getBlockX().equals(next.blockX()) && this.mob.getBlockY() == Mth.floor(next.y())
                        && this.mob.getBlockZ().equals(next.blockZ())) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
            if (!this.isDone()) {
                this.mob.getMoveControl().setWantedPosition(this.path.getNextExactEntityPos(this.mob), this.speedModifier);
            }
        }
    }

    public void setCanOpenDoors(boolean pCanOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(pCanOpenDoors);
    }

    public boolean canPassDoors() {
        return this.nodeEvaluator.canPassDoors();
    }

    public void setCanPassDoors(boolean pCanEnterDoors) {
        this.nodeEvaluator.setCanPassDoors(pCanEnterDoors);
    }

    public boolean canOpenDoors() {
        return this.nodeEvaluator.canPassDoors();
    }

    public boolean isStableDestination(BlockPos pPos) {
        return this.level.getBlockState(pPos).entityCanStandOn(this.level, pPos, this.mob);
    }
}
