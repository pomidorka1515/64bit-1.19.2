package net.minecraft.world.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class WaterAvoidingRandomFlyingGoal extends WaterAvoidingRandomStrollGoal {
   public WaterAvoidingRandomFlyingGoal(PathfinderMob p_25981_, double p_25982_) {
      super(p_25981_, p_25982_);
   }

   @Nullable
   protected SectorVec3 getPosition() {
      Vec3 view = this.mob.getViewVector(0.0F);
      SectorVec3 target = HoverRandomPos.getSectorPos(this.mob, 8, 7, view.x, view.z,
            ((float)Math.PI / 2F), 3, 1);
      return target != null ? target : AirAndWaterRandomPos.getSectorPos(this.mob, 8, 4, -2,
            view.x, view.z, (double)((float)Math.PI / 2F));
   }
}