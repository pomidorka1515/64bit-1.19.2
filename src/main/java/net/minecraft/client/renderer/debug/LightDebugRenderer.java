package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LightDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private static final int MAX_RENDER_DIST = 10;

    public LightDebugRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        Level level = this.minecraft.level;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        BlockPos blockpos = this.minecraft.gameRenderer.getMainCamera().getBlockPosition();
        ObjectSet<SectionPos> longset = new ObjectOpenHashSet<>();

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            int i = level.getBrightness(LightLayer.SKY, blockpos1);
            float f = (float) (15 - i) / 15.0F * 0.5F + 0.16F;
            int j = Mth.hsvToRgb(f, 0.9F, 0.9F);
            SectionPos k = SectionPos.of(blockpos1);
            if (longset.add(k)) {
                DebugRenderer.renderFloatingText(level.getChunkSource().getLightEngine().getDebugData(LightLayer.SKY, k), k.center(), 0, 0, 0, 16711680, 0.3F, true, 0, false);
            }

            if (i != 15) {
                DebugRenderer.renderFloatingText(String.valueOf(i), blockpos1, 0.5D, 0.25D, 0.5D, j, 0.02F, true, 0, false);
            }
        }

        RenderSystem.enableTexture();
    }
}
