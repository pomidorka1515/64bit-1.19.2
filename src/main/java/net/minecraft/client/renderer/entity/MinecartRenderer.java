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

    public MinecartRenderer(EntityRendererProvider.Context pContext, ModelLayerLocation pLayer) {
        super(pContext);
        this.shadowRadius = 0.7F;
        this.model = new MinecartModel<>(pContext.bakeLayer(pLayer));
        this.blockRenderer = pContext.getBlockRenderDispatcher();
    }

    public void render(T pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight) {
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
        pMatrixStack.pushPose();
        long i = (long) pEntity.getId() * 493286711L;
        i = i * i * 4392167121L + i * 98761L;
        float f = (((float) (i >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float f1 = (((float) (i >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float f2 = (((float) (i >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        pMatrixStack.translate((double) f, (double) f1, (double) f2);
        SectorVec3 cartPosition = pEntity.interpolatedExactPosition(pPartialTicks);
        SectorVec3 railPosition = pEntity.getExactPos(cartPosition);
        float f3 = Mth.lerp(pPartialTicks, pEntity.xRotO, pEntity.getXRot());
        if (railPosition != null) {
            SectorVec3 forward = pEntity.getExactPosOffs(cartPosition, (double) 0.3F);
            SectorVec3 backward = pEntity.getExactPosOffs(cartPosition, (double) -0.3F);
            if (forward == null) {
                forward = railPosition;
            }

            if (backward == null) {
                backward = railPosition;
            }

            Vec3 railOffset = railPosition.relativeTo(cartPosition);
            pMatrixStack.translate(railOffset.x, (forward.y() + backward.y()) / 2.0D - cartPosition.y(), railOffset.z);
            Vec3 railDirection = backward.relativeTo(forward);
            if (railDirection.length() != 0.0D) {
                railDirection = railDirection.normalize();
                pEntityYaw = (float) (Math.atan2(railDirection.z, railDirection.x) * 180.0D / Math.PI);
                f3 = (float) (Math.atan(railDirection.y) * 73.0D);
            }
        }

        pMatrixStack.translate(0.0D, 0.375D, 0.0D);
        pMatrixStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - pEntityYaw));
        pMatrixStack.mulPose(Vector3f.ZP.rotationDegrees(-f3));
        float f5 = (float) pEntity.getHurtTime() - pPartialTicks;
        float f6 = pEntity.getDamage() - pPartialTicks;
        if (f6 < 0.0F) {
            f6 = 0.0F;
        }

        if (f5 > 0.0F) {
            pMatrixStack.mulPose(Vector3f.XP.rotationDegrees(Mth.sin(f5) * f5 * f6 / 10.0F * (float) pEntity.getHurtDir()));
        }

        int j = pEntity.getDisplayOffset();
        BlockState blockstate = pEntity.getDisplayBlockState();
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            pMatrixStack.pushPose();
            float f4 = 0.75F;
            pMatrixStack.scale(0.75F, 0.75F, 0.75F);
            pMatrixStack.translate(-0.5D, (double) ((float) (j - 8) / 16.0F), 0.5D);
            pMatrixStack.mulPose(Vector3f.YP.rotationDegrees(90.0F));
            this.renderMinecartContents(pEntity, pPartialTicks, blockstate, pMatrixStack, pBuffer, pPackedLight);
            pMatrixStack.popPose();
        }

        pMatrixStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(pEntity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        VertexConsumer vertexconsumer = pBuffer.getBuffer(this.model.renderType(this.getTextureLocation(pEntity)));
        this.model.renderToBuffer(pMatrixStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pMatrixStack.popPose();
    }

    public ResourceLocation getTextureLocation(T pEntity) {
        return MINECART_LOCATION;
    }

    protected void renderMinecartContents(T pEntity, float pPartialTicks, BlockState pState, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight) {
        this.blockRenderer.renderSingleBlock(pState, pMatrixStack, pBuffer, pPackedLight, OverlayTexture.NO_OVERLAY);
    }
}
