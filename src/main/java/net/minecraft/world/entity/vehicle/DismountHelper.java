package net.minecraft.world.entity.vehicle;

import net.minecraft.world.phys.SectorPhysicsOrigin;

import net.minecraft.world.phys.SectorAABB;

import net.minecraft.world.phys.SectorVec3;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DismountHelper {
    public static int[][] offsetsForDirection(Direction pDirection) {
        Direction direction = pDirection.getClockWise();
        Direction direction1 = direction.getOpposite();
        Direction direction2 = pDirection.getOpposite();
        return new int[][] {{(int) direction.getStepX(), (int) direction.getStepZ()}, {(int) direction1.getStepX(), (int) direction1.getStepZ()}, {(int) (direction2.getStepX() + direction.getStepX()), (int) (direction2.getStepZ() + direction.getStepZ())}, {(int) (direction2.getStepX() + direction1.getStepX()), (int) (direction2.getStepZ() + direction1.getStepZ())}, {(int) (pDirection.getStepX() + direction.getStepX()), (int) (pDirection.getStepZ() + direction.getStepZ())}, {(int) (pDirection.getStepX() + direction1.getStepX()), (int) (pDirection.getStepZ() + direction1.getStepZ())}, {(int) direction2.getStepX(), (int) direction2.getStepZ()}, {(int) pDirection.getStepX(), (int) pDirection.getStepZ()}};
    }

    public static boolean isBlockFloorValid(double pDistance) {
        return !Double.isInfinite(pDistance) && pDistance < 1.0D;
    }

    public static boolean canDismountTo(CollisionGetter pLevel, LivingEntity pPassenger, AABB pBoundingBox) {
        for (VoxelShape voxelshape : pLevel.getBlockCollisions(pPassenger, pBoundingBox)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        return pLevel.getWorldBorder().isWithinBounds(pBoundingBox);
    }

    public static boolean canDismountTo(CollisionGetter pLevel, Vec3 pOffset, LivingEntity pPassenger, Pose pPose) {
        return canDismountTo(pLevel, pPassenger, pPassenger.getLocalBoundsForPose(pPose).move(pOffset));
    }

    /**
    * Exact split-coordinate variant.  The passenger's pose box stays a small
    * local box; the collision query is centered on {@code origin}, so no
    * global-double translation can quantize the check near 2^63.
    */
    public static boolean canExactDismountTo(CollisionGetter level, LivingEntity passenger,
            SectorVec3 candidate, Pose pose) {
        SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(candidate);
        AABB localBox = passenger.getLocalBoundsForPose(pose);
        AABB worldBox = localBox.move(net.minecraft.world.level.WorldBounds.signedDifference(candidate.blockX(), origin.originBlockX()),
                candidate.y() - (double) origin.originBlockY(), net.minecraft.world.level.WorldBounds.signedDifference(candidate.blockZ(), origin.originBlockZ()));
        for (VoxelShape voxelshape : level.getSectorBlockCollisions(passenger,
                     SectorAABB.around(candidate, localBox.getXsize(), localBox.getYsize()),
                     worldBox, origin)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static VoxelShape nonClimbableShape(BlockGetter pLevel, BlockPos pPos) {
        BlockState blockstate = pLevel.getBlockState(pPos);
        return !blockstate.is(BlockTags.CLIMBABLE) && (!(blockstate.getBlock() instanceof TrapDoorBlock) || !blockstate.getValue(TrapDoorBlock.OPEN)) ? blockstate.getCollisionShape(pLevel, pPos) : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pPos, int pCeiling, Function<BlockPos, VoxelShape> pShapeForPos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pPos.mutable();
        int i = 0;

        while (i < pCeiling) {
            VoxelShape voxelshape = pShapeForPos.apply(blockpos$mutableblockpos);
            if (!voxelshape.isEmpty()) {
                return (double) (pPos.getY() + i) + voxelshape.min(Direction.Axis.Y);
            }

            ++i;
            blockpos$mutableblockpos.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    @Nullable
    public static Vec3 findSafeDismountLocation(EntityType<?> pEntityType, CollisionGetter pLevel, BlockPos pPos, boolean pOnlySafePositions) {
        if (pOnlySafePositions && pEntityType.isBlockDangerous(pLevel.getBlockState(pPos))) {
            return null;
        } else {
            double d0 = pLevel.getBlockFloorHeight(nonClimbableShape(pLevel, pPos), () -> {
                return nonClimbableShape(pLevel, pPos.below());
            });
            if (!isBlockFloorValid(d0)) {
                return null;
            } else if (pOnlySafePositions && d0 <= 0.0D && pEntityType.isBlockDangerous(pLevel.getBlockState(pPos.below()))) {
                return null;
            } else {
                Vec3 vec3 = Vec3.upFromBottomCenterOf(pPos, d0);
                AABB aabb = pEntityType.getDimensions().makeBoundingBox(vec3);

                for (VoxelShape voxelshape : pLevel.getBlockCollisions((Entity) null, aabb)) {
                    if (!voxelshape.isEmpty()) {
                        return null;
                    }
                }

                return !pLevel.getWorldBorder().isWithinBounds(aabb) ? null : vec3;
            }
        }
    }

    /** Exact split-coordinate variant of {@link #findSafeDismountLocation}; never reconstructs a global X/Z double. */
    @Nullable
    public static SectorVec3 findExactSafeDismountLocation(EntityType<?> type, CollisionGetter level, BlockPos pos) {
        double floorHeight = level.getBlockFloorHeight(nonClimbableShape(level, pos), () -> {
            return nonClimbableShape(level, pos.below());
        });
        if (!isBlockFloorValid(floorHeight)) {
            return null;
        }
        SectorVec3 candidate = SectorVec3.fromBlockAndFraction(
                pos.getX(), 0.5D, (double) pos.getY() + floorHeight, pos.getZ(), 0.5D);
        SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(candidate);
        EntityDimensions dimensions = type.getDimensions();
        AABB localBox = new AABB(-dimensions.width / 2.0D, 0.0D, -dimensions.width / 2.0D,
                dimensions.width / 2.0D, dimensions.height, dimensions.width / 2.0D);
        AABB worldBox = localBox.move(net.minecraft.world.level.WorldBounds.signedDifference(candidate.blockX(), origin.originBlockX()),
                candidate.y() - (double) origin.originBlockY(), net.minecraft.world.level.WorldBounds.signedDifference(candidate.blockZ(), origin.originBlockZ()));
        for (VoxelShape voxelshape : level.getSectorBlockCollisions(null,
                     SectorAABB.around(candidate, dimensions.width, dimensions.height),
                     worldBox, origin)) {
            if (!voxelshape.isEmpty()) {
                return null;
            }
        }

        return candidate;
    }
}
