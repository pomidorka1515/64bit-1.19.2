package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block collision iterator for a sector-physics operation.
 *
 * <p>The cursor and block lookup use exact world block coordinates.  The
 * returned shapes, and the entity shape used for filtering, are in the small
 * local frame described by {@code origin}; no world coordinate is converted
 * to a double during shape translation.</p>
 */
public final class SectorBlockCollisions extends AbstractIterator<VoxelShape> {
   private final AABB localBox;
   private final CollisionContext context;
   private final Cursor3D cursor;
   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
   private final VoxelShape localEntityShape;
   private final CollisionGetter collisionGetter;
   private final SectorPhysicsOrigin origin;
   private final boolean onlySuffocatingBlocks;
   @Nullable
   private BlockGetter cachedBlockGetter;
   @Nullable
   private ChunkPos cachedBlockGetterPos;

   public SectorBlockCollisions(CollisionGetter collisionGetter, @Nullable Entity entity,
                                SectorAABB exactBox, AABB localBox,
                                SectorPhysicsOrigin origin) {
      this(collisionGetter, entity, exactBox, localBox, origin, false);
   }

   public SectorBlockCollisions(CollisionGetter collisionGetter, @Nullable Entity entity,
                                SectorAABB exactBox, AABB localBox,
                                SectorPhysicsOrigin origin, boolean onlySuffocatingBlocks) {
      this.collisionGetter = collisionGetter;
      this.origin = origin;
      this.localBox = localBox;
      this.localEntityShape = Shapes.create(localBox);
      this.context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
      this.onlySuffocatingBlocks = onlySuffocatingBlocks;

      long minX = exactBox.minBlockXForCollision();
      long maxX = exactBox.maxBlockXForCollision();
      int minY = exactBox.minBlockYForCollision();
      int maxY = exactBox.maxBlockYForCollision();
      long minZ = exactBox.minBlockZForCollision();
      long maxZ = exactBox.maxBlockZForCollision();
      // SectorAABB has already checked arithmetic at the endpoint boundaries.
      // Cursor3D is deliberately given long X/Z values, never double bounds.
      this.cursor = new Cursor3D(minX, minY, minZ, maxX, maxY, maxZ);
   }

   @Nullable
   private BlockGetter getChunk(long blockX, long blockZ) {
      long chunkX = SectionPos.blockToSectionCoord(blockX);
      long chunkZ = SectionPos.blockToSectionCoord(blockZ);
      ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
      if (this.cachedBlockGetter != null && chunkPos.equals(this.cachedBlockGetterPos)) {
         return this.cachedBlockGetter;
      }

      BlockGetter getter = this.collisionGetter.getChunkForCollisions(chunkX, chunkZ);
      this.cachedBlockGetter = getter;
      this.cachedBlockGetterPos = chunkPos;
      return getter;
   }

   @Override
   protected VoxelShape computeNext() {
      while (this.cursor.advance()) {
         long blockX = this.cursor.nextX();
         int blockY = this.cursor.nextY();
         long blockZ = this.cursor.nextZ();
         int type = this.cursor.getNextType();
         if (type == Cursor3D.TYPE_CORNER) {
            continue;
         }

         BlockGetter getter = this.getChunk(blockX, blockZ);
         if (getter == null) {
            continue;
         }

         this.pos.set(blockX, blockY, blockZ);
         BlockState state = getter.getBlockState(this.pos);
         if (this.onlySuffocatingBlocks && !state.isSuffocating(getter, this.pos)
               || type == Cursor3D.TYPE_FACE && !state.hasLargeCollisionShape()
               || type == Cursor3D.TYPE_EDGE && !state.is(Blocks.MOVING_PISTON)) {
            continue;
         }

         VoxelShape shape = state.getCollisionShape(this.collisionGetter, this.pos, this.context);
         // Exact integer subtraction happens before conversion to local doubles.
         double localX = (double)Math.subtractExact(blockX, this.origin.originBlockX());
         double localY = (double)blockY - (double)this.origin.originBlockY();
         double localZ = (double)Math.subtractExact(blockZ, this.origin.originBlockZ());
         VoxelShape localShape = shape.move(localX, localY, localZ);

         if (shape == Shapes.block()
               && !this.localBox.intersects(localX, localY, localZ,
                     localX + 1.0D, localY + 1.0D, localZ + 1.0D)) {
            continue;
         }
         if (!Shapes.joinIsNotEmpty(localShape, this.localEntityShape, BooleanOp.AND)) {
            continue;
         }
         return localShape;
      }

      return this.endOfData();
   }
}
