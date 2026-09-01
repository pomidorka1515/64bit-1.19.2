package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.MinecartModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinecartRenderer<T extends AbstractMinecart> extends EntityRenderer<T> {
   private static final ResourceLocation MINECART_LOCATION = new ResourceLocation("textures/entity/minecart.png");
   protected final EntityModel<T> model;
   private final BlockRenderDispatcher blockRenderer;

   public MinecartRenderer(EntityRendererProvider.Context p_174300_, ModelLayerLocation p_174301_) {
      super(p_174300_);
      this.shadowRadius = 0.7F;
      this.model = new MinecartModel<>(p_174300_.bakeLayer(p_174301_));
      this.blockRenderer = p_174300_.getBlockRenderDispatcher();
   }

   public void render(T p_115418_, float p_115419_, float p_115420_, PoseStack p_115421_, MultiBufferSource p_115422_, int p_115423_) {
      super.render(p_115418_, p_115419_, p_115420_, p_115421_, p_115422_, p_115423_);
      p_115421_.pushPose();
      long i = (long)p_115418_.getId() * 493286711L;
      i = i * i * 4392167121L + i * 98761L;
      float f = (((float)(i >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
      float f1 = (((float)(i >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
      float f2 = (((float)(i >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
      p_115421_.translate((double)f, (double)f1, (double)f2);
      SectorVec3 cartPosition = p_115418_.interpolatedExactPosition(p_115420_);
      double d1 = Mth.lerp((double)p_115420_, p_115418_.yOld, p_115418_.getY());
      if (cartPosition == null) {
         cartPosition = SectorVec3.fromApproximate(Mth.lerp((double)p_115420_, p_115418_.xOld, p_115418_.getX()),
               d1, Mth.lerp((double)p_115420_, p_115418_.zOld, p_115418_.getZ()));
      }

      SectorVec3 railPosition = p_115418_.getExactPos(cartPosition);
      float f3 = Mth.lerp(p_115420_, p_115418_.xRotO, p_115418_.getXRot());
      if (railPosition != null) {
         SectorVec3 forward = p_115418_.getExactPosOffs(cartPosition, (double)0.3F);
         SectorVec3 backward = p_115418_.getExactPosOffs(cartPosition, (double)-0.3F);
         if (forward == null) {
            forward = railPosition;
         }

         if (backward == null) {
            backward = railPosition;
         }

         Vec3 railOffset = railPosition.relativeTo(cartPosition);
         p_115421_.translate(railOffset.x, (forward.y() + backward.y()) / 2.0D - cartPosition.y(), railOffset.z);
         Vec3 railDirection = backward.relativeTo(forward);
         if (railDirection.length() != 0.0D) {
            railDirection = railDirection.normalize();
            p_115419_ = (float)(Math.atan2(railDirection.z, railDirection.x) * 180.0D / Math.PI);
            f3 = (float)(Math.atan(railDirection.y) * 73.0D);
         }
      }

      p_115421_.translate(0.0D, 0.375D, 0.0D);
      p_115421_.mulPose(Vector3f.YP.rotationDegrees(180.0F - p_115419_));
      p_115421_.mulPose(Vector3f.ZP.rotationDegrees(-f3));
      float f5 = (float)p_115418_.getHurtTime() - p_115420_;
      float f6 = p_115418_.getDamage() - p_115420_;
      if (f6 < 0.0F) {
         f6 = 0.0F;
      }

      if (f5 > 0.0F) {
         p_115421_.mulPose(Vector3f.XP.rotationDegrees(Mth.sin(f5) * f5 * f6 / 10.0F * (float)p_115418_.getHurtDir()));
      }

      int j = p_115418_.getDisplayOffset();
      BlockState blockstate = p_115418_.getDisplayBlockState();
      if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
         p_115421_.pushPose();
         float f4 = 0.75F;
         p_115421_.scale(0.75F, 0.75F, 0.75F);
         p_115421_.translate(-0.5D, (double)((float)(j - 8) / 16.0F), 0.5D);
         p_115421_.mulPose(Vector3f.YP.rotationDegrees(90.0F));
         this.renderMinecartContents(p_115418_, p_115420_, blockstate, p_115421_, p_115422_, p_115423_);
         p_115421_.popPose();
      }

      p_115421_.scale(-1.0F, -1.0F, 1.0F);
      this.model.setupAnim(p_115418_, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      VertexConsumer vertexconsumer = p_115422_.getBuffer(this.model.renderType(this.getTextureLocation(p_115418_)));
      this.model.renderToBuffer(p_115421_, vertexconsumer, p_115423_, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      p_115421_.popPose();
   }

   public ResourceLocation getTextureLocation(T p_115416_) {
      return MINECART_LOCATION;
   }

   protected void renderMinecartContents(T p_115424_, float p_115425_, BlockState p_115426_, PoseStack p_115427_, MultiBufferSource p_115428_, int p_115429_) {
      this.blockRenderer.renderSingleBlock(p_115426_, p_115427_, p_115428_, p_115429_, OverlayTexture.NO_OVERLAY);
   }
}