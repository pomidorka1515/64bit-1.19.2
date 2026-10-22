package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
        SectionPos first = SectionPos.of(Int128.of(123L, -1L), 4, Int128.of(-123L, 0L));
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
        storage.getEntitiesInSections(Int128.ONE, Int128.ONE, 4, 4, Int128.ONE, Int128.ONE, found::add);
        assertEquals(List.of(nearby), found);
    }

    @Test
    void fullWidthChunkIndexAndQueriesKeepHighHalfAliasesSeparate() {
        ChunkPos visible = new ChunkPos(Int128.of(1L, 7L), Int128.of(2L, 9L));
        ChunkPos hidden = new ChunkPos(Int128.of(3L, 7L), Int128.of(2L, 9L));
        ChunkPos otherZ = new ChunkPos(Int128.of(1L, 7L), Int128.of(4L, 9L));
        List<ChunkPos> visibilityLookups = new ArrayList<>();
        EntitySectionStorage<EntityAccess> storage = new EntitySectionStorage<>(EntityAccess.class, chunk -> {
            visibilityLookups.add((ChunkPos) chunk);
            return chunk.equals(hidden) ? Visibility.HIDDEN : Visibility.TICKING;
        });
        for (ChunkPos chunk : List.of(visible, hidden, otherZ)) {
            TestEntity entity = new TestEntity(new BlockPos(chunk.getMinBlockX(), 64, chunk.getMinBlockZ()));
            storage.getOrCreateSection(SectionPos.of(entity)).add(entity);
        }
        assertEquals(List.of(visible, hidden, otherZ), visibilityLookups);
        assertEquals(3, storage.getAllChunksWithExistingSections().size());
        for (ChunkPos chunk : List.of(visible, hidden, otherZ)) {
            SectionPos section = SectionPos.of(chunk.x(), 4, chunk.z());
            assertEquals(List.of(section), storage.getExistingSectionPositionsInChunk(chunk).toList());
            List<EntityAccess> found = new ArrayList<>();
            storage.getEntitiesInSections(chunk.x(), chunk.x(), 4, 4, chunk.z(), chunk.z(), found::add);
            assertEquals(chunk.equals(hidden) ? 0 : 1, found.size());
            if (!found.isEmpty()) assertEquals(chunk, new ChunkPos(found.get(0).blockPosition()));
        }
        storage.remove(SectionPos.of(visible.x(), 4, visible.z()));
        assertEquals(2, storage.count());
        assertTrue(storage.getExistingSectionPositionsInChunk(visible).findAny().isEmpty());
        assertEquals(1, storage.getExistingSectionPositionsInChunk(hidden).count());
        assertEquals(1, storage.getExistingSectionPositionsInChunk(otherZ).count());
    }

    private static final class TestEntity implements EntityAccess {
        private final UUID uuid = UUID.randomUUID();
        private final BlockPos position;

        private TestEntity(BlockPos position) {
            this.position = position;
        }

        @Override
        public int getId() {
            return 0;
        }
        @Override
        public UUID getUUID() {
            return this.uuid;
        }
        @Override
        public BlockPos blockPosition() {
            return this.position;
        }
        @Override
        public AABB exactBoundingBox() {
            return new AABB(this.position);
        }
        @Override
        public void setLevelCallback(EntityInLevelCallback callback) {}
        @Override
        public Stream<? extends EntityAccess> getSelfAndPassengers() {
            return Stream.of(this);
        }
        @Override
        public Stream<? extends EntityAccess> getPassengersAndSelf() {
            return Stream.of(this);
        }
        @Override
        public void setRemoved(Entity.RemovalReason reason) {}
        @Override
        public boolean shouldBeSaved() {
            return true;
        }
        @Override
        public boolean isAlwaysTicking() {
            return false;
        }
    }
}
