package net.minecraft.world.level.entity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersistentEntitySectionManagerTest {
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
        for (Int128 coordinate : List.of(Int128.fromLong(-1L), Int128.of(7L, -1L),
                Int128.of(-8L, 0L), WorldBounds.MIN_BLOCK, WorldBounds.MAX_BLOCK)) {
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
