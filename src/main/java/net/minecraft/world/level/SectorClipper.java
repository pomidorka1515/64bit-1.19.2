package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Exact X/Z block ray traversal for sector-aware entities.
 *
 * <p>The DDA walks long block addresses while all ray/shape arithmetic happens
 * in a small frame anchored at the ray origin.  In particular, no absolute
 * world X/Z double is ever created while selecting a block.</p>
 */
public final class SectorClipper {
   private SectorClipper() {}

   public static BlockHitResult clip(BlockGetter level, SectorVec3 from, SectorVec3 to, Entity entity,
                                     ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
      SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(from);
      Vec3 localFrom = from.toLocal(origin.originBlockX(), origin.originBlockY(), origin.originBlockZ());
      Vec3 localTo = to.toLocal(origin.originBlockX(), origin.originBlockY(), origin.originBlockZ());
      ClipContext context = new ClipContext(localFrom, localTo, blockMode, fluidMode, entity);
      if (localFrom.equals(localTo)) {
         return BlockHitResult.missExact(to, Direction.getNearest(
               localFrom.x - localTo.x, localFrom.y - localTo.y, localFrom.z - localTo.z), to.blockPosition());
      }

      // BlockGetter's DDA divides by each ray component.  A level ray is often
      // exactly horizontal or vertical, so a zero component can turn the first
      // boundary distance into 0 * infinity (or make the DDA keep selecting a
      // zero-length axis).  Use a tiny signed component for traversal only; the
      // ClipContext still contains the unmodified ray, so hit locations remain
      // on the real ray and do not acquire a visible wobble.
      Vec3 ddaTo = safeDdaEnd(localFrom, localTo);
      return BlockGetter.traverseBlocks(localFrom, ddaTo, context,
            (ignored, localPos) -> clipBlock(level, context, origin, localPos),
            ignored -> BlockHitResult.missExact(to, Direction.getNearest(
                  localFrom.x - localTo.x, localFrom.y - localTo.y, localFrom.z - localTo.z), to.blockPosition()));
   }

   private static Vec3 safeDdaEnd(Vec3 from, Vec3 to) {
      return new Vec3(from.x + safeDirection(to.x - from.x),
            from.y + safeDirection(to.y - from.y), from.z + safeDirection(to.z - from.z));
   }

   private static double safeDirection(double direction) {
      if (Math.abs(direction) >= 1.0E-9D) return direction;
      return direction < 0.0D ? -1.0E-9D : 1.0E-9D;
   }

   @Nullable
   private static BlockHitResult clipBlock(BlockGetter level, ClipContext context,
                                           SectorPhysicsOrigin origin, BlockPos localPos) {
      long blockX = Math.addExact(origin.originBlockX(), localPos.getX());
      long blockZ = Math.addExact(origin.originBlockZ(), localPos.getZ());
      int blockY = Math.addExact(origin.originBlockY(), localPos.getY());
      BlockPos worldPos = new BlockPos(blockX, blockY, blockZ);
      BlockState state = level.getBlockState(worldPos);
      FluidState fluid = level.getFluidState(worldPos);
      double localX = (double)localPos.getX();
      double localY = (double)localPos.getY();
      double localZ = (double)localPos.getZ();

      VoxelShape blockShape = context.getBlockShape(state, level, worldPos);
      BlockHitResult blockHit = clipShape(context.getFrom(), context.getTo(),
            blockShape.move(localX, localY, localZ));
      if (blockHit != null) {
         BlockHitResult interactionHit = clipShape(context.getFrom(), context.getTo(),
               state.getInteractionShape(level, worldPos).move(localX, localY, localZ));
         if (interactionHit != null
               && interactionHit.getLocation().subtract(context.getFrom()).lengthSqr()
                  < blockHit.getLocation().subtract(context.getFrom()).lengthSqr()) {
            blockHit = blockHit.withDirection(interactionHit.getDirection());
         }
      }

      BlockHitResult fluidHit = clipShape(context.getFrom(), context.getTo(),
            context.getFluidShape(fluid, level, worldPos).move(localX, localY, localZ));
      BlockHitResult result;
      if (blockHit == null) {
         result = fluidHit;
      } else if (fluidHit == null || context.getFrom().distanceToSqr(blockHit.getLocation())
            <= context.getFrom().distanceToSqr(fluidHit.getLocation())) {
         result = blockHit;
      } else {
         result = fluidHit;
      }
      if (result == null) return null;
      return new BlockHitResult(toExact(origin, result.getLocation()), result.getDirection(), worldPos, result.isInside());
   }

   @Nullable
   private static BlockHitResult clipShape(Vec3 from, Vec3 to, VoxelShape shape) {
      // The shape has already been translated into the origin-local frame.
      // Passing ZERO prevents VoxelShape.clip from applying a world address.
      return shape.clip(from, to, BlockPos.ZERO);
   }

   private static SectorVec3 toExact(SectorPhysicsOrigin origin, Vec3 local) {
      return SectorVec3.fromBlockAndFraction(origin.originBlockX(), local.x,
            (double)origin.originBlockY() + local.y, origin.originBlockZ(), local.z);
   }
}
