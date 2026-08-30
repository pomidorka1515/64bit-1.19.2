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
      return Particle.createAt(position, () -> new ItemPickupParticle(dispatcher, renderBuffers, level, item, target,
            item.getDeltaMovement()));
   }

   private static SectorVec3 exactPosition(Entity entity) {
      SectorVec3 position = entity.exactPosition();
      return position != null ? position : SectorVec3.fromApproximate(entity.getX(), entity.getY(), entity.getZ());
   }

   private static SectorVec3 oldExactPosition(Entity entity) {
      SectorVec3 position = entity.oldExactPosition();
      return position != null ? position : SectorVec3.fromApproximate(entity.xOld, entity.yOld, entity.zOld);
   }

   private ItemPickupParticle(EntityRenderDispatcher p_107029_, RenderBuffers p_107030_, ClientLevel p_107031_, Entity p_107032_, Entity p_107033_, Vec3 p_107034_) {
      super(p_107031_, 0.0D, p_107032_.getY(), 0.0D, p_107034_.x, p_107034_.y, p_107034_.z);
      this.renderBuffers = p_107030_;
      this.itemEntity = this.getSafeCopy(p_107032_);
      this.target = p_107033_;
      this.entityRenderDispatcher = p_107029_;
   }

   private Entity getSafeCopy(Entity p_107037_) {
      return (Entity)(!(p_107037_ instanceof ItemEntity) ? p_107037_ : ((ItemEntity)p_107037_).copy());
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.CUSTOM;
   }

   public void render(VertexConsumer p_107039_, Camera p_107040_, float p_107041_) {
      float f = ((float)this.life + p_107041_) / 3.0F;
      f *= f;
      SectorVec3 targetCurrent = exactPosition(this.target);
      SectorVec3 targetOld = oldExactPosition(this.target);
      targetCurrent = targetCurrent.withY((targetCurrent.y() + this.target.getEyeY()) / 2.0D);
      SectorVec3 targetPosition = targetOld.lerpTo(targetCurrent, (double)p_107041_);
      SectorVec3 itemPosition = this.exactPosition().lerpTo(targetPosition, (double)f);
      SectorVec3 camera = p_107040_.exactPosition();
      if (camera == null) {
         Vec3 legacyCamera = p_107040_.getPosition();
         camera = SectorVec3.fromApproximate(legacyCamera.x, legacyCamera.y, legacyCamera.z);
      }
      Vec3 relative = itemPosition.relativeTo(camera);
      MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
      this.entityRenderDispatcher.render(this.itemEntity, relative.x, relative.y, relative.z, this.itemEntity.getYRot(), p_107041_, new PoseStack(), multibuffersource$buffersource, this.entityRenderDispatcher.getPackedLightCoords(this.itemEntity, p_107041_));
      multibuffersource$buffersource.endBatch();
   }

   public void tick() {
      ++this.life;
      if (this.life == 3) {
         this.remove();
      }

   }
}