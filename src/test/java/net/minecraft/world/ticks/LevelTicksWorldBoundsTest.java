package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class LevelTicksWorldBoundsTest {
    @Test
    void tickingReadinessAndRemovalUseFullWidthChunkKeys() {
        BlockPos first = new BlockPos(Int128.of(16L, 0L), 64, Int128.of(32L, 0L));
        BlockPos otherX = new BlockPos(Int128.of(48L, 0L), 64, Int128.of(32L, 0L));
        BlockPos otherZ = new BlockPos(Int128.of(16L, 0L), 64, Int128.of(64L, 0L));
        java.util.Set<ChunkPos> ready = new java.util.HashSet<>();
        LevelTicks<Object> ticks = new LevelTicks<>(ready::contains, () -> InactiveProfiler.INSTANCE);
        Object type = new Object();
        for (BlockPos pos : java.util.List.of(first, otherX, otherZ)) {
            ticks.addContainer(new ChunkPos(pos), new LevelChunkTicks<>());
            ticks.schedule(new ScheduledTick<>(type, pos, 10L, 0L));
        }
        ready.add(new ChunkPos(otherX));
        java.util.List<BlockPos> ran = new java.util.ArrayList<>();
        ticks.tick(10L, 10, (pos, value) -> ran.add(pos));
        assertEquals(java.util.List.of(otherX), ran);
        assertTrue(ticks.hasScheduledTick(first, type));
        assertTrue(ticks.hasScheduledTick(otherZ, type));
        ticks.removeContainer(new ChunkPos(first));
        ready.add(new ChunkPos(first));
        ready.add(new ChunkPos(otherZ));
        ticks.tick(11L, 10, (pos, value) -> ran.add(pos));
        assertEquals(java.util.List.of(otherX, otherZ), ran);
        assertEquals(0, ticks.count());
    }

    @Test
    void fullWidthAreaClearsDistinctHighHalfContainersWithoutScanningTheWorld() {
        LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
        Object type = new Object();
        for (Int128 x : new Int128[] {Int128.MIN, Int128.of(1L, 0L), Int128.MAX}) {
            BlockPos pos = new BlockPos(x, 64, x);
            ticks.addContainer(new ChunkPos(pos), new LevelChunkTicks<>());
            ticks.schedule(new ScheduledTick<>(type, pos, 10L, 0L));
            assertTrue(ticks.hasScheduledTick(pos, type));
        }
        assertEquals(3, ticks.count());
        ticks.clearArea(new BoundingBox(Int128.MIN, 64, Int128.MIN,
                Int128.MAX, 64, Int128.MAX));
        assertEquals(0, ticks.count());
    }

    @Test
    void legacyOffsetCopyStillPreservesItsDestination() {
        LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
        LevelChunkTicks<Object> container = new LevelChunkTicks<>();
        ticks.addContainer(new ChunkPos(0L, 0L), container);
        Object type = new Object();
        BlockPos source = new BlockPos(1L, 64, 2L);
        ticks.schedule(new ScheduledTick<>(type, source, 10L, 0L));
        ticks.copyArea(BoundingBox.fromCorners(source, source), new BlockPos(2L, 3, 4L));
        assertTrue(container.hasScheduledTick(new BlockPos(3L, 67, 6L), type));
        assertEquals(2, ticks.count());
    }

    @Test
    void clearAreaUsesExactChunkCoordinatesAtBothWorldEdges() {
        for (Int128 x : new Int128[] {Int128.MIN, Int128.MAX}) {
            LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
            BlockPos pos = new BlockPos(x, 64, x);
            LevelChunkTicks<Object> container = new LevelChunkTicks<>();
            ticks.addContainer(new ChunkPos(pos), container);
            ticks.schedule(new ScheduledTick<>(new Object(), pos, 10L, 0L));
            assertEquals(1, ticks.count());
            ticks.clearArea(BoundingBox.fromCorners(pos, pos));
            assertEquals(0, ticks.count());
        }
    }

    @Test
    void copyAreaAcrossOppositeEdgesPreservesLocalOffsetsAndTickData() {
        for (boolean reverse : new boolean[] {false, true}) {
            Int128 sourceX = reverse ? Int128.MAX.subtract(Int128.ONE) : Int128.MIN;
            Int128 targetX = reverse ? Int128.MIN : Int128.MAX.subtract(Int128.ONE);
            BlockPos source = new BlockPos(sourceX, 64, sourceX);
            BlockPos target = new BlockPos(targetX, 70, targetX);
            LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
            LevelChunkTicks<Object> sourceContainer = new LevelChunkTicks<>();
            LevelChunkTicks<Object> targetContainer = new LevelChunkTicks<>();
            ticks.addContainer(new ChunkPos(source), sourceContainer);
            ticks.addContainer(new ChunkPos(target), targetContainer);
            Object type = new Object();
            ticks.schedule(new ScheduledTick<>(type, source.offset(1, 0, 1), 123L, TickPriority.HIGH, 7L));

            ticks.copyAreaTo(BoundingBox.fromCorners(source, source.offset(1, 0, 1)), target);

            assertEquals(2, ticks.count());
            ScheduledTick<Object> copied = targetContainer.peek();
            assertEquals(target.offset(1, 0, 1), copied.pos());
            assertEquals(123L, copied.triggerTick());
            assertEquals(TickPriority.HIGH, copied.priority());
            assertEquals(8L, copied.subTickOrder());
            assertTrue(sourceContainer.hasScheduledTick(source.offset(1, 0, 1), type));
            assertFalse(targetContainer.hasScheduledTick(target, type));
        }
    }
}
