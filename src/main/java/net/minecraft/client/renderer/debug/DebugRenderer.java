package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DebugRenderer {
    public final PathfindingRenderer pathfindingRenderer = new PathfindingRenderer();
    public final DebugRenderer.SimpleDebugRenderer waterDebugRenderer;
    public final DebugRenderer.SimpleDebugRenderer chunkBorderRenderer;
    public final DebugRenderer.SimpleDebugRenderer heightMapRenderer;
    public final DebugRenderer.SimpleDebugRenderer collisionBoxRenderer;
    public final DebugRenderer.SimpleDebugRenderer neighborsUpdateRenderer;
    public final StructureRenderer structureRenderer;
    public final DebugRenderer.SimpleDebugRenderer lightDebugRenderer;
    public final DebugRenderer.SimpleDebugRenderer worldGenAttemptRenderer;
    public final DebugRenderer.SimpleDebugRenderer solidFaceRenderer;
    public final DebugRenderer.SimpleDebugRenderer chunkRenderer;
    public final BrainDebugRenderer brainDebugRenderer;
    public final VillageSectionsDebugRenderer villageSectionsDebugRenderer;
    public final BeeDebugRenderer beeDebugRenderer;
    public final RaidDebugRenderer raidDebugRenderer;
    public final GoalSelectorDebugRenderer goalSelectorRenderer;
    public final GameTestDebugRenderer gameTestDebugRenderer;
    public final GameEventListenerRenderer gameEventListenerRenderer;
    private boolean renderChunkborder;

    public DebugRenderer(Minecraft pMinecraft) {
        this.waterDebugRenderer = new WaterDebugRenderer(pMinecraft);
        this.chunkBorderRenderer = new ChunkBorderRenderer(pMinecraft);
        this.heightMapRenderer = new HeightMapRenderer(pMinecraft);
        this.collisionBoxRenderer = new CollisionBoxRenderer(pMinecraft);
        this.neighborsUpdateRenderer = new NeighborsUpdateRenderer(pMinecraft);
        this.structureRenderer = new StructureRenderer(pMinecraft);
        this.lightDebugRenderer = new LightDebugRenderer(pMinecraft);
        this.worldGenAttemptRenderer = new WorldGenAttemptRenderer();
        this.solidFaceRenderer = new SolidFaceRenderer(pMinecraft);
        this.chunkRenderer = new ChunkDebugRenderer(pMinecraft);
        this.brainDebugRenderer = new BrainDebugRenderer(pMinecraft);
        this.villageSectionsDebugRenderer = new VillageSectionsDebugRenderer();
        this.beeDebugRenderer = new BeeDebugRenderer(pMinecraft);
        this.raidDebugRenderer = new RaidDebugRenderer(pMinecraft);
        this.goalSelectorRenderer = new GoalSelectorDebugRenderer(pMinecraft);
        this.gameTestDebugRenderer = new GameTestDebugRenderer();
        this.gameEventListenerRenderer = new GameEventListenerRenderer(pMinecraft);
    }

    public void clear() {
        this.pathfindingRenderer.clear();
        this.waterDebugRenderer.clear();
        this.chunkBorderRenderer.clear();
        this.heightMapRenderer.clear();
        this.collisionBoxRenderer.clear();
        this.neighborsUpdateRenderer.clear();
        this.structureRenderer.clear();
        this.lightDebugRenderer.clear();
        this.worldGenAttemptRenderer.clear();
        this.solidFaceRenderer.clear();
        this.chunkRenderer.clear();
        this.brainDebugRenderer.clear();
        this.villageSectionsDebugRenderer.clear();
        this.beeDebugRenderer.clear();
        this.raidDebugRenderer.clear();
        this.goalSelectorRenderer.clear();
        this.gameTestDebugRenderer.clear();
        this.gameEventListenerRenderer.clear();
    }

    public boolean switchRenderChunkborder() {
        this.renderChunkborder = !this.renderChunkborder;
        return this.renderChunkborder;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource.BufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        if (this.renderChunkborder && !Minecraft.getInstance().showOnlyReducedInfo()) {
            this.chunkBorderRenderer.render(pPoseStack, pBufferSource, pCamX, pCamY, pCamZ);
        }

        this.gameTestDebugRenderer.render(pPoseStack, pBufferSource, pCamX, pCamY, pCamZ);
    }

    public static Optional<Entity> getTargetedEntity(@Nullable Entity pEntity, int pDistance) {
        if (pEntity == null) {
            return Optional.empty();
        } else {
            SectorVec3 start = pEntity.exactEyePosition();
            Vec3 vec31 = pEntity.getViewVector(1.0F).scale((double) pDistance);
            SectorVec3 end = start.add(vec31.x, vec31.y, vec31.z);
            int i = pDistance * pDistance;
            Predicate<Entity> predicate = (p_113447_) -> {
                return !p_113447_.isSpectator() && p_113447_.isPickable();
            };
            EntityHitResult entityhitresult = ProjectileUtil.getSectorEntityHitResult(pEntity, start, end, predicate, 1.0D, (double) i);
            if (entityhitresult == null) {
                return Optional.empty();
            } else {
                return Optional.of(entityhitresult.getEntity());
            }
        }
    }

    public static void renderFilledBox(BlockPos pStart, BlockPos pEnd, float pRed, float pGreen, float pBlue, float pAlpha) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            AABB aabb = localBox(new AABB(pStart, pEnd));
            renderFilledBox(aabb, pRed, pGreen, pBlue, pAlpha);
        }
    }

    public static void renderFilledBox(BlockPos pPos, float pSize, float pRed, float pGreen, float pBlue, float pAlpha) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            AABB aabb = localBox(new AABB(pPos)).inflate((double) pSize);
            renderFilledBox(aabb, pRed, pGreen, pBlue, pAlpha);
        }
    }

    public static void renderFilledBox(AABB pBox, float pRed, float pGreen, float pBlue, float pAlpha) {
        renderFilledBox(pBox.minX, pBox.minY, pBox.minZ, pBox.maxX, pBox.maxY, pBox.maxZ, pRed, pGreen, pBlue, pAlpha);
    }

    public static void renderFilledBox(double pMinX, double pMinY, double pMinZ, double pMaxX, double pMaxY, double pMaxZ, float pRed, float pGreen, float pBlue, float pAlpha) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        LevelRenderer.addChainedFilledBoxVertices(bufferbuilder, pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ, pRed, pGreen, pBlue, pAlpha);
        tesselator.end();
    }

    public static void renderFloatingText(String pText, int pX, int pY, int pZ, int pColor) {
        renderFloatingText(pText, (double) pX + 0.5D, (double) pY + 0.5D, (double) pZ + 0.5D, pColor);
    }

    public static void renderFloatingText(String pText, double pX, double pY, double pZ, int pColor) {
        renderFloatingText(pText, pX, pY, pZ, pColor, 0.02F);
    }

    public static void renderFloatingText(String pText, double pX, double pY, double pZ, int pColor, float pScale) {
        renderFloatingText(pText, pX, pY, pZ, pColor, pScale, true, 0.0F, false);
    }

    public static void renderFloatingText(String pText, double pX, double pY, double pZ, int pColor, float pScale, boolean pCenter, float pXOffset, boolean pTransparent) {
        // Explicit legacy input boundary; exact debug positions use the overload below.
        renderFloatingText(pText, SectorVec3.fromApproximate(pX, pY, pZ), pColor, pScale, pCenter, pXOffset, pTransparent);
    }

    public static CameraRelativePosition cameraPosition() {
        return CameraRelativePosition.of(Minecraft.getInstance().gameRenderer.getMainCamera().getExactPosition());
    }

    public static Vec3 localPosition(BlockPos pos, double x, double y, double z) {
        CameraRelativePosition camera = cameraPosition();
        return new Vec3(camera.relativeX(pos.getX()) + x, camera.relativeY(pos.getY()) + y,
                camera.relativeZ(pos.getZ()) + z);
    }

    /** Exact world boxes become local only after subtracting the full-width camera. */
    public static AABB localBox(AABB box) {
        SectorVec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getExactPosition();
        return box.getSectorBounds() != null ? box.getSectorBounds().toLocalAABB(camera)
                : box.move(-camera.subX(), -camera.y(), -camera.subZ());
    }

    public static void renderFloatingText(String text, BlockPos pos, double x, double y, double z,
            int color, float scale, boolean center, float xOffset, boolean transparent) {
        renderFloatingText(text, SectorVec3.fromBlockAndFraction(pos.getX(), x, pos.getY() + y, pos.getZ(), z),
                color, scale, center, xOffset, transparent);
    }

    public static void renderFloatingText(String pText, SectorVec3 position, int pColor, float pScale,
            boolean pCenter, float pXOffset, boolean pTransparent) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.isInitialized() && minecraft.getEntityRenderDispatcher().options != null) {
            Font font = minecraft.font;
            Vec3 local = position.relativeTo(camera.getExactPosition());
            PoseStack posestack = RenderSystem.getModelViewStack();
            posestack.pushPose();
            posestack.translate((float) local.x, (float) local.y + 0.07F, (float) local.z);
            posestack.mulPoseMatrix(new Matrix4f(camera.rotation()));
            posestack.scale(pScale, -pScale, pScale);
            RenderSystem.enableTexture();
            if (pTransparent) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }

            RenderSystem.depthMask(true);
            posestack.scale(-1.0F, 1.0F, 1.0F);
            RenderSystem.applyModelViewMatrix();
            float f = pCenter ? (float) (-font.width(pText)) / 2.0F : 0.0F;
            f -= pXOffset / pScale;
            MultiBufferSource.BufferSource multibuffersource$buffersource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            font.drawInBatch(pText, f, 0.0F, pColor, false, Transformation.identity().getMatrix(), multibuffersource$buffersource, pTransparent, 0, 15728880);
            multibuffersource$buffersource.endBatch();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableDepthTest();
            posestack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface SimpleDebugRenderer {
        void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ);

        default void clear() {
        }
    }
}
