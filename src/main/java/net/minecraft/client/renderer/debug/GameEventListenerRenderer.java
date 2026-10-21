package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Vector3f;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GameEventListenerRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private static final int LISTENER_RENDER_DIST = 32;
    private static final float BOX_HEIGHT = 1.0F;
    private final List<GameEventListenerRenderer.TrackedGameEvent> trackedGameEvents = Lists.newArrayList();
    private final List<GameEventListenerRenderer.TrackedListener> trackedListeners = Lists.newArrayList();

    public GameEventListenerRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        Level level = this.minecraft.level;
        if (level == null) {
            this.trackedGameEvents.clear();
            this.trackedListeners.clear();
        } else {
            SectorVec3 vec3 = this.minecraft.gameRenderer.getMainCamera().getExactPosition();
            this.trackedGameEvents.removeIf(GameEventListenerRenderer.TrackedGameEvent::isExpired);
            this.trackedListeners.removeIf((p_234512_) -> {
                return p_234512_.isExpired(level, vec3);
            });
            RenderSystem.disableTexture();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            VertexConsumer vertexconsumer = pBufferSource.getBuffer(RenderType.lines());

            for (GameEventListenerRenderer.TrackedListener gameeventlistenerrenderer$trackedlistener : this.trackedListeners) {
                gameeventlistenerrenderer$trackedlistener.exactPosition(level).ifPresent((position) -> {
                    Vec3 p_234531_ = position.relativeTo(vec3);
                    double d7 = p_234531_.x() - (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    double d8 = p_234531_.y() - (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    double d9 = p_234531_.z() - (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    double d10 = p_234531_.x() + (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    double d11 = p_234531_.y() + (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    double d12 = p_234531_.z() + (double) gameeventlistenerrenderer$trackedlistener.getListenerRadius();
                    Vector3f vector3f = new Vector3f(1.0F, 1.0F, 0.0F);
                    LevelRenderer.renderVoxelShape(pPoseStack, vertexconsumer, Shapes.create(new AABB(d7, d8, d9, d10, d11, d12)), 0, 0, 0, vector3f.x(), vector3f.y(), vector3f.z(), 0.35F);
                });
            }

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (GameEventListenerRenderer.TrackedListener gameeventlistenerrenderer$trackedlistener1 : this.trackedListeners) {
                gameeventlistenerrenderer$trackedlistener1.exactPosition(level).ifPresent((position) -> {
                    Vec3 p_234523_ = position.relativeTo(vec3);
                    Vector3f vector3f = new Vector3f(1.0F, 1.0F, 0.0F);
                    LevelRenderer.addChainedFilledBoxVertices(bufferbuilder, p_234523_.x() - 0.25D, p_234523_.y(), p_234523_.z() - 0.25D, p_234523_.x() + 0.25D, p_234523_.y() + 1.0D, p_234523_.z() + 0.25D, vector3f.x(), vector3f.y(), vector3f.z(), 0.35F);
                });
            }

            tesselator.end();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0F);
            RenderSystem.depthMask(false);

            for (GameEventListenerRenderer.TrackedListener gameeventlistenerrenderer$trackedlistener2 : this.trackedListeners) {
                gameeventlistenerrenderer$trackedlistener2.exactPosition(level).ifPresent((position) -> {
                    DebugRenderer.renderFloatingText("Listener Origin", position.add(0, 1.8D, 0), -1, 0.025F, true, 0, false);
                    DebugRenderer.renderFloatingText(position.blockPosition().toString(), position.add(0, 1.5D, 0), -6959665, 0.025F, true, 0, false);
                });
            }

            for (GameEventListenerRenderer.TrackedGameEvent gameeventlistenerrenderer$trackedgameevent : this.trackedGameEvents) {
                Vec3 vec31 = gameeventlistenerrenderer$trackedgameevent.position.relativeTo(vec3);
                double d0 = (double) 0.2F;
                double d1 = vec31.x - (double) 0.2F;
                double d2 = vec31.y - (double) 0.2F;
                double d3 = vec31.z - (double) 0.2F;
                double d4 = vec31.x + (double) 0.2F;
                double d5 = vec31.y + (double) 0.2F + 0.5D;
                double d6 = vec31.z + (double) 0.2F;
                renderTransparentFilledBox(new AABB(d1, d2, d3, d4, d5, d6), 1.0F, 1.0F, 1.0F, 0.2F);
                DebugRenderer.renderFloatingText(gameeventlistenerrenderer$trackedgameevent.gameEvent.getName(), gameeventlistenerrenderer$trackedgameevent.position.add(0, 0.85D, 0), -7564911, 0.0075F, true, 0, false);
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }
    }

    private static void renderTransparentFilledBox(AABB pBox, float pRed, float pGreen, float pBlue, float pAlpha) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            DebugRenderer.renderFilledBox(pBox, pRed, pGreen, pBlue, pAlpha);
        }
    }

    public void trackGameEvent(GameEvent pEvent, Vec3 pPos) {
        this.trackGameEvent(pEvent, SectorVec3.fromApproximate(pPos));
    }

    public void trackGameEvent(GameEvent pEvent, SectorVec3 pPos) {
        this.trackedGameEvents.add(new GameEventListenerRenderer.TrackedGameEvent(Util.getMillis(), pEvent, pPos));
    }

    public void trackListener(PositionSource pListenerSource, int pListenerRange) {
        this.trackedListeners.add(new GameEventListenerRenderer.TrackedListener(pListenerSource, pListenerRange));
    }

    @OnlyIn(Dist.CLIENT)
    static record TrackedGameEvent(long timeStamp, GameEvent gameEvent, SectorVec3 position) {
        public boolean isExpired() {
            return Util.getMillis() - this.timeStamp > 3000L;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class TrackedListener implements GameEventListener {
        public final PositionSource listenerSource;
        public final int listenerRange;

        public TrackedListener(PositionSource pListenerSource, int pListenerRange) {
            this.listenerSource = pListenerSource;
            this.listenerRange = pListenerRange;
        }

        public boolean isExpired(Level pLevel, SectorVec3 pPos) {
            return this.exactPosition(pLevel).map(position -> position.relativeTo(pPos).lengthSqr() > 1024.0D).orElse(true);
        }

        public Optional<SectorVec3> exactPosition(Level level) {
            return this.listenerSource.exactPosition(level);
        }

        public Optional<Vec3> getPosition(Level pLevel) {
            return this.listenerSource.getPosition(pLevel);
        }

        public PositionSource getListenerSource() {
            return this.listenerSource;
        }

        public int getListenerRadius() {
            return this.listenerRange;
        }

        public boolean handleGameEvent(ServerLevel pLevel, GameEvent.Message pEventMessage) {
            return false;
        }
    }
}
