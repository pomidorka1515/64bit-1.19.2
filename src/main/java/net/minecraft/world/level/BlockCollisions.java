package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Compatibility iterator for callers expecting global-double collision shapes.
 *
 * <p>The global frame is safe only within {@link WorldBounds#MAX_SAFE_LOCAL_SPAN}
 * of zero. More distant queries must use the sector collision API with an exact
 * local origin; this bridge rejects them rather than rounding world addresses.</p>
 */
public class BlockCollisions extends AbstractIterator<VoxelShape> {
    private final SectorBlockCollisions collisions;

    public BlockCollisions(CollisionGetter pCollisionGetter, @Nullable Entity pEntity, AABB pBox) {
        this(pCollisionGetter, pEntity, pBox, false);
    }

    public BlockCollisions(CollisionGetter pCollisionGetter, @Nullable Entity pEntity, AABB pBox, boolean pOnlySuffocatingBlocks) {
        if (!Double.isFinite(pBox.minX) || !Double.isFinite(pBox.minY) || !Double.isFinite(pBox.minZ)
                || !Double.isFinite(pBox.maxX) || !Double.isFinite(pBox.maxY) || !Double.isFinite(pBox.maxZ)) {
            throw new IllegalArgumentException("Collision bounds must be finite");
        }
        SectorAABB exactBox = pBox.getSectorBounds();
        if (exactBox == null) {
            SectorVec3 min = SectorVec3.fromApproximate(pBox.minX, pBox.minY, pBox.minZ);
            SectorVec3 max = SectorVec3.fromApproximate(pBox.maxX, pBox.maxY, pBox.maxZ);
            exactBox = new SectorAABB(min.blockX(), min.subX(), min.y(), min.blockZ(), min.subZ(),
                    max.blockX(), max.subX(), max.y(), max.blockZ(), max.subZ());
        }
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 0, 0L);
        // Reject unsafe global frames before making any approximate shape.
        AABB localBox = exactBox.toLocalAABB(origin);
        this.collisions = new SectorBlockCollisions(pCollisionGetter, pEntity, exactBox, localBox,
                origin, pOnlySuffocatingBlocks);
    }

    @Override
    protected VoxelShape computeNext() {
        return this.collisions.hasNext() ? this.collisions.next() : this.endOfData();
    }
}
