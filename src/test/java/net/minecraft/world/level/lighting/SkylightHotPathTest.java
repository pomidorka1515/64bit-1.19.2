package net.minecraft.world.level.lighting;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkylightHotPathTest {
    @Test
    void flatBucketsHandleCollisionsNullsDeletionAndSnapshotBranches() {
        LightStorageMap<Object, Integer> map = new LightStorageMap<>(Map.of());
        Map<Object, Integer> expected = new HashMap<>();
        for (int i = 0; i < 300; ++i) {
            Collision key = new Collision(i);
            map.put(key, i % 7 == 0 ? null : i);
            expected.put(key, i % 7 == 0 ? null : i);
        }
        map.put(null, null);
        expected.put(null, null);
        var snapshot = map.copy();
        for (int i = 0; i < 300; i += 2) assertEquals(expected.remove(new Collision(i)), map.remove(new Collision(i)));
        for (int i = 300; i < 600; ++i) {
            map.put(new Collision(i), i);
            expected.put(new Collision(i), i);
        }
        assertEquals(expected, map);
        assertTrue(map.containsKey(null));
        assertNull(map.getOrDefault(null, -1));
        assertEquals(-1, map.getOrDefault(new Collision(-1), -1));
        for (int i = 0; i < 300; ++i) {
            assertTrue(snapshot.containsKey(new Collision(i)));
            assertEquals(i % 7 == 0 ? null : Integer.valueOf(i), snapshot.get(new Collision(i)));
        }
        var branch = snapshot.copy();
        branch.remove(null);
        branch.put(new Collision(1), -1);
        assertTrue(snapshot.containsKey(null));
        assertEquals(1, snapshot.get(new Collision(1)));
        // Coordinate keys are equal across Vec3i subclasses, including BlockPos.
        map.put(SectionPos.of(1, 2, 3), 42);
        assertEquals(42, map.get(new BlockPos(1, 2, 3)));
    }

    @Test
    void sectionCacheInvalidatesPositiveAndNegativeEntriesWithoutGlobalClears() {
        var map = new SkyLightSectionStorage.SkyDataLayerStorageMap(Map.of(), Map.of(), -4);
        SectionPos section = SectionPos.of(Int1088.ONE.shiftLeftSaturated(1024), 2, Int1088.MIN.shiftRight(4));
        assertNull(map.getLayer(section));
        assertFalse(map.hasLayer(section));
        DataLayer first = new DataLayer();
        map.setLayer(section, first);
        assertSame(first, map.getLayer(section));
        var visible = map.copy();
        visible.disableCache();
        map.copyDataLayer(section);
        assertNotSame(first, map.getLayer(section));
        assertSame(first, visible.getLayer(section));
        map.removeLayer(section);
        assertNull(map.getLayer(section));
        assertFalse(map.hasLayer(section));
        map.setLayer(section, first);
        assertSame(first, map.getLayer(section));
        assertSame(section, map.sectionForBlock(section.origin().offset(15, 3, 15)));
        // A direct-cache collision must not alias distant full-width identities.
        SectionPos other = SectionPos.of(section.x().addSaturated(Int1088.fromLong(16)), section.y(), section.z());
        assertNull(map.getLayer(other));
        assertSame(first, map.getLayer(section));
    }

    @Test
    void computedLevelFetchesTargetOpacityOnlyOnce() {
        bootstrap();
        TestWorld world = new TestWorld();
        SkyLightEngine engine = new SkyLightEngine(world);
        BlockPos target = new BlockPos(8, 8, 8);
        SectionPos section = SectionPos.of(target);
        engine.storage.setLevel(section, 0);
        DataLayer layer = engine.storage.getDataLayer(section, true);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            layer.set((int) neighbor.xLimb(0) & 15, neighbor.getY() & 15, (int) neighbor.zLimb(0) & 15, 10);
        }
        world.counted = target;
        assertEquals(6, engine.getComputedLevel(target, null, 15));
        assertEquals(1, world.targetReads);
    }

    @Test
    void roofAndOpeningPropagateAndRetractSkylightAtFullWidthCoordinates() {
        bootstrap();
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            TestWorld world = new TestWorld();
            SkyLightEngine engine = new SkyLightEngine(world);
            SectionPos section = SectionPos.of(new BlockPos(x, 8, Int1088.ZERO));
            engine.storage.setLevel(section, 0);
            engine.enableLightSources(section.chunk(), true);
            drain(engine);
            BlockPos hole = section.origin().offset(8, 10, 8);
            assertEquals(15, engine.getLightValue(hole.below()));
            world.roof = true;
            for (int dx = 0; dx < 16; ++dx) {
                for (int dz = 0; dz < 16; ++dz) engine.checkBlock(section.origin().offset(dx, 10, dz));
            }
            drain(engine);
            assertEquals(15, engine.getLightValue(hole.above()));
            assertEquals(0, engine.getLightValue(hole.below()));
            world.hole = hole;
            engine.checkBlock(hole);
            drain(engine);
            assertEquals(15, engine.getLightValue(hole.below(5)));
            assertEquals(14, engine.getLightValue(hole.below().east()));
            world.hole = null;
            engine.checkBlock(hole);
            drain(engine);
            assertEquals(0, engine.getLightValue(hole.below()));
        }
    }

    private static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void sparseColumnSpanSkippingMatchesBlockByBlockPropagation() {
        bootstrap();
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(1024), Int1088.MAX}) {
            TestWorld world = new TestWorld();
            SkyLightEngine optimized = new SkyLightEngine(world);
            SkyLightEngine reference = new SkyLightEngine(world);
            SectionPos top = SectionPos.of(new BlockPos(x, 64, Int1088.ZERO));
            // Include disjoint neighboring spans separated by empty sections.
            SectionPos[] sections = {top, top.offset(1, -2, 0), top.offset(-1, -4, 0), top.offset(0, -3, 1)};
            BlockPos source = top.origin().offset(15, 0, 15);
            for (SkyLightEngine engine : new SkyLightEngine[] {optimized, reference}) {
                for (SectionPos section : sections) engine.storage.setLevel(section, 0);
                engine.storage.setStoredLevel(source, 15);
            }
            optimized.checkNeighborsAfterUpdate(source, 0, true);
            legacyCheckNeighbors(reference, source);
            assertEquals(reference.getQueueSize(), optimized.getQueueSize());
            drain(optimized);
            drain(reference);
            for (SectionPos section : sections) {
                assertArrayEquals(reference.storage.getDataLayer(section, true).getData(), optimized.storage.getDataLayer(section, true).getData());
            }
        }
    }

    /** Original per-block traversal, retained as an oracle for span skipping. */
    private static void legacyCheckNeighbors(SkyLightEngine engine, BlockPos block) {
        SectionPos section = SectionPos.of(block);
        int empty = 0;
        if ((block.getY() & 15) == 0) {
            while (!engine.storage.storingLightForSection(section.offset(0, -empty - 1, 0))
                    && engine.storage.hasSectionsBelow(section.y() - empty - 1)) ++empty;
        }
        BlockPos below = block.below(1 + empty * 16);
        if (!below.equals(block) && (section.contains(below) || engine.storage.storingLightForSection(SectionPos.of(below))))
            engine.checkNeighbor(block, below, 0, true);
        BlockPos above = block.above();
        if (!above.equals(block) && (section.contains(above) || engine.storage.storingLightForSection(SectionPos.of(above))))
            engine.checkNeighbor(block, above, 0, true);
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (int distance = 0;; ++distance) {
                BlockPos neighbor = block.offset(direction.getStepX(), -distance, direction.getStepZ());
                if (section.contains(neighbor)) {
                    if (!neighbor.equals(block)) engine.checkNeighbor(block, neighbor, 0, true);
                    break;
                }
                if (engine.storage.storingLightForSection(SectionPos.of(neighbor))) {
                    BlockPos from = block.below(distance);
                    if (!neighbor.equals(from)) engine.checkNeighbor(from, neighbor, 0, true);
                }
                if (distance > empty * 16) break;
            }
        }
    }

    private static void drain(SkyLightEngine engine) {
        int calls = 0;
        while (engine.hasLightWork()) {
            assertTrue(++calls < 1000, "Skylight must converge");
            engine.runUpdates(100000, true, true);
        }
    }

    private record Collision(int value) {
        @Override public int hashCode() { return 123; }
    }

    private static class TestWorld implements BlockGetter, LightChunkGetter {
        boolean roof;
        BlockPos hole;
        BlockPos counted;
        int targetReads;
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(this.counted)) ++this.targetReads;
            return (this.roof && pos.getY() == 10 && !pos.equals(this.hole) ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        public int getMinBuildHeight() { return 0; }
        public int getHeight() { return 16; }
        public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) { return this; }
        public BlockGetter getLevel() { return this; }
    }
}
