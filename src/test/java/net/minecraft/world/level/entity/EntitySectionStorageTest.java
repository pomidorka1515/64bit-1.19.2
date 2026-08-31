package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EntitySectionStorageTest {
   @Test
   void keepsDistinctLongCoordinateSections() {
      EntitySectionStorage<EntityAccess> storage = new EntitySectionStorage<>(EntityAccess.class,
            chunk -> Visibility.TICKING);
      storage.getOrCreateSection(SectionPos.of(0L, 0, 0L));
      storage.getOrCreateSection(SectionPos.of(1L << 32, 0, 0L));
      assertEquals(2, storage.count());
   }

   @Test
   void chunkLookupUsesAndMaintainsDirectIndex() {
      EntitySectionStorage<EntityAccess> storage = new EntitySectionStorage<>(EntityAccess.class,
            chunk -> Visibility.TICKING);
      SectionPos first = SectionPos.of(53_905_378_846_979_123L >> 4, 4, -53_905_378_846_979_123L >> 4);
      SectionPos second = SectionPos.of(first.x(), 5, first.z());
      ChunkPos chunk = new ChunkPos(first.x(), first.z());
      storage.getOrCreateSection(first);
      storage.getOrCreateSection(second);

      assertEquals(List.of(first, second).stream().sorted().toList(),
            storage.getExistingSectionPositionsInChunk(chunk).sorted().toList());
      assertTrue(storage.getAllChunksWithExistingSections().contains(chunk));

      storage.remove(first);
      assertEquals(List.of(second), storage.getExistingSectionPositionsInChunk(chunk).toList());
      storage.remove(second);
      assertTrue(storage.getExistingSectionPositionsInChunk(chunk).findAny().isEmpty());
      assertTrue(!storage.getAllChunksWithExistingSections().contains(chunk));
   }

   @Test
   void boundedQueryOnlyVisitsRequestedSections() {
      EntitySectionStorage<EntityAccess> storage = new EntitySectionStorage<>(EntityAccess.class,
            chunk -> Visibility.TICKING);
      TestEntity nearby = new TestEntity(new BlockPos(16L, 64, 16L));
      TestEntity farAway = new TestEntity(new BlockPos(1L << 50, 64, -(1L << 50)));
      storage.getOrCreateSection(SectionPos.of(nearby)).add(nearby);
      storage.getOrCreateSection(SectionPos.of(farAway)).add(farAway);

      List<EntityAccess> found = new ArrayList<>();
      storage.getEntitiesInSections(1L, 1L, 4, 4, 1L, 1L, found::add);
      assertEquals(List.of(nearby), found);
   }

   private static final class TestEntity implements EntityAccess {
      private final UUID uuid = UUID.randomUUID();
      private final BlockPos position;

      private TestEntity(BlockPos position) {
         this.position = position;
      }

      @Override public int getId() { return 0; }
      @Override public UUID getUUID() { return this.uuid; }
      @Override public BlockPos blockPosition() { return this.position; }
      @Override public AABB getBoundingBox() { return new AABB(this.position); }
      @Override public void setLevelCallback(EntityInLevelCallback callback) {}
      @Override public Stream<? extends EntityAccess> getSelfAndPassengers() { return Stream.of(this); }
      @Override public Stream<? extends EntityAccess> getPassengersAndSelf() { return Stream.of(this); }
      @Override public void setRemoved(Entity.RemovalReason reason) {}
      @Override public boolean shouldBeSaved() { return true; }
      @Override public boolean isAlwaysTicking() { return false; }
   }
}
