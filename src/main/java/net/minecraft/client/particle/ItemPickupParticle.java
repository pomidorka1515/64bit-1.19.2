package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemPickupParticle extends Particle {
    private static final int LIFE_TIME = 3;
    private final RenderBuffers renderBuffers;
    private final Entity itemEntity;
    private final Entity target;
    private int life;
    private final EntityRenderDispatcher entityRenderDispatcher;

    public static ItemPickupParticle create(EntityRenderDispatcher dispatcher, RenderBuffers renderBuffers,
            ClientLevel level, Entity item, Entity target) {
        SectorVec3 position = exactPosition(item);
        return Particle.createAt(position, () -> new ItemPickupParticle(dispatcher, renderBuffers, level, item, target, item.getDeltaMovement()));
    }

    private static SectorVec3 exactPosition(Entity entity) {
        SectorVec3 position = entity.exactPosition();
        return position != null ? position : SectorVec3.fromApproximate(entity.getX(), entity.getY(), entity.getZ());
    }

    private static SectorVec3 oldExactPosition(Entity entity) {
        SectorVec3 position = entity.oldExactPosition();
        return position != null ? position : SectorVec3.fromApproximate(entity.xOld, entity.yOld, entity.zOld);
    }

    private ItemPickupParticle(EntityRenderDispatcher pEntityRenderDispatcher, RenderBuffers pBuffers, ClientLevel pLevel, Entity pItemEntity, Entity pTarget, Vec3 pSpeedVector) {
        super(pLevel, 0.0D, pItemEntity.getY(), 0.0D, pSpeedVector.x, pSpeedVector.y, pSpeedVector.z);
        this.renderBuffers = pBuffers;
        this.itemEntity = this.getSafeCopy(pItemEntity);
        this.target = pTarget;
        this.entityRenderDispatcher = pEntityRenderDispatcher;
    }

    private Entity getSafeCopy(Entity pEntity) {
        return (Entity) (!(pEntity instanceof ItemEntity) ? pEntity : ((ItemEntity) pEntity).copy());
    }

    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    public void render(VertexConsumer pBuffer, Camera pRenderInfo, float pPartialTicks) {
        float f = ((float) this.life + pPartialTicks) / 3.0F;
        f *= f;
        SectorVec3 targetCurrent = exactPosition(this.target);
        SectorVec3 targetOld = oldExactPosition(this.target);
        targetCurrent = targetCurrent.withY((targetCurrent.y() + this.target.getEyeY()) / 2.0D);
        SectorVec3 targetPosition = targetOld.lerpTo(targetCurrent, (double) pPartialTicks);
        SectorVec3 itemPosition = this.exactPosition().lerpTo(targetPosition, (double) f);
        Vec3 relative = itemPosition.relativeTo(pRenderInfo.exactPosition());
        MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
        this.entityRenderDispatcher.render(this.itemEntity, relative.x, relative.y, relative.z, this.itemEntity.getYRot(), pPartialTicks, new PoseStack(), multibuffersource$buffersource, this.entityRenderDispatcher.getPackedLightCoords(this.itemEntity, pPartialTicks));
        multibuffersource$buffersource.endBatch();
    }

    public void tick() {
        ++this.life;
        if (this.life == 3) {
            this.remove();
        }
    }
}
