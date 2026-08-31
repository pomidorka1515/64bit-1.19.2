package net.minecraft.world.entity.ai.goal;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.SectorVec3;

public class GolemRandomStrollInVillageGoal extends RandomStrollGoal {
   private static final int POI_SECTION_SCAN_RADIUS = 2;
   private static final int VILLAGER_SCAN_RADIUS = 32;
   private static final int RANDOM_POS_XY_DISTANCE = 10;
   private static final int RANDOM_POS_Y_DISTANCE = 7;

   public GolemRandomStrollInVillageGoal(PathfinderMob p_25398_, double p_25399_) {
      super(p_25398_, p_25399_, 240, false);
   }

   @Nullable
   protected SectorVec3 getPosition() {
      float choice = this.mob.level.random.nextFloat();
      if (this.mob.level.random.nextFloat() < 0.3F) {
         return this.getPositionTowardsAnywhere();
      } else {
         SectorVec3 target;
         if (choice < 0.7F) {
            target = this.getPositionTowardsVillagerWhoWantsGolem();
            if (target == null) {
               target = this.getPositionTowardsPoi();
            }
         } else {
            target = this.getPositionTowardsPoi();
            if (target == null) {
               target = this.getPositionTowardsVillagerWhoWantsGolem();
            }
         }

         return target == null ? this.getPositionTowardsAnywhere() : target;
      }
   }

   @Nullable
   private SectorVec3 getPositionTowardsAnywhere() {
      return LandRandomPos.getSectorPos(this.mob, 10, 7);
   }

   @Nullable
   private SectorVec3 getPositionTowardsVillagerWhoWantsGolem() {
      ServerLevel serverlevel = (ServerLevel)this.mob.level;
      List<Villager> list = serverlevel.getSectorEntities(EntityType.VILLAGER,
            this.mob.getSectorBoundingBox().inflate(32.0D, 32.0D, 32.0D), this::doesVillagerWantGolem);
      if (list.isEmpty()) {
         return null;
      } else {
         Villager villager = list.get(this.mob.level.random.nextInt(list.size()));
         return LandRandomPos.getSectorPosTowards(this.mob, 10, 7, villager);
      }
   }

   @Nullable
   private SectorVec3 getPositionTowardsPoi() {
      SectionPos sectionpos = this.getRandomVillageSection();
      if (sectionpos == null) {
         return null;
      } else {
         BlockPos blockpos = this.getRandomPoiWithinSection(sectionpos);
         return blockpos == null ? null : LandRandomPos.getSectorPosTowards(this.mob, 10, 7,
               SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, blockpos.getY(),
                     blockpos.getZ(), 0.5D));
      }
   }

   @Nullable
   private SectionPos getRandomVillageSection() {
      ServerLevel serverlevel = (ServerLevel)this.mob.level;
      List<SectionPos> list = SectionPos.cube(SectionPos.of(this.mob), 2).filter((p_25402_) -> {
         return serverlevel.sectionsToVillage(p_25402_) == 0;
      }).collect(Collectors.toList());
      return list.isEmpty() ? null : list.get(serverlevel.random.nextInt(list.size()));
   }

   @Nullable
   private BlockPos getRandomPoiWithinSection(SectionPos p_25408_) {
      ServerLevel serverlevel = (ServerLevel)this.mob.level;
      PoiManager poimanager = serverlevel.getPoiManager();
      List<BlockPos> list = poimanager.getInRange((p_217747_) -> {
         return true;
      }, p_25408_.center(), 8, PoiManager.Occupancy.IS_OCCUPIED).map(PoiRecord::getPos).collect(Collectors.toList());
      return list.isEmpty() ? null : list.get(serverlevel.random.nextInt(list.size()));
   }

   private boolean doesVillagerWantGolem(Villager p_25406_) {
      return p_25406_.wantsToSpawnGolem(this.mob.level.getGameTime());
   }
}