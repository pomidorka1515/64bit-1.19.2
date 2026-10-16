package net.minecraft.world.entity.ai.navigation;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

public class WallClimberNavigation extends GroundPathNavigation {
    @Nullable
    private BlockPos pathToPosition;

    public WallClimberNavigation(Mob pMob, Level pLevel) {
        super(pMob, pLevel);
    }

    public Path createPath(BlockPos pPos, int pAccuracy) {
        this.pathToPosition = pPos;
        return super.createPath(pPos, pAccuracy);
    }

    public Path createPath(Entity pEntity, int pAccuracy) {
        this.pathToPosition = pEntity.blockPosition();
        return super.createPath(pEntity, pAccuracy);
    }

    public boolean moveTo(Entity pEntity, double pSpeed) {
        Path path = this.createPath(pEntity, 0);
        if (path != null) {
            return this.moveTo(path, pSpeed);
        } else {
            this.pathToPosition = pEntity.blockPosition();
            this.speedModifier = pSpeed;
            return true;
        }
    }

    public void tick() {
        if (!this.isDone()) {
            super.tick();
        } else {
            if (this.pathToPosition != null) {
                net.minecraft.world.phys.SectorVec3 target = net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(
                        this.pathToPosition.getX(), 0.5D, (double) this.pathToPosition.getY(),
                        this.pathToPosition.getZ(), 0.5D);
                net.minecraft.world.phys.Vec3 delta = target.relativeTo(this.mob.sectorPosition());
                double width = (double) this.mob.getBbWidth();
                boolean close = delta.lengthSqr() < width * width;
                boolean closeHorizontallyAbove = this.mob.getY() > (double) this.pathToPosition.getY()
                        && delta.x * delta.x + delta.z * delta.z < width * width;
                if (!close && !closeHorizontallyAbove) {
                    this.mob.getMoveControl().setWantedPosition(target, this.speedModifier);
                } else {
                    this.pathToPosition = null;
                }
            }
        }
    }
}
