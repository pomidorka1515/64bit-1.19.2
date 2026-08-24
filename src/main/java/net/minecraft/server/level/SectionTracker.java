package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

/** Propagates section status using 3×3×3 neighbouring sections. */
public abstract class SectionTracker extends DynamicGraphMinFixedPoint<SectionPos> {
   protected SectionTracker(int levelCount, int expectedQueueSize, int expectedMapSize) {
      super(levelCount, expectedQueueSize, expectedMapSize);
   }

   protected boolean isSource(SectionPos sectionPos) {
      return sectionPos == null;
   }

   protected void checkNeighborsAfterUpdate(SectionPos sectionPos, int level, boolean decreasing) {
      for (int x = -1; x <= 1; ++x) {
         for (int y = -1; y <= 1; ++y) {
            for (int z = -1; z <= 1; ++z) {
               SectionPos neighbor = sectionPos.offset(x, y, z);
               if (!neighbor.equals(sectionPos)) {
                  this.checkNeighbor(sectionPos, neighbor, level, decreasing);
               }
            }
         }
      }
   }

   protected int getComputedLevel(SectionPos sectionPos, SectionPos excludedNeighbor, int level) {
      int computedLevel = level;
      for (int x = -1; x <= 1; ++x) {
         for (int y = -1; y <= 1; ++y) {
            for (int z = -1; z <= 1; ++z) {
               SectionPos neighbor = sectionPos.offset(x, y, z);
               if (neighbor.equals(sectionPos)) {
                  neighbor = null;
               }

               if (neighbor != excludedNeighbor) {
                  int neighborLevel = this.computeLevelFromNeighbor(neighbor, sectionPos, neighbor == null ? 0 : this.getLevel(neighbor));
                  if (computedLevel > neighborLevel) {
                     computedLevel = neighborLevel;
                  }
                  if (computedLevel == 0) {
                     return computedLevel;
                  }
               }
            }
         }
      }
      return computedLevel;
   }

   protected int computeLevelFromNeighbor(SectionPos source, SectionPos target, int level) {
      return source == null ? this.getLevelFromSource(target) : level + 1;
   }

   protected abstract int getLevelFromSource(SectionPos sectionPos);

   public void update(SectionPos sectionPos, int level, boolean decreasing) {
      this.checkEdge(null, sectionPos, level, decreasing);
   }
}