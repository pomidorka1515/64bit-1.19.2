package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkRequestValidationTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requestsRequireATrackedHolderWithTheRequestedTicketLevel() {
        int fullLevel = 33;
        assertTrue(ServerChunkCache.chunkAbsent(null, fullLevel), "untracked requests must be rejected");
        for (long x : new long[] {0L, WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            ChunkHolder holder = new ChunkHolder(new ChunkPos(x, 0L), fullLevel + 1,
                    LevelHeightAccessor.create(-64, 384), null, null, null);
            assertTrue(ServerChunkCache.chunkAbsent(holder, fullLevel), "an unloaded holder is insufficient");
            holder.setTicketLevel(fullLevel);
            assertFalse(ServerChunkCache.chunkAbsent(holder, fullLevel), "loaded requests must be accepted even at edges");
            holder.setTicketLevel(fullLevel + 1);
            assertTrue(ServerChunkCache.chunkAbsent(holder, fullLevel), "unloading must revoke access");
        }
    }
}
