package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GoalSelectorDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int MAX_RENDER_DIST = 160;
    private final Minecraft minecraft;
    private final Map<Integer, List<GoalSelectorDebugRenderer.DebugGoal>> goalSelectors = Maps.newHashMap();

    public void clear() {
        this.goalSelectors.clear();
    }

    public void addGoalSelector(int pMobId, List<GoalSelectorDebugRenderer.DebugGoal> pGoals) {
        this.goalSelectors.put(pMobId, pGoals);
    }

    public void removeGoalSelector(int pMobId) {
        this.goalSelectors.remove(pMobId);
    }

    public GoalSelectorDebugRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        BlockPos blockpos = camera.getBlockPosition().atY(0);
        this.goalSelectors.forEach((p_113559_, p_113560_) -> {
            for (int i = 0; i < p_113560_.size(); ++i) {
                GoalSelectorDebugRenderer.DebugGoal goalselectordebugrenderer$debuggoal = p_113560_.get(i);
                if (blockpos.closerThan(goalselectordebugrenderer$debuggoal.pos, 160.0D)) {
                    int j = goalselectordebugrenderer$debuggoal.isRunning ? -16711936 : -3355444;
                    DebugRenderer.renderFloatingText(goalselectordebugrenderer$debuggoal.name, goalselectordebugrenderer$debuggoal.pos, 0.5D, 2.0D + i * 0.25D, 0.5D, j, 0.02F, true, 0, false);
                }
            }
        });
        RenderSystem.enableDepthTest();
        RenderSystem.enableTexture();
    }

    @OnlyIn(Dist.CLIENT)
    public static class DebugGoal {
        public final BlockPos pos;
        public final int priority;
        public final String name;
        public final boolean isRunning;

        public DebugGoal(BlockPos pPos, int pPriority, String pName, boolean pIsRunning) {
            this.pos = pPos;
            this.priority = pPriority;
            this.name = pName;
            this.isRunning = pIsRunning;
        }
    }
}
