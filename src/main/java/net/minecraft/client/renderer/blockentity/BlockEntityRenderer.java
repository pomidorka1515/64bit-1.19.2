package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface BlockEntityRenderer<T extends BlockEntity> {
    void render(T pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBufferSource, int pPackedLight, int pPackedOverlay);

    default boolean shouldRenderOffScreen(T pBlockEntity) {
        return false;
    }

    default int getViewDistance() {
        return 64;
    }

    /**
    * Tests the block entity's block-center distance in the camera's exact
    * split-coordinate frame.  Block entities are addressed by {@link BlockPos},
    * so constructing their center from that position preserves X/Z precision.
    */
    default boolean shouldRender(T pBlockEntity, SectorVec3 pCameraPos) {
        BlockPos blockpos = pBlockEntity.getBlockPos();
        return SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, (double) blockpos.getY() + 0.5D,
                                 blockpos.getZ(), 0.5D)
                       .relativeTo(pCameraPos)
                       .lengthSqr()
                < (double) this.getViewDistance() * (double) this.getViewDistance();
    }
}
