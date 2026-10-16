package net.minecraft.world.level;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionGetter extends BlockGetter {
    WorldBorder getWorldBorder();

    @Nullable
    BlockGetter getChunkForCollisions(long pChunkX, long pChunkZ);

    default boolean isUnobstructed(@Nullable Entity pEntity, VoxelShape pShape) {
        return true;
    }

    default boolean isUnobstructed(BlockState pState, BlockPos pPos, CollisionContext pContext) {
        VoxelShape voxelshape = pState.getCollisionShape(this, pPos, pContext);
        if (voxelshape.isEmpty()) {
            return true;
        }

        Entity entity = pContext instanceof EntityCollisionContext entitycollisioncontext ? entitycollisioncontext.getEntity() : null;
        if (entity != null && entity.hasSectorPosition()) {
            if (entity.isColliding(pPos, pState)) {
                return false;
            }

            return this.isUnobstructed(entity, voxelshape.move((double) pPos.getX(), (double) pPos.getY(), (double) pPos.getZ()));
        }

        return this.isUnobstructed((Entity) null, voxelshape.move((double) pPos.getX(), (double) pPos.getY(), (double) pPos.getZ()));
    }

    default boolean isUnobstructed(Entity pEntity) {
        if (pEntity.hasSectorPosition() && this instanceof EntityGetter getter) {
            SectorAABB box = pEntity.getSectorBoundingBox();
            SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(pEntity.sectorPosition());
            return getter.getSectorEntities(pEntity, box, box.toLocalAABB(origin), origin,
                    candidate -> !candidate.isRemoved() && candidate.blocksBuilding
                            && !candidate.isPassengerOfSameVehicle(pEntity)).isEmpty();
        }
        return this.isUnobstructed(pEntity, Shapes.create(pEntity.getBoundingBox()));
    }

    default boolean noCollision(AABB pCollisionBox) {
        return this.noCollision((Entity) null, pCollisionBox);
    }

    default boolean noCollision(Entity pEntity) {
        return this.noCollision(pEntity, pEntity.getBoundingBox());
    }

    default boolean noCollision(@Nullable Entity pEntity, AABB pCollisionBox) {
        SectorAABB exactBox = pCollisionBox.getSectorBounds();
        if (exactBox != null) {
            SectorPhysicsOrigin origin = new SectorPhysicsOrigin(exactBox.minBlockX(), exactBox.minBlockYForRange(), exactBox.minBlockZ());
            for (VoxelShape shape : this.getSectorCollisions(pEntity, exactBox, exactBox.toLocalAABB(origin), origin)) {
                if (!shape.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        for (VoxelShape voxelshape : this.getBlockCollisions(pEntity, pCollisionBox)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        if (!this.getEntityCollisions(pEntity, pCollisionBox).isEmpty()) {
            return false;
        } else if (pEntity == null) {
            return true;
        } else {
            VoxelShape voxelshape1 = this.borderCollision(pEntity, pCollisionBox);
            return voxelshape1 == null || !Shapes.joinIsNotEmpty(voxelshape1, Shapes.create(pCollisionBox), BooleanOp.AND);
        }
    }

    List<VoxelShape> getEntityCollisions(@Nullable Entity pEntity, AABB pCollisionBox);

    default Iterable<VoxelShape> getCollisions(@Nullable Entity pEntity, AABB pCollisionBox) {
        List<VoxelShape> list = this.getEntityCollisions(pEntity, pCollisionBox);
        Iterable<VoxelShape> iterable = this.getBlockCollisions(pEntity, pCollisionBox);
        return list.isEmpty() ? iterable : Iterables.concat(list, iterable);
    }

    default Iterable<VoxelShape> getBlockCollisions(@Nullable Entity pEntity, AABB pCollisionBox) {
        return () -> {
            return new BlockCollisions(this, pEntity, pCollisionBox);
        };
    }

    /** Returns block shapes in a local frame for exact sector-physics queries. */
    default Iterable<VoxelShape> getSectorBlockCollisions(@Nullable Entity entity, SectorAABB exactBox,
            AABB localBox, SectorPhysicsOrigin origin) {
        return () -> new SectorBlockCollisions(this, entity, exactBox, localBox, origin);
    }

    /**
    * Exact equivalent of {@link #getCollisions(Entity, AABB)}.
    *
    * <p>Only world implementations also exposing {@link EntityGetter} can
    * contribute entity shapes. Read-only collision views still return their
    * exact block shapes, matching the information available to the legacy
    * method.</p>
    */
    default Iterable<VoxelShape> getSectorCollisions(@Nullable Entity entity, SectorAABB exactBox,
            AABB localBox, SectorPhysicsOrigin origin) {
        Iterable<VoxelShape> blockCollisions = this.getSectorBlockCollisions(entity, exactBox, localBox, origin);
        if (!(this instanceof EntityGetter entityGetter)) {
            return blockCollisions;
        }

        List<VoxelShape> entityCollisions = entityGetter.getSectorEntityCollisions(entity, exactBox, localBox, origin);
        return entityCollisions.isEmpty() ? blockCollisions : Iterables.concat(entityCollisions, blockCollisions);
    }

    @Nullable
    private VoxelShape borderCollision(Entity pEntity, AABB pBox) {
        //      WorldBorder worldborder = this.getWorldBorder();
        //      return worldborder.isInsideCloseToBorder(p_186441_, p_186442_) ? worldborder.getCollisionShape() : null;
        return null;
    }

    default boolean collidesWithSuffocatingBlock(@Nullable Entity pEntity, AABB pBox) {
        BlockCollisions blockcollisions = new BlockCollisions(this, pEntity, pBox, true);

        while (blockcollisions.hasNext()) {
            if (!blockcollisions.next().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    default Optional<Vec3> findFreePosition(@Nullable Entity pEntity, VoxelShape pShape, Vec3 pPos, double pX, double pY, double pZ) {
        if (pShape.isEmpty()) {
            return Optional.empty();
        } else {
            AABB aabb = pShape.bounds().inflate(pX, pY, pZ);
            VoxelShape voxelshape = StreamSupport.stream(this.getBlockCollisions(pEntity, aabb).spliterator(), false).filter((p_186430_) -> {
                                                                                                                           return this.getWorldBorder() == null || this.getWorldBorder().isWithinBounds(p_186430_.bounds());
                                                                                                                       })
                                            .flatMap((p_186426_) -> {
                                                return p_186426_.toAabbs().stream();
                                            })
                                            .map((p_186424_) -> {
                                                return p_186424_.inflate(pX / 2.0D, pY / 2.0D, pZ / 2.0D);
                                            })
                                            .map(Shapes::create)
                                            .reduce(Shapes.empty(), Shapes::or);
            VoxelShape voxelshape1 = Shapes.join(pShape, voxelshape, BooleanOp.ONLY_FIRST);
            return voxelshape1.closestPointTo(pPos);
        }
    }
}
