package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.SectorVec3;

public class GoToClosestVillage extends Behavior<Villager> {
   private final float speedModifier;
   private final int closeEnoughDistance;

   public GoToClosestVillage(float p_23077_, int p_23078_) {
      super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
      this.speedModifier = p_23077_;
      this.closeEnoughDistance = p_23078_;
   }

   protected boolean checkExtraStartConditions(ServerLevel p_23087_, Villager p_23088_) {
      return !p_23087_.isVillage(p_23088_.blockPosition());
   }

   protected void start(ServerLevel p_23090_, Villager p_23091_, long p_23092_) {
      PoiManager poimanager = p_23090_.getPoiManager();
      int i = poimanager.sectionsToVillage(SectionPos.of(p_23091_.blockPosition()));
      SectorVec3 target = null;

      for(int j = 0; j < 5; ++j) {
         SectorVec3 candidate = LandRandomPos.getSectorPos(p_23091_, 15, 7, (p_147554_) -> {
            return (double)(-poimanager.sectionsToVillage(SectionPos.of(p_147554_)));
         });
         if (candidate != null) {
            int k = poimanager.sectionsToVillage(SectionPos.of(candidate.blockPosition()));
            if (k < i) {
               target = candidate;
               break;
            }

            if (k == i) {
               target = candidate;
            }
         }
      }

      if (target != null) {
         p_23091_.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
               new WalkTarget(target, this.speedModifier, this.closeEnoughDistance));
      }

   }
}