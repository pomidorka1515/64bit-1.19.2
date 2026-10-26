package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CollisionBoxRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private double lastUpdateTime = Double.MIN_VALUE;
    private List<VoxelShape> shapes = Collections.emptyList();
    private SectorPhysicsOrigin origin;

    public CollisionBoxRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        double d0 = (double) Util.getNanos();
        if (d0 - this.lastUpdateTime > 1.0E8D) {
            this.lastUpdateTime = d0;
            Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
            this.origin = SectorPhysicsOrigin.from(entity.exactPosition());
            var box = entity.getSectorBoundingBox().inflate(6.0D, 6.0D, 6.0D);
            this.shapes = ImmutableList.copyOf(entity.level.getSectorCollisions(entity, box, box.toLocalAABB(this.origin), this.origin));
        }

        VertexConsumer vertexconsumer = pBufferSource.getBuffer(RenderType.lines());

        for (VoxelShape voxelshape : this.shapes) {
            var camera = DebugRenderer.cameraPosition();
            LevelRenderer.renderVoxelShape(pPoseStack, vertexconsumer, voxelshape, camera.relativeX(this.origin.originBlockX()), camera.relativeY(this.origin.originBlockY()), camera.relativeZ(this.origin.originBlockZ()), 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
