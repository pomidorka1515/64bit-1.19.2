package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WaterDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public WaterDebugRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        BlockPos blockpos = this.minecraft.player.blockPosition();
        LevelReader levelreader = this.minecraft.player.level;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0F, 1.0F, 0.0F, 0.75F);
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(6.0F);

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            FluidState fluidstate = levelreader.getFluidState(blockpos1);
            if (fluidstate.is(FluidTags.WATER)) {
                double d0 = (double) ((float) blockpos1.getY() + fluidstate.getHeight(levelreader, blockpos1));
                var local = DebugRenderer.localPosition(blockpos1, 0, 0, 0);
                DebugRenderer.renderFilledBox(new AABB(local.x + 0.01D, local.y + 0.01D, local.z + 0.01D, local.x + 0.99D, local.y + fluidstate.getHeight(levelreader, blockpos1), local.z + 0.99D), 1.0F, 1.0F, 1.0F, 0.2F);
            }
        }

        for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            FluidState fluidstate1 = levelreader.getFluidState(blockpos2);
            if (fluidstate1.is(FluidTags.WATER)) {
                DebugRenderer.renderFloatingText(String.valueOf(fluidstate1.getAmount()), blockpos2, 0.5D, fluidstate1.getHeight(levelreader, blockpos2), 0.5D, -16777216, 0.02F, true, 0, false);
            }
        }

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }
}
