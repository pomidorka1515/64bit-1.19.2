package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldEdgeLightingTest {
    private static final LightChunkGetter NO_CHUNKS = new LightChunkGetter() {
        public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) { return null; }
        public BlockGetter getLevel() { return null; }
    };

    @Test
    void sectionEdgeChecksNeverConnectOppositeWorldEdges() {
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.of(0, 1), WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            for (Int1088 z : new Int1088[] {Int1088.ZERO, Int1088.of(0, 1), WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
                RecordingEngine engine = new RecordingEngine();
                SectionPos section = SectionPos.of(x, 4, z);
                engine.storage.setLevel(section, 0);
                engine.storage.queueSectionData(section, new DataLayer(), false);
                engine.storage.markNewInconsistencies(engine, true, false);
                assertEquals(0, engine.edges, "An isolated section has no stored neighbors");

                // A genuine inward neighbor must still have all 256 face cells checked both ways.
                SectionPos inward = section.offset(x.equals(WorldBounds.MAX_CHUNK) ? -1 : 1, 0, 0);
                engine.storage.setLevel(inward, 0);
                engine.storage.queueSectionData(section, new DataLayer(), false);
                engine.storage.markNewInconsistencies(engine, true, false);
                assertEquals(512, engine.edges);
            }
        }
    }

    private static class RecordingEngine extends LayerLightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
        int edges;

        RecordingEngine() {
            super(NO_CHUNKS, LightLayer.BLOCK, new BlockLightSectionStorage(NO_CHUNKS));
        }

        protected int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level) {
            assertTrue(WorldBounds.distance(source.getX(), target.getX()) <= 1);
            assertTrue(WorldBounds.distance(source.getZ(), target.getZ()) <= 1);
            ++this.edges;
            return 15;
        }

        protected int getComputedLevel(BlockPos pos, BlockPos excluded, int level) {
            return 15;
        }
    }
}
