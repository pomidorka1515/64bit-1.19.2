package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectionTrackerTest {
   @Test
   void propagationFromWorldEdgesNeverCreatesAnOutOfRangeSection() {
      assertPropagationStaysInWorld(WorldBounds.MAX_CHUNK);
      assertPropagationStaysInWorld(WorldBounds.MIN_CHUNK);
   }

   private static void assertPropagationStaysInWorld(long edge) {
      TestTracker tracker = new TestTracker();
      tracker.update(SectionPos.of(edge, 0, edge), 0, true);
      tracker.runAll();

      assertFalse(tracker.seen.stream().anyMatch(section -> !WorldBounds.isValidChunk(section.x(), section.z())));
      assertTrue(tracker.seen.stream().anyMatch(section -> section.x() == edge && section.z() == edge));
   }

   private static final class TestTracker extends SectionTracker {
      private final Set<SectionPos> seen = new HashSet<>();

      private TestTracker() {
         super(3, 64, 64);
      }

      @Override
      protected int getLevelFromSource(SectionPos sectionPos) {
         return 0;
      }

      @Override
      protected int getLevel(SectionPos sectionPos) {
         return this.seen.contains(sectionPos) ? 0 : 2;
      }

      @Override
      protected void setLevel(SectionPos sectionPos, int level) {
         if (level == 0) {
            this.seen.add(sectionPos);
         } else {
            this.seen.remove(sectionPos);
         }
      }

      private void runAll() {
         this.runUpdates(Integer.MAX_VALUE);
      }
   }
}
