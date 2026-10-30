package net.minecraft.world.level.lighting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.junit.jupiter.api.Test;

class SectionStatusOptimizationTest {
    private static final LightChunkGetter SOURCE = new LightChunkGetter() {
        public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) { return EmptyBlockGetter.INSTANCE; }
        public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
    };

    @Test
    void specializedScanMatchesOriginalWithPendingStatusesAndExcludedNeighbors() {
        Random random = new Random(718321);
        for (Int1088 coordinate : coordinates()) {
            SectionPos center = SectionPos.of(coordinate, 0, coordinate);
            List<SectionPos> neighbors = neighborhood(center);
            TestStorage storage = new TestStorage(false);
            List<SectionPos> exclusions = new ArrayList<>(neighbors);
            exclusions.add(null);
            // Same low word, different upper limbs must never exclude a neighbor.
            exclusions.add(SectionPos.of(Int1088.of(coordinate.lowLimb(), 27), 0, coordinate));
            for (int iteration = 0; iteration < 30; ++iteration) {
                for (SectionPos neighbor : neighbors) {
                    storage.setLevel(neighbor, random.nextInt(3));
                    if (random.nextBoolean()) storage.toMarkData.add(neighbor);
                    else storage.toMarkData.remove(neighbor);
                    if (random.nextBoolean()) storage.toMarkNoData.add(neighbor);
                    else storage.toMarkNoData.remove(neighbor);
                }
                for (SectionPos excluded : exclusions) {
                    for (int level = 0; level <= 2; ++level) {
                        int expected = storage.originalComputedLevel(center, excluded, level);
                        storage.levelReads = 0;
                        assertEquals(expected, storage.getComputedLevel(center, excluded, level));
                        assertEquals(0, storage.levelReads, "Status scans must not query light layers");
                    }
                }
            }
        }
    }

    @Test
    void additionsRemovalsAndReadditionsMatchOriginalWithSingleUpdateBudgets() {
        Random random = new Random(98213);
        for (Int1088 coordinate : coordinates()) {
            List<SectionPos> sections = neighborhood(SectionPos.of(coordinate, 0, coordinate));
            TestStorage optimized = new TestStorage(false);
            TestStorage original = new TestStorage(true);
            for (int batch = 0; batch < 12; ++batch) {
                for (int update = 0; update < 8; ++update) {
                    SectionPos section = sections.get(random.nextInt(sections.size()));
                    boolean empty = batch == 11 || random.nextBoolean();
                    optimized.updateSectionStatus(section, empty);
                    original.updateSectionStatus(section, empty);
                }
                int budget = 10000;
                while (optimized.pending() || original.pending()) {
                    assertTrue(--budget > 0, "Section status propagation must converge");
                    assertEquals(original.step(), optimized.step());
                    assertEquals(original.getQueueSize(), optimized.getQueueSize());
                    assertEquals(original.dataSectionSet, optimized.dataSectionSet);
                    assertEquals(original.changedSections, optimized.changedSections);
                    assertEquals(original.sectionsAffectedByLightUpdates, optimized.sectionsAffectedByLightUpdates);
                    for (SectionPos section : original.changedSections)
                        assertEquals(original.getLevel(section), optimized.getLevel(section));
                }
            }
        }
    }

    private static Int1088[] coordinates() {
        return new Int1088[] {Int1088.ZERO, Int1088.of(-1, 7), Int1088.of(0, -9),
                Int1088.ONE.shiftLeftSaturated(1000), WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK};
    }

    private static List<SectionPos> neighborhood(SectionPos center) {
        List<SectionPos> sections = new ArrayList<>();
        for (int x = -1; x <= 1; ++x) {
            Int1088 nx = WorldBounds.tryAddChunkOffset(center.x(), x);
            if (nx == null) continue;
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    Int1088 nz = WorldBounds.tryAddChunkOffset(center.z(), z);
                    if (nz != null) sections.add(SectionPos.of(nx, center.y() + y, nz));
                }
            }
        }
        return sections;
    }

    private static class TestStorage extends BlockLightSectionStorage {
        private final boolean original;
        int levelReads;

        TestStorage(boolean original) {
            super(SOURCE);
            this.original = original;
        }

        @Override
        protected int getLevel(SectionPos section) {
            ++this.levelReads;
            return super.getLevel(section);
        }

        @Override
        protected int getComputedLevel(SectionPos section, SectionPos excluded, int level) {
            return this.original ? this.originalComputedLevel(section, excluded, level)
                    : super.getComputedLevel(section, excluded, level);
        }

        int originalComputedLevel(SectionPos section, SectionPos excluded, int level) {
            for (SectionPos position : neighborhood(section)) {
                SectionPos neighbor = position.equals(section) ? null : position;
                if (neighbor == null ? excluded != null : !neighbor.equals(excluded)) {
                    level = Math.min(level, this.computeLevelFromNeighbor(neighbor, section,
                            neighbor == null ? 0 : this.getLevel(neighbor)));
                    if (level == 0) return level;
                }
            }
            return level;
        }

        boolean pending() { return this.hasWork(); }
        int step() { return this.runUpdates(1); }
    }
}
