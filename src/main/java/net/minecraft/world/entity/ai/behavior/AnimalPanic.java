package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.SectorVec3;

public class AnimalPanic extends Behavior<PathfinderMob> {
    private static final int PANIC_MIN_DURATION = 100;
    private static final int PANIC_MAX_DURATION = 120;
    private static final int PANIC_DISTANCE_HORIZONTAL = 5;
    private static final int PANIC_DISTANCE_VERTICAL = 4;
    private final float speedMultiplier;

    public AnimalPanic(float pSpeedMultiplier) {
        super(ImmutableMap.of(MemoryModuleType.IS_PANICKING, MemoryStatus.REGISTERED, MemoryModuleType.HURT_BY, MemoryStatus.VALUE_PRESENT), 100, 120);
        this.speedMultiplier = pSpeedMultiplier;
    }

    protected boolean canStillUse(ServerLevel pLevel, PathfinderMob pEntity, long pGameTime) {
        return true;
    }

    protected void start(ServerLevel pLevel, PathfinderMob pEntity, long pGameTime) {
        pEntity.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        pEntity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    protected void stop(ServerLevel pLevel, PathfinderMob pEntity, long pGameTime) {
        Brain<?> brain = pEntity.getBrain();
        brain.eraseMemory(MemoryModuleType.IS_PANICKING);
    }

    protected void tick(ServerLevel pLevel, PathfinderMob pOwner, long pGameTime) {
        if (pOwner.getNavigation().isDone()) {
            SectorVec3 target = this.getPanicPos(pOwner, pLevel);
            if (target != null) {
                pOwner.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(target, this.speedMultiplier, 0));
            }
        }
    }

    @Nullable
    private SectorVec3 getPanicPos(PathfinderMob pPathfinder, ServerLevel pLevel) {
        if (pPathfinder.isOnFire()) {
            Optional<BlockPos> optional = this.lookForWater(pLevel, pPathfinder);
            if (optional.isPresent()) {
                BlockPos position = optional.get();
                return SectorVec3.fromBlockAndFraction(position.getX(), 0.5D, position.getY(),
                        position.getZ(), 0.5D);
            }
        }

        return LandRandomPos.getSectorPos(pPathfinder, 5, 4);
    }

    private Optional<BlockPos> lookForWater(BlockGetter pLevel, Entity pEntity) {
        BlockPos blockpos = pEntity.blockPosition();
        return !pLevel.getBlockState(blockpos).getCollisionShape(pLevel, blockpos).isEmpty() ? Optional.empty() : BlockPos.findClosestMatch(blockpos, 5, 1, (p_196646_) -> {
            return pLevel.getFluidState(p_196646_).is(FluidTags.WATER);
        });
    }
}
