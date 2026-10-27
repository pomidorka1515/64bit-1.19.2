package net.minecraft.world.level.entity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersistentEntitySectionManagerTest {
    @Test
    void pendingLoadsVisibilityAndUnloadCancellationUseEveryChunkLimb() {
        List<ChunkPos> requested = new java.util.ArrayList<>();
        java.util.Map<ChunkPos, CompletableFuture<ChunkEntities<EntityAccess>>> pending = new java.util.HashMap<>();
        EntityPersistentStorage<EntityAccess> storage = new EntityPersistentStorage<>() {
            public CompletableFuture<ChunkEntities<EntityAccess>> loadEntities(ChunkPos pos) {
                requested.add(pos);
                CompletableFuture<ChunkEntities<EntityAccess>> future = new CompletableFuture<>();
                pending.put(pos, future);
                return future;
            }
            public void storeEntities(ChunkEntities<EntityAccess> entities) {
                assertTrue(entities.getEntities().findAny().isEmpty());
            }
            public void flush(boolean synchronize) {}
        };
        PersistentEntitySectionManager<EntityAccess> manager =
                new PersistentEntitySectionManager<>(EntityAccess.class, null, storage);
        List<ChunkPos> chunks = new java.util.ArrayList<>();
        chunks.add(new ChunkPos(0L, 0L));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                chunks.add(new ChunkPos(high, Int1088.ZERO));
                chunks.add(new ChunkPos(Int1088.ZERO, high));
            }
        }
        chunks.add(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
        chunks.add(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK));
        for (ChunkPos chunk : chunks) {
            manager.updateChunkStatus(chunk, Visibility.TICKING);
            manager.updateChunkStatus(chunk, Visibility.TICKING);
            assertFalse(manager.areEntitiesLoaded(chunk));
        }
        assertEquals(chunks, requested, "Pending loads must be deduplicated only by full identity");
        // Complete out of order and check that the inbox updates just that address.
        for (int i = chunks.size() - 1; i >= 0; --i) {
            ChunkPos chunk = chunks.get(i);
            pending.get(chunk).complete(new ChunkEntities<>(chunk, List.of()));
            manager.tick();
            for (int j = 0; j < chunks.size(); ++j) assertEquals(j >= i, manager.areEntitiesLoaded(chunks.get(j)));
        }
        for (int i = 0; i < chunks.size(); ++i) {
            ChunkPos chunk = chunks.get(i);
            manager.updateChunkStatus(chunk, Visibility.HIDDEN);
            if (i % 2 == 0) manager.updateChunkStatus(chunk, Visibility.TICKING);
        }
        manager.tick();
        for (int i = 0; i < chunks.size(); ++i) {
            ChunkPos chunk = chunks.get(i);
            assertEquals(i % 2 == 0, manager.areEntitiesLoaded(chunk));
            assertEquals(i % 2 == 0, manager.canPositionTick(chunk));
            assertEquals(i % 2 == 0, manager.canPositionTick(new BlockPos(chunk.getMinBlockX(), 64, chunk.getMinBlockZ())));
        }
        for (int i = 1; i < chunks.size(); i += 2) manager.updateChunkStatus(chunks.get(i), Visibility.TRACKED);
        assertEquals(chunks.size() + chunks.size() / 2, requested.size());
    }

    @Test
    void blockTickingProbeUsesContainingChunkAndHiddenDefault() {
        EntityPersistentStorage<EntityAccess> storage = new EntityPersistentStorage<>() {
            public CompletableFuture<ChunkEntities<EntityAccess>> loadEntities(ChunkPos pos) {
                return CompletableFuture.completedFuture(new ChunkEntities<>(pos, List.of()));
            }

            public void storeEntities(ChunkEntities<EntityAccess> entities) {
            }

            public void flush(boolean synchronize) {
            }
        };
        // No entities are inserted, so no entity lifecycle callbacks are invoked.
        PersistentEntitySectionManager<EntityAccess> manager =
                new PersistentEntitySectionManager<>(EntityAccess.class, null, storage);
        for (Int1088 coordinate : List.of(Int1088.fromLong(-1L), Int1088.of(-1L, 7L),
                Int1088.of(0L, -8L), WorldBounds.MIN_BLOCK, WorldBounds.MAX_BLOCK)) {
            BlockPos block = new BlockPos(coordinate, 64, coordinate);
            ChunkPos chunk = new ChunkPos(block);
            assertFalse(manager.canPositionTick(block));
            assertFalse(manager.canPositionTick(chunk));
            assertFalse(manager.areEntitiesLoaded(chunk));
            manager.updateChunkStatus(chunk, Visibility.TICKING);
            assertTrue(manager.canPositionTick(block));
            assertTrue(manager.canPositionTick(chunk));
            manager.updateChunkStatus(chunk, Visibility.TRACKED);
            assertFalse(manager.canPositionTick(block));
            manager.updateChunkStatus(chunk, Visibility.HIDDEN);
            assertFalse(manager.canPositionTick(block));
            assertFalse(manager.canPositionTick(chunk));
        }
    }
}
