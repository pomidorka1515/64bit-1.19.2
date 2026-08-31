package net.minecraft.client.renderer.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityRendererTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   private static final BlockEntityRenderer<BlockEntity> RENDERER = new BlockEntityRenderer<>() {
      @Override
      public void render(BlockEntity blockEntity, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                         net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
      }
   };

   @Test
   void usableDistancePreservesNearbyChestBeyondDoublePrecision() {
      long huge = 1L << 53;
      BlockPos chest = new BlockPos(huge, 64, -huge);
      SectorVec3 nearbyPlayer = SectorVec3.fromBlockAndFraction(huge + 7L, 0.5D, 64.5D, -huge, 0.5D);
      SectorVec3 distantPlayer = SectorVec3.fromBlockAndFraction(huge + 8L, 0.75D, 64.5D, -huge, 0.5D);

      assertTrue(BlockEntity.isWithinUsableDistance(nearbyPlayer, chest, 8.0D));
      assertFalse(BlockEntity.isWithinUsableDistance(distantPlayer, chest, 8.0D));
   }

   @Test
   void defaultRangeCheckPreservesNearbyBlockEntityFractionBeyondDoublePrecision() {
      long huge = 1L << 53;
      BlockEntity blockEntity = new BlockEntity(BlockEntityType.CHEST,
            new BlockPos(huge, 64, -huge), Blocks.CHEST.defaultBlockState()) {
      };
      // At 2^53, a legacy Vec3 rounds this camera's X coordinate to huge + 64,
      // incorrectly putting the block center on the exclusive 64-block boundary.
      SectorVec3 nearbyCamera = SectorVec3.fromBlockAndFraction(huge + 63L, 0.75D, 64.5D, -huge, 0.25D);
      SectorVec3 distantCamera = SectorVec3.fromBlockAndFraction(huge + 64L, 0.75D, 64.5D, -huge, 0.25D);

      assertTrue(RENDERER.shouldRender(blockEntity, nearbyCamera));
      assertFalse(RENDERER.shouldRender(blockEntity, distantCamera));
   }
}
