package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetAwayFrom<T> extends Behavior<PathfinderMob> {
    private final MemoryModuleType<T> walkAwayFromMemory;
    private final float speedModifier;
    private final int desiredDistance;
    private final Function<T, SectorVec3> toPosition;

    public SetWalkTargetAwayFrom(MemoryModuleType<T> pWalkTargetAwayFromMemory, float pSpeedModifier, int pDesiredDistance, boolean pHasTarget, Function<T, SectorVec3> pToPosition) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, pHasTarget ? MemoryStatus.REGISTERED : MemoryStatus.VALUE_ABSENT, pWalkTargetAwayFromMemory, MemoryStatus.VALUE_PRESENT));
        this.walkAwayFromMemory = pWalkTargetAwayFromMemory;
        this.speedModifier = pSpeedModifier;
        this.desiredDistance = pDesiredDistance;
        this.toPosition = pToPosition;
    }

    public static SetWalkTargetAwayFrom<BlockPos> pos(MemoryModuleType<BlockPos> pWalkTargetAwayFromMemory, float pSpeedModifier, int pDesiredDistance, boolean pHasTarget) {
        return new SetWalkTargetAwayFrom<>(pWalkTargetAwayFromMemory, pSpeedModifier, pDesiredDistance, pHasTarget, position -> SectorVec3.fromBlockAndFraction(position.getX(), 0.5D, position.getY(), position.getZ(), 0.5D));
    }

    public static SetWalkTargetAwayFrom<? extends Entity> entity(MemoryModuleType<? extends Entity> pWalkTargetAwayFromMemory, float pSpeedModifier, int pDesiredDistance, boolean pHasTarget) {
        return new SetWalkTargetAwayFrom<>(pWalkTargetAwayFromMemory, pSpeedModifier, pDesiredDistance, pHasTarget, Entity::sectorPosition);
    }

    protected boolean checkExtraStartConditions(ServerLevel pLevel, PathfinderMob pOwner) {
        if (this.alreadyWalkingAwayFromPosWithSameSpeed(pOwner))
            return false;
        return this.getPosToAvoid(pOwner).relativeTo(pOwner.sectorPosition()).lengthSqr()
                < (double) (this.desiredDistance * this.desiredDistance);
    }

    private SectorVec3 getPosToAvoid(PathfinderMob pPathfinder) {
        return this.toPosition.apply(pPathfinder.getBrain().getMemory(this.walkAwayFromMemory).get());
    }

    private boolean alreadyWalkingAwayFromPosWithSameSpeed(PathfinderMob pPathfinder) {
        if (!pPathfinder.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        } else {
            WalkTarget walktarget = pPathfinder.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get();
            if (walktarget.getSpeedModifier() != this.speedModifier) {
                return false;
            } else {
                Vec3 walkDirection = walktarget.getTarget().currentExactPosition().relativeTo(pPathfinder.sectorPosition());
                Vec3 avoidDirection = this.getPosToAvoid(pPathfinder).relativeTo(pPathfinder.sectorPosition());
                return walkDirection.dot(avoidDirection) < 0.0D;
            }
        }
    }

    protected void start(ServerLevel pLevel, PathfinderMob pEntity, long pGameTime) {
        moveAwayFrom(pEntity, this.getPosToAvoid(pEntity), this.speedModifier);
    }

    private static void moveAwayFrom(PathfinderMob mob, SectorVec3 avoid, float speedModifier) {
        for (int i = 0; i < 10; ++i) {
            SectorVec3 target = LandRandomPos.getSectorPosAway(mob, 16, 7, avoid);
            if (target != null) {
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(target, speedModifier, 0));
                return;
            }
        }
    }
}
