package net.minecraft.world.level.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EntitySectionStorageTest {
    @Test
    void everyLimbRemainsDistinctInSectionTreeChunkIndexAndRemoval() {
        EntitySectionStorage<EntityAccess> storage = new EntitySectionStorage<>(EntityAccess.class,
                chunk -> Visibility.TICKING);
        List<SectionPos> sections = new ArrayList<>();
        sections.add(SectionPos.of(0L, 4, 0L));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                sections.add(SectionPos.of(high, 4, Int1088.ZERO));
                sections.add(SectionPos.of(Int1088.ZERO, 4, high));
            }
        }
        sections.add(SectionPos.of(net.minecraft.world.level.WorldBounds.MIN_CHUNK, 4,
                net.minecraft.world.level.WorldBounds.MAX_CHUNK));
        sections.add(SectionPos.of(net.minecraft.world.level.WorldBounds.MAX_CHUNK, 4,
                net.minecraft.world.level.WorldBounds.MIN_CHUNK));
        for (SectionPos section : sections) {
            TestEntity entity = new TestEntity(new BlockPos(section.chunk().getMinBlockX(), 64,
                    section.chunk().getMinBlockZ()));
            storage.getOrCreateSection(section).add(entity);
        }
        assertEquals(sections.size(), storage.count());
        assertEquals(sections.size(), storage.getAllChunksWithExistingSections().size());
        List<EntityAccess> found = new ArrayList<>();
        // A huge range uses the ordered section index rather than direct lookups.
        storage.getEntitiesInSections(Int1088.MIN, Int1088.MAX, 4, 4,
                Int1088.MIN, Int1088.MAX, found::add);
        assertEquals(sections.size(), found.size());
        assertEquals(sections.size(), new java.util.HashSet<>(found).size());
        for (SectionPos section : sections) {
            assertEquals(List.of(section), storage.getExistingSectionPositionsInChunk(section.chunk()).toList());
            found.clear();
            storage.getEntitiesInSections(section.x(), section.x(), 4, 4, section.z(), section.z(), found::add);
            assertEquals(1, found.size());
            assertEquals(section, SectionPos.of(found.get(0).blockPosition()));
        }
        for (int i = 0; i < sections.size(); ++i) {
            SectionPos section = sections.get(i);
            storage.remove(section);
            assertEquals(sections.size() - i - 1, storage.count());
            assertTrue(storage.getExistingSectionPositionsInChunk(section.chunk()).findAny().isEmpty());
            for (int j = i + 1; j < sections.size(); ++j) {
                SectionPos remaining = sections.get(j);
                assertEquals(List.of(remaining), storage.getExistingSectionPositionsInChunk(remaining.chunk()).toList());
            }
        }
        assertTrue(storage.getAllChunksWithExistingSections().isEmpty());
    }

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
        SectionPos first = SectionPos.of(Int1088.of(-1L, 123L), 4, Int1088.of(0L, -123L));
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
        storage.getEntitiesInSections(Int1088.ONE, Int1088.ONE, 4, 4, Int1088.ONE, Int1088.ONE, found::add);
        assertEquals(List.of(nearby), found);
    }

    @Test
    void fullWidthChunkIndexAndQueriesKeepHighLimbAliasesSeparate() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1000).addSaturated(Int1088.fromLong(7));
        Int1088 z = Int1088.ONE.shiftLeftSaturated(999).addSaturated(Int1088.fromLong(9));
        ChunkPos visible = new ChunkPos(x, z);
        ChunkPos hidden = new ChunkPos(x.addSaturated(Int1088.ONE.shiftLeftSaturated(900)), z);
        ChunkPos otherZ = new ChunkPos(x, z.addSaturated(Int1088.ONE.shiftLeftSaturated(900)));
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
