package net.minecraft.world.entity.boss.enderdragon.phases;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;

public class DragonLandingPhase extends AbstractDragonPhaseInstance {
   @Nullable
   private Vec3 targetLocation;

   public DragonLandingPhase(EnderDragon p_31305_) {
      super(p_31305_);
   }

   public void doClientTick() {
      Vec3 vec3 = this.dragon.getHeadLookVector(1.0F).normalize();
      vec3.yRot((-(float)Math.PI / 4F));
      Vec3 headRelative = this.dragon.head.position().subtract(this.dragon.position());

      for(int i = 0; i < 8; ++i) {
         RandomSource randomsource = this.dragon.getRandom();
         Vec3 vec31 = this.dragon.getDeltaMovement();
         this.dragon.level.addParticle(ParticleTypes.DRAGON_BREATH, this.dragon.particlePosition(
               headRelative.x + randomsource.nextGaussian() / 2.0D,
               headRelative.y + (double)this.dragon.getBbHeight() * 0.5D + randomsource.nextGaussian() / 2.0D,
               headRelative.z + randomsource.nextGaussian() / 2.0D),
               -vec3.x * (double)0.08F + vec31.x, -vec3.y * (double)0.3F + vec31.y,
               -vec3.z * (double)0.08F + vec31.z);
         vec3.yRot(0.19634955F);
      }

   }

   public void doServerTick() {
      if (this.targetLocation == null) {
         this.targetLocation = Vec3.atBottomCenterOf(this.dragon.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.END_PODIUM_LOCATION));
      }

      if (this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ()) < 1.0D) {
         this.dragon.getPhaseManager().getPhase(EnderDragonPhase.SITTING_FLAMING).resetFlameCount();
         this.dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
      }

   }

   public float getFlySpeed() {
      return 1.5F;
   }

   public float getTurnSpeed() {
      float f = (float)this.dragon.getDeltaMovement().horizontalDistance() + 1.0F;
      float f1 = Math.min(f, 40.0F);
      return f1 / f;
   }

   public void begin() {
      this.targetLocation = null;
   }

   @Nullable
   public Vec3 getFlyTargetLocation() {
      return this.targetLocation;
   }

   public EnderDragonPhase<DragonLandingPhase> getPhase() {
      return EnderDragonPhase.LANDING;
   }
}