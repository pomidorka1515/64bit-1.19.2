package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VibrationSignalParticle extends TextureSheetParticle {
   private final PositionSource target;
   private float yRot;
   private float yRotO;

   VibrationSignalParticle(ClientLevel p_234105_, double p_234106_, double p_234107_, double p_234108_, PositionSource p_234109_, int p_234110_) {
      super(p_234105_, p_234106_, p_234107_, p_234108_, 0.0D, 0.0D, 0.0D);
      this.quadSize = 0.3F;
      this.target = p_234109_;
      this.lifetime = p_234110_;
   }

   public void render(VertexConsumer p_172475_, Camera p_172476_, float p_172477_) {
      float f = Mth.sin(((float)this.age + p_172477_ - ((float)Math.PI * 2F)) * 0.05F) * 2.0F;
      float f1 = Mth.lerp(p_172477_, this.yRotO, this.yRot);
      float f2 = 1.0472F;
      this.renderSignal(p_172475_, p_172476_, p_172477_, (p_172487_) -> {
         p_172487_.mul(Vector3f.YP.rotation(f1));
         p_172487_.mul(Vector3f.XP.rotation(-1.0472F));
         p_172487_.mul(Vector3f.YP.rotation(f));
      });
      this.renderSignal(p_172475_, p_172476_, p_172477_, (p_172473_) -> {
         p_172473_.mul(Vector3f.YP.rotation(-(float)Math.PI + f1));
         p_172473_.mul(Vector3f.XP.rotation(1.0472F));
         p_172473_.mul(Vector3f.YP.rotation(f));
      });
   }

   private void renderSignal(VertexConsumer p_172479_, Camera p_172480_, float p_172481_, Consumer<Quaternion> p_172482_) {
      net.minecraft.world.phys.Vec3 relative = this.cameraRelativePosition(p_172480_, p_172481_);
      float f = (float)relative.x;
      float f1 = (float)relative.y;
      float f2 = (float)relative.z;
      Vector3f vector3f = new Vector3f(0.5F, 0.5F, 0.5F);
      vector3f.normalize();
      Quaternion quaternion = new Quaternion(vector3f, 0.0F, true);
      p_172482_.accept(quaternion);
      Vector3f vector3f1 = new Vector3f(-1.0F, -1.0F, 0.0F);
      vector3f1.transform(quaternion);
      Vector3f[] avector3f = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
      float f3 = this.getQuadSize(p_172481_);

      for(int i = 0; i < 4; ++i) {
         Vector3f vector3f2 = avector3f[i];
         vector3f2.transform(quaternion);
         vector3f2.mul(f3);
         vector3f2.add(f, f1, f2);
      }

      float f6 = this.getU0();
      float f7 = this.getU1();
      float f4 = this.getV0();
      float f5 = this.getV1();
      int j = this.getLightColor(p_172481_);
      p_172479_.vertex((double)avector3f[0].x(), (double)avector3f[0].y(), (double)avector3f[0].z()).uv(f7, f5).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
      p_172479_.vertex((double)avector3f[1].x(), (double)avector3f[1].y(), (double)avector3f[1].z()).uv(f7, f4).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
      p_172479_.vertex((double)avector3f[2].x(), (double)avector3f[2].y(), (double)avector3f[2].z()).uv(f6, f4).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
      p_172479_.vertex((double)avector3f[3].x(), (double)avector3f[3].y(), (double)avector3f[3].z()).uv(f6, f5).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
   }

   public int getLightColor(float p_172469_) {
      return 240;
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         Optional<SectorVec3> optional = this.target.exactPosition(this.level);
         if (optional.isEmpty()) {
            this.remove();
         } else {
            int i = this.lifetime - this.age;
            double d0 = 1.0D / (double)i;
            SectorVec3 targetPosition = optional.get();
            SectorVec3 currentPosition = this.exactPosition();
            net.minecraft.world.phys.Vec3 targetRelative = targetPosition.relativeTo(currentPosition);
            double dx = targetRelative.x * d0;
            double dy = targetRelative.y * d0;
            double dz = targetRelative.z * d0;
            this.x += dx;
            this.y += dy;
            this.z += dz;
            this.yRotO = this.yRot;
            this.yRot = (float)Mth.atan2(-dx, -dz);
         }
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static class Provider implements ParticleProvider<VibrationParticleOption> {
      private final SpriteSet sprite;

      public Provider(SpriteSet p_172490_) {
         this.sprite = p_172490_;
      }

      public Particle createParticle(VibrationParticleOption p_172501_, ClientLevel p_172502_, double p_172503_, double p_172504_, double p_172505_, double p_172506_, double p_172507_, double p_172508_) {
         VibrationSignalParticle vibrationsignalparticle = new VibrationSignalParticle(p_172502_, p_172503_, p_172504_, p_172505_, p_172501_.getDestination(), p_172501_.getArrivalInTicks());
         vibrationsignalparticle.pickSprite(this.sprite);
         vibrationsignalparticle.setAlpha(1.0F);
         return vibrationsignalparticle;
      }
   }
}