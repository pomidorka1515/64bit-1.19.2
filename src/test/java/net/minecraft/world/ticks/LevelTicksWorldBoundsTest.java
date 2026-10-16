package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class LevelTicksWorldBoundsTest {
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
        for (long x : new long[] {Long.MIN_VALUE, Long.MAX_VALUE}) {
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
            long sourceX = reverse ? Long.MAX_VALUE - 1L : Long.MIN_VALUE;
            long targetX = reverse ? Long.MIN_VALUE : Long.MAX_VALUE - 1L;
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
