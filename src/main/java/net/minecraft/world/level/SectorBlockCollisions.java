package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
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
   private final boolean invalidRange;
   private boolean invalidRangeReported;
   /*
    * Collision sweeps usually touch only a few chunks, but a boundary sweep
    * can revisit them thousands of times.  Cache misses as well as hits; a
    * null entry means "known unloaded", not "look it up again".
    */
   private final Map<ChunkPos, BlockGetter> chunkCache = new HashMap<>();

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
      // Cursor3D stores its linear index in an int.  It is also unable to
      // represent a range whose width/depth is Long.MAX_VALUE.  A normal
      // player box is small, but hostile movement near an edge can make a
      // saturated endpoint appear to span the whole world.  Reject that
      // range before Cursor3D can overflow or spend minutes walking it.
      this.invalidRange = !WorldBounds.isValidBlock(minX, minZ) || !WorldBounds.isValidBlock(maxX, maxZ)
            || !isSafeCursorRange(minX, maxX, minY, maxY, minZ, maxZ);
      // A malformed or excessively large sweep must not be handed to
      // Cursor3D: its int index would wrap and the server could loop forever.
      this.cursor = this.invalidRange
            ? new Cursor3D(0L, 0, 0L, -1L, -1, -1L)
            : new Cursor3D(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private static boolean isSafeCursorRange(long minX, long maxX, int minY, int maxY,
                                            long minZ, long maxZ) {
      // Cursor3D uses an int linear index even though its end is a long. If
      // the volume exceeds Integer.MAX_VALUE, the index wraps and advance()
      // never reaches end. Reject saturated/hostile ranges before creating
      // such a cursor. A 64-block horizontal bound is far above any normal
      // entity sweep and also keeps the long differences exact.
      if (maxX < minX || maxZ < minZ || maxY < minY
            || WorldBounds.distance(minX, maxX) > 64.0D
            || WorldBounds.distance(minZ, maxZ) > 64.0D) {
         return false;
      }
      long width = (long)WorldBounds.distance(minX, maxX) + 1L;
      long height = (long)WorldBounds.distance((long)minY, (long)maxY) + 1L;
      long depth = (long)WorldBounds.distance(minZ, maxZ) + 1L;
      return width <= Integer.MAX_VALUE / Math.max(1L, height)
            && width * height <= Integer.MAX_VALUE / Math.max(1L, depth);
   }

   @Nullable
   private BlockGetter getChunk(long blockX, long blockZ) {
      long chunkX = SectionPos.blockToSectionCoord(blockX);
      long chunkZ = SectionPos.blockToSectionCoord(blockZ);
      ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
      if (this.chunkCache.containsKey(chunkPos)) {
         return this.chunkCache.get(chunkPos);
      }

      if (!WorldBounds.isValidChunk(chunkX, chunkZ)) {
         this.chunkCache.put(chunkPos, null);
         return null;
      }

      BlockGetter getter;
      if (this.collisionGetter instanceof ServerLevel serverLevel) {
         // This method is intentionally non-blocking.  In particular, never
         // call Level.getChunk(..., false) here: its old server implementation
         // can wait for generation while the tick thread is processing input.
         getter = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
      } else {
         getter = this.collisionGetter.getChunkForCollisions(chunkX, chunkZ);
      }
      this.chunkCache.put(chunkPos, getter);
      return getter;
   }

   @Override
   protected VoxelShape computeNext() {
      if (this.invalidRange) {
         // A sweep that cannot be represented by Cursor3D is conservative:
         // report an overlap in the current local frame once.  Returning no
         // shape would make the movement resolver accept the entire unsafe
         // delta and teleport across the world.
         if (!this.invalidRangeReported) {
            this.invalidRangeReported = true;
            return Shapes.block().move(this.localBox.minX, this.localBox.minY, this.localBox.minZ);
         }
         return this.endOfData();
      }

      while (this.cursor.advance()) {
         long blockX = this.cursor.nextX();
         int blockY = this.cursor.nextY();
         long blockZ = this.cursor.nextZ();
         int type = this.cursor.getNextType();
         if (!WorldBounds.isValidBlock(blockX, blockZ)) continue;
         if (type == Cursor3D.TYPE_CORNER) {
            continue;
         }

         BlockGetter getter = this.getChunk(blockX, blockZ);
         if (getter == null) {
            // A non-blocking server lookup can observe a chunk while it is
            // still loading.  Treat it as solid for movement collision.  The
            // old behavior treated it as air, which made the player sink
            // through the edge of an unloaded chunk and then triggered the
            // anticheat teleport.  This is conservative and, importantly,
            // still never waits for chunk generation.
            if (this.onlySuffocatingBlocks) {
               continue;
            }
            double localX = WorldBounds.signedDifference(blockX, this.origin.originBlockX());
            double localY = (double)blockY - (double)this.origin.originBlockY();
            double localZ = WorldBounds.signedDifference(blockZ, this.origin.originBlockZ());
            VoxelShape localShape = Shapes.block().move(localX, localY, localZ);
            if (this.localBox.intersects(localX, localY, localZ,
                  localX + 1.0D, localY + 1.0D, localZ + 1.0D)
                  && Shapes.joinIsNotEmpty(localShape, this.localEntityShape, BooleanOp.AND)) {
               return localShape;
            }
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
         double localX = WorldBounds.signedDifference(blockX, this.origin.originBlockX());
         double localY = (double)blockY - (double)this.origin.originBlockY();
         double localZ = WorldBounds.signedDifference(blockZ, this.origin.originBlockZ());
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
