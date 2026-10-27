package net.minecraft.world.level.lighting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightingOptimizationTest {
    @Test
    void skylightSourcesPropagateAtWorldEdges() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.of(-8, 1), Int1088.MIN, Int1088.MAX}) {
            LightChunkGetter source = new LightChunkGetter() {
                public BlockGetter getChunkForLighting(Int1088 cx, Int1088 cz) { return EmptyBlockGetter.INSTANCE; }
                public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
            };
            SkyLightEngine engine = new SkyLightEngine(source);
            BlockPos pos = new BlockPos(x, 8, Int1088.of(8, -2));
            SectionPos section = SectionPos.of(pos);
            engine.storage.setLevel(section, 0);
            engine.enableLightSources(section.chunk(), true);
            while (engine.hasLightWork()) engine.runUpdates(100000, true, true);
            assertEquals(15, engine.storage.getStoredLevel(pos));
            assertEquals(15, engine.storage.getStoredLevel(pos.below(8)));
        }
    }

    @Test
    void blockLightPropagatesAndRetractsAtFullWidthCoordinates() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.of(-8, 1), Int1088.MIN, Int1088.MAX}) {
            BlockPos emitter = new BlockPos(x, 8, Int1088.of(8, -2));
            boolean[] emitting = {true};
            BlockGetter blocks = new BlockGetter() {
                public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return null; }
                public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
                    return (emitting[0] && emitter.equals(pos) ? net.minecraft.world.level.block.Blocks.GLOWSTONE
                            : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState();
                }
                public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                    return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
                }
                public int getMinBuildHeight() { return 0; }
                public int getHeight() { return 16; }
            };
            LightChunkGetter source = new LightChunkGetter() {
                public BlockGetter getChunkForLighting(Int1088 cx, Int1088 cz) { return blocks; }
                public BlockGetter getLevel() { return blocks; }
            };
            BlockLightEngine engine = new BlockLightEngine(source);
            engine.storage.setLevel(SectionPos.of(emitter), 0);
            engine.onBlockEmissionIncrease(emitter, 15);
            while (engine.hasLightWork()) engine.runUpdates(100000, true, true);
            assertEquals(15, engine.getLightValue(emitter));
            assertEquals(14, engine.getLightValue(emitter.above()));
            assertEquals(13, engine.getLightValue(emitter.above(2)));
            emitting[0] = false;
            engine.checkBlock(emitter);
            while (engine.hasLightWork()) engine.runUpdates(100000, true, true);
            assertEquals(0, engine.getLightValue(emitter));
            assertEquals(0, engine.getLightValue(emitter.above()));
        }
    }

    @Test
    void primitiveSectionConversionAndAffectedSectionsMatchObjectArithmetic() {
        Random random = new Random(981723L);
        List<Int1088> values = new ArrayList<>(List.of(Int1088.MIN, Int1088.MAX, Int1088.ZERO,
                Int1088.fromLong(-1), Int1088.fromLong(15), Int1088.fromLong(16),
                Int1088.of(0, 1), Int1088.of(-1, -1), Int1088.of(Long.MIN_VALUE, 0)));
        for (int i = 0; i < 1000; ++i) {
            long[] limbs = new long[Int1088.LIMBS];
            for (int j = 0; j < limbs.length; ++j) limbs[j] = random.nextLong();
            values.add(Int1088.of(limbs));
        }
        for (Int1088 x : values) {
            Int1088 z = values.get(random.nextInt(values.size()));
            for (int y : new int[] {Integer.MIN_VALUE, -17, -1, 0, 15, 16, Integer.MAX_VALUE}) {
                BlockPos block = new BlockPos(x, y, z);
                SectionPos expected = SectionPos.of(x.shiftRight(4), y >> 4, z.shiftRight(4));
                SectionPos actual = SectionPos.of(block);
                assertEquals(expected, actual);
                assertEquals(expected.hashCode(), actual.hashCode());
                assertEquals(expected.x(), actual.x());
                assertEquals(expected.z(), actual.z());
                assertTrue(actual.contains(block));
                assertEquals(expected.equals(SectionPos.of(block.offset(1, 1, -1))), actual.contains(block.offset(1, 1, -1)));
                List<SectionPos> reference = new ArrayList<>();
                List<SectionPos> primitive = new ArrayList<>();
                SectionPos.aroundAndAtBlockPos(x, y, z, reference::add);
                SectionPos.aroundAndAtBlockPos(block, primitive::add);
                assertEquals(reference, primitive, "Affected section iteration order must not change");
            }
        }
    }

    @Test
    void snapshotsMatchHashMapAndRemainIndependentAcrossMutations() {
        Random random = new Random(18723);
        LightStorageMap<SectionPos, Integer> map = new LightStorageMap<>(new HashMap<>());
        Map<SectionPos, Integer> reference = new HashMap<>();
        List<LightStorageMap<SectionPos, Integer>> snapshots = new ArrayList<>();
        List<Map<SectionPos, Integer>> references = new ArrayList<>();
        List<SectionPos> keys = new ArrayList<>();
        for (int i = 0; i < 800; ++i) {
            keys.add(SectionPos.of(Int1088.of(random.nextLong(), random.nextLong()).shiftRight(4),
                    random.nextInt(64), Int1088.of(random.nextLong(), random.nextLong()).shiftRight(4)));
        }
        for (int i = 0; i < 5000; ++i) {
            SectionPos key = keys.get(random.nextInt(keys.size()));
            if (random.nextInt(4) == 0) assertEquals(reference.remove(key), map.remove(key));
            else assertEquals(reference.put(key, i), map.put(key, i));
            if (i % 100 == 0) {
                snapshots.add(map.copy());
                references.add(new HashMap<>(reference));
            }
        }
        assertEquals(reference, map);
        for (int i = 0; i < snapshots.size(); ++i) assertEquals(references.get(i), snapshots.get(i));
        LightStorageMap<SectionPos, Integer> branch = snapshots.get(0).copy();
        branch.put(keys.get(0), -1);
        branch.clear();
        assertTrue(branch.isEmpty());
        for (int i = 0; i < snapshots.size(); ++i) assertEquals(references.get(i), snapshots.get(i));
    }

    @Test
    void dataLayersAndSkyMetadataRemainIsolatedAfterPublicationAndRemoval() {
        SkyLightSectionStorage.SkyDataLayerStorageMap updating = new SkyLightSectionStorage.SkyDataLayerStorageMap(
                new HashMap<>(), new HashMap<>(), -4);
        SectionPos section = SectionPos.of(WorldBounds.MAX_CHUNK, 3, WorldBounds.MIN_CHUNK);
        SectionPos column = SectionPos.getZeroNode(section);
        updating.setLayer(section, new DataLayer());
        updating.topSections.put(column, 4);
        var visible = updating.copy();
        visible.disableCache();
        updating.copyDataLayer(section);
        updating.getLayer(section).set(15, 2, 0, 13);
        updating.topSections.put(column, 5);
        updating.currentLowestY = -5;
        assertEquals(0, visible.getLayer(section).get(15, 2, 0));
        assertEquals(4, visible.topSections.get(column));
        assertEquals(-4, visible.currentLowestY);
        var next = updating.copy();
        updating.removeLayer(section);
        updating.topSections.remove(column);
        assertEquals(13, next.getLayer(section).get(15, 2, 0));
        assertEquals(5, next.topSections.get(column));
        assertNotNull(visible.getLayer(section));
    }

    @Test
    void chunkCacheUsesAllLimbsAndDoesNotSurviveUpdateCallsOrCacheMisses() {
        CountingSource source = new CountingSource();
        CacheEngine engine = new CacheEngine(source);
        BlockPos first = new BlockPos(Int1088.ONE.shiftLeftSaturated(1024), 0, Int1088.MIN);
        BlockPos other = new BlockPos(Int1088.ONE.shiftLeftSaturated(1025), 0, Int1088.MIN);
        engine.positions = List.of(first, first, other, other, first);
        engine.runUpdates(1, true, true);
        assertEquals(3, source.calls);
        engine.runUpdates(1, true, true);
        assertEquals(6, source.calls);
        source.missing = true;
        engine.positions = List.of(first, first);
        engine.runUpdates(1, true, true);
        assertEquals(8, source.calls);
        source.missing = false;
        engine.runUpdates(1, true, true);
        assertEquals(9, source.calls);
        assertEquals(first.getX().shiftRight(4), source.x);
        assertEquals(first.getZ().shiftRight(4), source.z);
        engine.getChunk(first);
        engine.getChunk(first);
        assertEquals(11, source.calls, "Calls outside an update must not use retained chunks");
    }

    private static class CountingSource implements LightChunkGetter {
        int calls;
        boolean missing;
        Int1088 x;
        Int1088 z;
        public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) {
            ++this.calls;
            this.x = x;
            this.z = z;
            return this.missing ? null : EmptyBlockGetter.INSTANCE;
        }
        public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
    }

    private static class CacheEngine extends LayerLightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
        List<BlockPos> positions;
        CacheEngine(CountingSource source) {
            super(source, LightLayer.BLOCK, new CacheStorage(source));
        }
        protected int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level) { return 15; }
        protected int getComputedLevel(BlockPos pos, BlockPos excluded, int level) { return 15; }
    }

    private static class CacheStorage extends BlockLightSectionStorage {
        CacheStorage(CountingSource source) { super(source); }
        @Override
        protected void markNewInconsistencies(LayerLightEngine<BlockDataLayerStorageMap, ?> engine, boolean sky, boolean edges) {
            CacheEngine cache = (CacheEngine) engine;
            for (BlockPos pos : cache.positions) cache.getChunk(pos);
        }
    }
}
