package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectorBlockCollisionsTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void findsHugeBlockAndReturnsLocalShape() {
      long hugeX = 1L << 53;
      long hugeZ = -(1L << 53);
      BlockPos solidPos = new BlockPos(hugeX, 64, hugeZ);
      CollisionGetter level = new SingleBlockCollisionGetter(solidPos);
      SectorPhysicsOrigin origin = new SectorPhysicsOrigin(hugeX, 64, hugeZ);
      SectorVec3 player = SectorVec3.fromBlockAndFraction(hugeX, 0.25D, 64.0D, hugeZ, 0.25D);
      SectorAABB exactBox = SectorAABB.around(player, 0.6D, 1.8D);
      AABB localBox = exactBox.toLocalAABB(origin);

      List<VoxelShape> shapes = new java.util.ArrayList<>();
      for (VoxelShape shape : level.getSectorBlockCollisions(null, exactBox, localBox, origin)) {
         shapes.add(shape);
      }

      assertFalse(shapes.isEmpty());
      assertEquals(0.0D, shapes.get(0).bounds().minX, 0.0D);
      assertEquals(1.0D, shapes.get(0).bounds().maxX, 0.0D);
      assertEquals(0.0D, shapes.get(0).bounds().minZ, 0.0D);
      assertEquals(1.0D, shapes.get(0).bounds().maxZ, 0.0D);
   }

   @Test
   void rejectsAWorldSpanningSweepWithoutIterating() {
      SectorAABB box = new SectorAABB(Long.MIN_VALUE, 0.25D, 64.0D, 0L, 0.25D,
            Long.MAX_VALUE, Math.nextDown(1.0D), 65.8D, 0L, 0.75D);
      SectorPhysicsOrigin origin = new SectorPhysicsOrigin(0L, 64, 0L);
      CollisionGetter level = new SingleBlockCollisionGetter(new BlockPos(0L, 64, 0L));
      java.util.Iterator<VoxelShape> iterator = level.getSectorBlockCollisions(null, box,
            box.toLocalAABB(origin), origin).iterator();

      assertTrue(iterator.hasNext());
      iterator.next();
      assertFalse(iterator.hasNext());
   }

   private static final class SingleBlockCollisionGetter implements CollisionGetter {
      private final BlockPos solid;

      private SingleBlockCollisionGetter(BlockPos solid) {
         this.solid = solid;
      }

      @Override
      public WorldBorder getWorldBorder() {
         return new WorldBorder();
      }

      @Override
      @Nullable
      public BlockGetter getChunkForCollisions(long chunkX, long chunkZ) {
         return this;
      }

      @Override
      public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
         return Collections.emptyList();
      }

      @Override
      @Nullable
      public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
         return null;
      }

      @Override
      public BlockState getBlockState(BlockPos pos) {
         return this.solid.equals(pos) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
      }

      @Override
      public FluidState getFluidState(BlockPos pos) {
         return Fluids.EMPTY.defaultFluidState();
      }

      @Override
      public int getHeight() {
         return 384;
      }

      @Override
      public int getMinBuildHeight() {
         return -64;
      }
   }
}
