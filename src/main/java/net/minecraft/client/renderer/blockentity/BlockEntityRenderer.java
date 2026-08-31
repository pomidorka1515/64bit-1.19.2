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
   void render(T p_112307_, float p_112308_, PoseStack p_112309_, MultiBufferSource p_112310_, int p_112311_, int p_112312_);

   default boolean shouldRenderOffScreen(T p_112306_) {
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
   default boolean shouldRender(T p_173568_, SectorVec3 p_173569_) {
      BlockPos blockpos = p_173568_.getBlockPos();
      return SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, (double)blockpos.getY() + 0.5D,
            blockpos.getZ(), 0.5D).relativeTo(p_173569_).lengthSqr() < (double)this.getViewDistance() * (double)this.getViewDistance();
   }
}