package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TrackingEmitter extends NoRenderParticle {
   private final Entity entity;
   private int life;
   private final int lifeTime;
   private final ParticleOptions particleType;

   public TrackingEmitter(ClientLevel p_108390_, Entity p_108391_, ParticleOptions p_108392_) {
      this(p_108390_, p_108391_, p_108392_, 3);
   }

   public TrackingEmitter(ClientLevel p_108394_, Entity p_108395_, ParticleOptions p_108396_, int p_108397_) {
      this(p_108394_, p_108395_, p_108396_, p_108397_, p_108395_.getDeltaMovement());
   }

   private TrackingEmitter(ClientLevel p_108399_, Entity p_108400_, ParticleOptions p_108401_, int p_108402_, Vec3 p_108403_) {
      super(p_108399_, 0.0D, p_108400_.getY(0.5D), 0.0D, p_108403_.x, p_108403_.y, p_108403_.z);
      this.entity = p_108400_;
      this.lifeTime = p_108402_;
      this.particleType = p_108401_;
      this.tick();
   }

   public void tick() {
      for(int i = 0; i < 16; ++i) {
         double d0 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
         double d1 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
         double d2 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
         if (!(d0 * d0 + d1 * d1 + d2 * d2 > 1.0D)) {
            SectorVec3 position = this.entity.exactPosition();
            if (position == null) {
               position = SectorVec3.fromApproximate(this.entity.getX(), this.entity.getY(), this.entity.getZ());
            }
            position = position.add(d0 / 4.0D, this.entity.getBbHeight() * (0.5D + d1 / 4.0D), d2 / 4.0D);
            this.level.addParticle(this.particleType, false, position, d0, d1 + 0.2D, d2);
         }
      }

      ++this.life;
      if (this.life >= this.lifeTime) {
         this.remove();
      }

   }
}