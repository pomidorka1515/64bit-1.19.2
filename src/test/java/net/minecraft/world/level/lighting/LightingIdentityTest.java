package net.minecraft.world.level.lighting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.Test;

class LightingIdentityTest {
    private static List<BlockPos> positions() {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(BlockPos.ZERO);
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                positions.add(new BlockPos(high, 0, Int1088.ZERO));
                positions.add(new BlockPos(Int1088.ZERO, 0, high));
            }
        }
        positions.add(new BlockPos(Int1088.MIN, 0, Int1088.MAX));
        positions.add(new BlockPos(Int1088.MAX, 0, Int1088.MIN));
        return positions;
    }

    @Test
    void graphQueuesPreserveFullIdentityAndInsertionOrderAcrossGrowthAndRemoval() {
        TestGraph graph = new TestGraph();
        List<BlockPos> positions = positions();
        for (BlockPos pos : positions) {
            graph.enqueue(pos);
            graph.enqueue(pos);
        }
        assertEquals(positions.size(), graph.getQueueSize());
        List<BlockPos> expected = new ArrayList<>();
        for (int i = 0; i < positions.size(); ++i) {
            if (i % 3 == 0) graph.removeFromQueue(positions.get(i));
            else expected.add(positions.get(i));
        }
        for (int i = 0; i < positions.size(); i += 3) {
            graph.enqueue(positions.get(i));
            expected.add(positions.get(i));
        }
        assertEquals(positions.size(), graph.getQueueSize());
        for (BlockPos pos : expected) {
            assertEquals(0, graph.runUpdates(1));
            assertEquals(pos, graph.updated.get(graph.updated.size() - 1));
        }
        assertEquals(expected, graph.updated);
        assertEquals(0, graph.getQueueSize());
        assertFalse(graph.hasWork());
    }

    @Test
    void sectionLayersCacheAndSkyColumnsRetainAllLimbsAcrossSnapshots() {
        var updating = new SkyLightSectionStorage.SkyDataLayerStorageMap(new HashMap<>(), new HashMap<>(), -4);
        List<BlockPos> blocks = positions();
        List<SectionPos> sections = blocks.stream().map(SectionPos::of).toList();
        for (int i = 0; i < sections.size(); ++i) {
            SectionPos section = sections.get(i);
            DataLayer data = new DataLayer();
            data.set(0, 0, 0, i % 15 + 1);
            updating.setLayer(section, data);
            updating.topSections.put(SectionPos.getZeroNode(section), i);
        }
        var visible = updating.copy();
        for (int i = 0; i < sections.size(); ++i) {
            SectionPos section = sections.get(i);
            assertEquals(i % 15 + 1, updating.getLayer(section).get(0, 0, 0));
            assertEquals(section, updating.sectionForBlock(blocks.get(i)));
            updating.copyDataLayer(section);
            updating.getLayer(section).set(0, 0, 0, 0);
            updating.topSections.remove(SectionPos.getZeroNode(section));
        }
        for (int i = 0; i < sections.size(); ++i) {
            SectionPos section = sections.get(i);
            assertEquals(i % 15 + 1, visible.getLayer(section).get(0, 0, 0));
            assertEquals(i, visible.topSections.get(SectionPos.getZeroNode(section)));
            assertEquals(0, updating.getLayer(section).get(0, 0, 0));
        }
    }

    private static final class TestGraph extends DynamicGraphMinFixedPoint<BlockPos> {
        private final Map<BlockPos, Integer> levels = new HashMap<>();
        private final List<BlockPos> updated = new ArrayList<>();

        TestGraph() { super(16, 2, 2); }
        void enqueue(BlockPos pos) { this.checkEdge(null, pos, 3, true); }
        protected boolean isSource(BlockPos pos) { return pos == null; }
        protected int getComputedLevel(BlockPos pos, BlockPos excluded, int level) { return level; }
        protected void checkNeighborsAfterUpdate(BlockPos pos, int level, boolean decreasing) {}
        protected int getLevel(BlockPos pos) { return this.levels.getOrDefault(pos, 15); }
        protected void setLevel(BlockPos pos, int level) {
            this.levels.put(pos, level);
            this.updated.add(pos);
        }
        protected int computeLevelFromNeighbor(BlockPos from, BlockPos to, int level) { return level + 1; }
    }
}
