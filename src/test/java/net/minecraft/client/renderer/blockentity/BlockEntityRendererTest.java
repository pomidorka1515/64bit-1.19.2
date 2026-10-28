package net.minecraft.client.renderer.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector4f;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
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

    @Test
    void wideMatrixKeepsBlockEntityRangeAndLocalPoseOffsetsFinite() {
        for (int bit : new int[] {127, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 base = Int1088.ONE.shiftLeftSaturated(bit).multiplySaturated(sign);
                BlockPos block = new BlockPos(base, 64, base.negate());
                BlockEntity blockEntity = new BlockEntity(BlockEntityType.CHEST, block, Blocks.CHEST.defaultBlockState()) {};
                SectorVec3 camera = SectorVec3.fromBlockAndFraction(base, 0.75D, 64.5D, base.negate(), 0.25D);
                assertTrue(RENDERER.shouldRender(blockEntity, camera));
                assertFalse(RENDERER.shouldRender(blockEntity, camera.add(65, 0, 0)));
                var local = CameraRelativePosition.of(camera);
                var pose = new PoseStack();
                pose.translate(local.relativeX(block.getX()), local.relativeY(block.getY()), local.relativeZ(block.getZ()));
                var vertex = new Vector4f(0.5F, 0.5F, 0.5F, 1);
                vertex.transform(pose.last().pose());
                assertEquals(-0.25F, vertex.x());
                assertEquals(0.0F, vertex.y());
                assertEquals(0.25F, vertex.z());
            }
        }
    }

    @Test
    void rangeChecksRetainHighHalvesAndCrossLowWordSeams() {
        Int1088 x = Int1088.of(-2L, 123L);
        Int1088 z = Int1088.of(Long.MAX_VALUE, -456L);
        BlockEntity blockEntity = new BlockEntity(BlockEntityType.CHEST,
                new BlockPos(x, 64, z), Blocks.CHEST.defaultBlockState()) {};
        assertTrue(RENDERER.shouldRender(blockEntity, SectorVec3.fromBlockAndFraction(
                x.add(Int1088.fromLong(63L)), 0.75D, 64.5D, z, 0.5D)));
        assertFalse(RENDERER.shouldRender(blockEntity, SectorVec3.fromBlockAndFraction(
                x.add(Int1088.fromLong(64L)), 0.75D, 64.5D, z, 0.5D)));
        assertFalse(RENDERER.shouldRender(blockEntity, SectorVec3.fromBlockAndFraction(
                x.addSaturated(Int1088.ONE.shiftLeftSaturated(1024)), 0.5D, 64.5D, z, 0.5D)));
    }
}
