package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.SectorVec3;

public class VillageBoundRandomStroll extends Behavior<PathfinderMob> {
   private static final int MAX_XZ_DIST = 10;
   private static final int MAX_Y_DIST = 7;
   private final float speedModifier;
   private final int maxXyDist;
   private final int maxYDist;

   public VillageBoundRandomStroll(float p_24546_) {
      this(p_24546_, 10, 7);
   }

   public VillageBoundRandomStroll(float p_24548_, int p_24549_, int p_24550_) {
      super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
      this.speedModifier = p_24548_;
      this.maxXyDist = p_24549_;
      this.maxYDist = p_24550_;
   }

   protected void start(ServerLevel p_24556_, PathfinderMob p_24557_, long p_24558_) {
      BlockPos blockpos = p_24557_.blockPosition();
      if (p_24556_.isVillage(blockpos)) {
         this.setRandomPos(p_24557_);
      } else {
         SectionPos sectionpos = SectionPos.of(blockpos);
         SectionPos sectionpos1 = BehaviorUtils.findSectionClosestToVillage(p_24556_, sectionpos, 2);
         if (sectionpos1 != sectionpos) {
            this.setTargetedPos(p_24557_, sectionpos1);
         } else {
            this.setRandomPos(p_24557_);
         }
      }

   }

   private void setTargetedPos(PathfinderMob p_24562_, SectionPos p_24563_) {
      BlockPos center = p_24563_.center();
      SectorVec3 villageCenter = SectorVec3.fromBlockAndFraction(center.getX(), 0.5D,
            center.getY(), center.getZ(), 0.5D);
      Optional<SectorVec3> optional = Optional.ofNullable(DefaultRandomPos.getSectorPosTowards(p_24562_,
            this.maxXyDist, this.maxYDist, villageCenter, (double)((float)Math.PI / 2F)));
      p_24562_.getBrain().setMemory(MemoryModuleType.WALK_TARGET, optional.map((p_24567_) -> {
         return new WalkTarget(p_24567_, this.speedModifier, 0);
      }));
   }

   private void setRandomPos(PathfinderMob p_24560_) {
      Optional<SectorVec3> optional = Optional.ofNullable(LandRandomPos.getSectorPos(p_24560_, this.maxXyDist, this.maxYDist));
      p_24560_.getBrain().setMemory(MemoryModuleType.WALK_TARGET, optional.map((p_24565_) -> {
         return new WalkTarget(p_24565_, this.speedModifier, 0);
      }));
   }
}