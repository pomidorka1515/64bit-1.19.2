package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class DragonChargePlayerPhase extends AbstractDragonPhaseInstance {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CHARGE_RECOVERY_TIME = 10;
   @Nullable
   private net.minecraft.world.phys.SectorVec3 targetLocation;
   private int timeSinceCharge;

   public DragonChargePlayerPhase(EnderDragon p_31206_) {
      super(p_31206_);
   }

   public void doServerTick() {
      if (this.targetLocation == null) {
         LOGGER.warn("Aborting charge player as no target was set.");
         this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
      } else if (this.timeSinceCharge > 0 && this.timeSinceCharge++ >= 10) {
         this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
      } else {
         double d0 = this.targetLocation.relativeTo(this.dragon.sectorPosition()).lengthSqr();
         if (d0 < 100.0D || d0 > 22500.0D || this.dragon.horizontalCollision || this.dragon.verticalCollision) {
            ++this.timeSinceCharge;
         }

      }
   }

   public void begin() {
      this.targetLocation = null;
      this.timeSinceCharge = 0;
   }

   public void setTarget(Vec3 p_31208_) {
      this.setTarget(net.minecraft.world.phys.SectorVec3.fromApproximate(p_31208_.x, p_31208_.y, p_31208_.z));
   }

   public void setTarget(net.minecraft.world.phys.SectorVec3 target) {
      this.targetLocation = target;
   }

   public float getFlySpeed() {
      return 3.0F;
   }

   @Nullable
   public Vec3 getFlyTargetLocation() {
      return this.targetLocation == null ? null : this.targetLocation.toApproximateVec3();
   }

   public net.minecraft.world.phys.SectorVec3 getExactFlyTargetLocation() {
      return this.targetLocation;
   }

   public EnderDragonPhase<DragonChargePlayerPhase> getPhase() {
      return EnderDragonPhase.CHARGING_PLAYER;
   }
}
