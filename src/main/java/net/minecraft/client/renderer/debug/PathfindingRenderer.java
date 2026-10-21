package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Map<Integer, Path> pathMap = Maps.newHashMap();
    private final Map<Integer, Float> pathMaxDist = Maps.newHashMap();
    private final Map<Integer, Long> creationMap = Maps.newHashMap();
    private static final long TIMEOUT = 5000L;
    private static final float MAX_RENDER_DIST = 80.0F;
    private static final boolean SHOW_OPEN_CLOSED = true;
    private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
    private static final boolean SHOW_GROUND_LABELS = true;
    private static final float TEXT_SCALE = 0.02F;

    public void addPath(int pEntityId, Path pPath, float pMaxDistanceToWaypoint) {
        this.pathMap.put(pEntityId, pPath);
        this.creationMap.put(pEntityId, Util.getMillis());
        this.pathMaxDist.put(pEntityId, pMaxDistanceToWaypoint);
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        if (!this.pathMap.isEmpty()) {
            long i = Util.getMillis();

            for (Integer integer : this.pathMap.keySet()) {
                Path path = this.pathMap.get(integer);
                float f = this.pathMaxDist.get(integer);
                renderPath(path, f, true, true, pCamX, pCamY, pCamZ);
            }

            for (Integer integer1 : this.creationMap.keySet().toArray(new Integer[0])) {
                if (i - this.creationMap.get(integer1) > 5000L) {
                    this.pathMap.remove(integer1);
                    this.creationMap.remove(integer1);
                }
            }
        }
    }

    public static void renderPath(Path pPath, float pMaxDistanceToWaypoint, boolean pRenderOpenAndClosedSet, boolean pRenderText, double pX, double pY, double pZ) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(6.0F);
        doRenderPath(pPath, pMaxDistanceToWaypoint, pRenderOpenAndClosedSet, pRenderText, pX, pY, pZ);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void doRenderPath(Path pPath, float pMaxDistanceToWaypoint, boolean pRenderOpenAndClosedSet, boolean pRenderText, double pX, double pY, double pZ) {
        renderPathLine(pPath, pX, pY, pZ);
        BlockPos blockpos = pPath.getTarget();
        if (distanceToCamera(blockpos, pX, pY, pZ) <= 80.0F) {
            DebugRenderer.renderFilledBox(localNodeBox(blockpos, 0.25D, 0.25D, 0.75D, 0.75D), 0.0F, 1.0F, 0.0F, 0.5F);

            for (int i = 0; i < pPath.getNodeCount(); ++i) {
                Node node = pPath.getNode(i);
                if (distanceToCamera(node.asBlockPos(), pX, pY, pZ) <= 80.0F) {
                    float f = i == pPath.getNextNodeIndex() ? 1.0F : 0.0F;
                    float f1 = i == pPath.getNextNodeIndex() ? 0.0F : 1.0F;
                    DebugRenderer.renderFilledBox(localNodeBox(node.asBlockPos(), 0.5D - pMaxDistanceToWaypoint, 0.01D * i, 0.5D + pMaxDistanceToWaypoint, 0.25D + 0.01D * i), f, 0.0F, f1, 0.5F);
                }
            }
        }

        if (pRenderOpenAndClosedSet) {
            for (Node node2 : pPath.getClosedSet()) {
                if (distanceToCamera(node2.asBlockPos(), pX, pY, pZ) <= 80.0F) {
                    DebugRenderer.renderFilledBox(localNodeBox(node2.asBlockPos(), 0.5D - pMaxDistanceToWaypoint / 2, 0.01D, 0.5D + pMaxDistanceToWaypoint / 2, 0.1D), 1.0F, 0.8F, 0.8F, 0.5F);
                }
            }

            for (Node node3 : pPath.getOpenSet()) {
                if (distanceToCamera(node3.asBlockPos(), pX, pY, pZ) <= 80.0F) {
                    DebugRenderer.renderFilledBox(localNodeBox(node3.asBlockPos(), 0.5D - pMaxDistanceToWaypoint / 2, 0.01D, 0.5D + pMaxDistanceToWaypoint / 2, 0.1D), 0.8F, 1.0F, 1.0F, 0.5F);
                }
            }
        }

        if (pRenderText) {
            for (int j = 0; j < pPath.getNodeCount(); ++j) {
                Node node1 = pPath.getNode(j);
                if (distanceToCamera(node1.asBlockPos(), pX, pY, pZ) <= 80.0F) {
                    DebugRenderer.renderFloatingText(String.valueOf((Object) node1.type), node1.asBlockPos(), 0.5D, 0.75D, 0.5D, -1, 0.02F, true, 0.0F, true);
                    DebugRenderer.renderFloatingText(String.format(Locale.ROOT, "%.2f", node1.costMalus), node1.asBlockPos(), 0.5D, 0.25D, 0.5D, -1, 0.02F, true, 0.0F, true);
                }
            }
        }
    }

    public static void renderPathLine(Path pPath, double pX, double pY, double pZ) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < pPath.getNodeCount(); ++i) {
            Node node = pPath.getNode(i);
            if (!(distanceToCamera(node.asBlockPos(), pX, pY, pZ) > 80.0F)) {
                float f = (float) i / (float) pPath.getNodeCount() * 0.33F;
                int j = i == 0 ? 0 : Mth.hsvToRgb(f, 0.9F, 0.9F);
                int k = j >> 16 & 255;
                int l = j >> 8 & 255;
                int i1 = j & 255;
                var local = DebugRenderer.localPosition(node.asBlockPos(), 0.5D, 0.5D, 0.5D);
                bufferbuilder.vertex(local.x, local.y, local.z).color(k, l, i1, 255).endVertex();
            }
        }

        tesselator.end();
    }

    private static float distanceToCamera(BlockPos pPos, double pX, double pY, double pZ) {
        var local = DebugRenderer.localPosition(pPos, 0, 0, 0);
        return (float) (Math.abs(local.x) + Math.abs(local.y) + Math.abs(local.z));
    }

    private static AABB localNodeBox(BlockPos pos, double min, double minY, double max, double maxY) {
        var local = DebugRenderer.localPosition(pos, 0, 0, 0);
        return new AABB(local.x + min, local.y + minY, local.z + min, local.x + max, local.y + maxY, local.z + max);
    }
}
