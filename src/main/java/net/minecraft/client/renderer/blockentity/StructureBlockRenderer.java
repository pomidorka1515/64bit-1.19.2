package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StructureBlockRenderer implements BlockEntityRenderer<StructureBlockEntity> {
    public StructureBlockRenderer(BlockEntityRendererProvider.Context pContext) {
    }

    public void render(StructureBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBufferSource, int pPackedLight, int pPackedOverlay) {
        if (Minecraft.getInstance().player.canUseGameMasterBlocks() || Minecraft.getInstance().player.isSpectator()) {
            BlockPos blockpos = pBlockEntity.getStructurePos();
            Vec3i vec3i = pBlockEntity.getStructureSize();
            if (vec3i.getX().compareTo(Int1088.ONE) >= 0 && vec3i.getY() >= 1 && vec3i.getZ().compareTo(Int1088.ONE) >= 0) {
                if (pBlockEntity.getMode() == StructureMode.SAVE || pBlockEntity.getMode() == StructureMode.LOAD) {
                    // Structure position and size are bounded template-local offsets.
                    double d0 = WorldBounds.signedDifference(blockpos.getX(), Int1088.ZERO);
                    double d1 = WorldBounds.signedDifference(blockpos.getZ(), Int1088.ZERO);
                    double d5 = (double) blockpos.getY();
                    double d8 = d5 + (double) vec3i.getY();
                    double d2;
                    double d3;
                    switch (pBlockEntity.getMirror()) {
                        case LEFT_RIGHT:
                            d2 = WorldBounds.signedDifference(vec3i.getX(), Int1088.ZERO);
                            d3 = -WorldBounds.signedDifference(vec3i.getZ(), Int1088.ZERO);
                            break;
                        case FRONT_BACK:
                            d2 = -WorldBounds.signedDifference(vec3i.getX(), Int1088.ZERO);
                            d3 = WorldBounds.signedDifference(vec3i.getZ(), Int1088.ZERO);
                            break;
                        default:
                            d2 = WorldBounds.signedDifference(vec3i.getX(), Int1088.ZERO);
                            d3 = WorldBounds.signedDifference(vec3i.getZ(), Int1088.ZERO);
                    }

                    double d4;
                    double d6;
                    double d7;
                    double d9;
                    switch (pBlockEntity.getRotation()) {
                        case CLOCKWISE_90:
                            d4 = d3 < 0.0D ? d0 : d0 + 1.0D;
                            d6 = d2 < 0.0D ? d1 + 1.0D : d1;
                            d7 = d4 - d3;
                            d9 = d6 + d2;
                            break;
                        case CLOCKWISE_180:
                            d4 = d2 < 0.0D ? d0 : d0 + 1.0D;
                            d6 = d3 < 0.0D ? d1 : d1 + 1.0D;
                            d7 = d4 - d2;
                            d9 = d6 - d3;
                            break;
                        case COUNTERCLOCKWISE_90:
                            d4 = d3 < 0.0D ? d0 + 1.0D : d0;
                            d6 = d2 < 0.0D ? d1 : d1 + 1.0D;
                            d7 = d4 + d3;
                            d9 = d6 - d2;
                            break;
                        default:
                            d4 = d2 < 0.0D ? d0 + 1.0D : d0;
                            d6 = d3 < 0.0D ? d1 + 1.0D : d1;
                            d7 = d4 + d2;
                            d9 = d6 + d3;
                    }

                    float f = 1.0F;
                    float f1 = 0.9F;
                    float f2 = 0.5F;
                    VertexConsumer vertexconsumer = pBufferSource.getBuffer(RenderType.lines());
                    if (pBlockEntity.getMode() == StructureMode.SAVE || pBlockEntity.getShowBoundingBox()) {
                        LevelRenderer.renderLineBox(pPoseStack, vertexconsumer, d4, d5, d6, d7, d8, d9, 0.9F, 0.9F, 0.9F, 1.0F, 0.5F, 0.5F, 0.5F);
                    }

                    if (pBlockEntity.getMode() == StructureMode.SAVE && pBlockEntity.getShowAir()) {
                        this.renderInvisibleBlocks(pBlockEntity, vertexconsumer, blockpos, pPoseStack);
                    }
                }
            }
        }
    }

    private void renderInvisibleBlocks(StructureBlockEntity pBlockEntity, VertexConsumer pConsumer, BlockPos pPos, PoseStack pPoseStack) {
        BlockGetter blockgetter = pBlockEntity.getLevel();
        BlockPos blockpos = pBlockEntity.getBlockPos();
        BlockPos blockpos1 = blockpos.offset(pPos);

        for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos1, blockpos1.offset(pBlockEntity.getStructureSize()).offset(-1, -1, -1))) {
            BlockState blockstate = blockgetter.getBlockState(blockpos2);
            boolean flag = blockstate.isAir();
            boolean flag1 = blockstate.is(Blocks.STRUCTURE_VOID);
            boolean flag2 = blockstate.is(Blocks.BARRIER);
            boolean flag3 = blockstate.is(Blocks.LIGHT);
            boolean flag4 = flag1 || flag2 || flag3;
            if (flag || flag4) {
                float f = flag ? 0.05F : 0.0F;
                double d0 = (double) ((float) WorldBounds.signedDifference(blockpos2.getX(), blockpos.getX()) + 0.45F - f);
                double d1 = (double) ((float) (blockpos2.getY() - blockpos.getY()) + 0.45F - f);
                double d2 = (double) ((float) WorldBounds.signedDifference(blockpos2.getZ(), blockpos.getZ()) + 0.45F - f);
                double d3 = (double) ((float) WorldBounds.signedDifference(blockpos2.getX(), blockpos.getX()) + 0.55F + f);
                double d4 = (double) ((float) (blockpos2.getY() - blockpos.getY()) + 0.55F + f);
                double d5 = (double) ((float) WorldBounds.signedDifference(blockpos2.getZ(), blockpos.getZ()) + 0.55F + f);
                if (flag) {
                    LevelRenderer.renderLineBox(pPoseStack, pConsumer, d0, d1, d2, d3, d4, d5, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
                } else if (flag1) {
                    LevelRenderer.renderLineBox(pPoseStack, pConsumer, d0, d1, d2, d3, d4, d5, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
                } else if (flag2) {
                    LevelRenderer.renderLineBox(pPoseStack, pConsumer, d0, d1, d2, d3, d4, d5, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                } else if (flag3) {
                    LevelRenderer.renderLineBox(pPoseStack, pConsumer, d0, d1, d2, d3, d4, d5, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
                }
            }
        }
    }

    public boolean shouldRenderOffScreen(StructureBlockEntity pBlockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 96;
    }
}
