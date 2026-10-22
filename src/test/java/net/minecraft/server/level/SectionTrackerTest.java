package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectionTrackerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void propagationFromWorldEdgesNeverCreatesAnOutOfRangeSection() {
        assertPropagationStaysInWorld(WorldBounds.MAX_CHUNK);
        assertPropagationStaysInWorld(WorldBounds.MIN_CHUNK);
    }

    private static void assertPropagationStaysInWorld(Int128 edge) {
        TestTracker tracker = new TestTracker();
        tracker.update(SectionPos.of(edge, 0, edge), 0, true);
        tracker.runAll();

        assertFalse(tracker.seen.stream().anyMatch(section -> !WorldBounds.isValidChunk(section.x(), section.z())));
        assertFalse(tracker.visited.stream().anyMatch(section -> !WorldBounds.isValidChunk(section.x(), section.z())));
        assertTrue(tracker.seen.contains(SectionPos.of(edge, 0, edge)));
    }

    @Test
    void queuedSourcesKeepBothHighHalvesAndCrossLowWordSeams() {
        TestTracker tracker = new TestTracker();
        Set<SectionPos> sources = Set.of(SectionPos.of(Int128.of(1L, -1L), 0, Int128.of(2L, -1L)),
                SectionPos.of(Int128.of(3L, -1L), 0, Int128.of(2L, -1L)),
                SectionPos.of(Int128.of(1L, -1L), 0, Int128.of(4L, -1L)));
        sources.forEach(source -> tracker.update(source, 0, true));
        tracker.runAll();
        assertEquals(sources, tracker.seen);
        assertTrue(tracker.visited.contains(SectionPos.of(Int128.of(2L, 0L), 0, Int128.of(3L, 0L))));
    }

    @Test
    void trackingRangeIsTranslationInvariantAndRejectsLowWordAliases() {
        for (Int128 center : new Int128[] {Int128.ZERO, Int128.of(123L, -1L), Int128.of(-123L, 0L),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            for (int dx = -12; dx <= 12; ++dx) {
                for (int dz = -12; dz <= 12; ++dz) {
                    Int128 x = WorldBounds.tryAddChunkOffset(center, dx);
                    Int128 z = WorldBounds.tryAddChunkOffset(center, dz);
                    if (x == null || z == null) continue;
                    for (int radius : new int[] {0, 1, 2, 8}) {
                        assertEquals(ChunkMap.isChunkInRange(dx, dz, 0L, 0L, radius),
                                ChunkMap.isChunkInRange(x, z, center, center, radius));
                    }
                }
            }
            Int128 alias = center.equals(WorldBounds.MAX_CHUNK)
                    ? center.subtract(Int128.of(1L, 0L)) : center.add(Int128.of(1L, 0L));
            assertFalse(ChunkMap.isChunkInRange(alias, center, center, center, 8L));
            assertFalse(ChunkMap.isChunkInRange(center, alias, center, center, 8L));
        }
        assertFalse(ChunkMap.isChunkInRange(WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK, 8L));
    }

    private static final class TestTracker extends SectionTracker {
        private final Set<SectionPos> seen = new HashSet<>();
        private final Set<SectionPos> visited = new HashSet<>();

        private TestTracker() {
            super(3, 64, 64);
        }

        @Override
        protected int getLevelFromSource(SectionPos sectionPos) {
            return 0;
        }

        @Override
        protected int getLevel(SectionPos sectionPos) {
            this.visited.add(sectionPos);
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
