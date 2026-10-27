package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class LevelTicksTest {
    private static List<BlockPos> positions() {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(new BlockPos(3L, 64, 7L));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                positions.add(new BlockPos(high.addSaturated(Int1088.fromLong(3)), 64, Int1088.fromLong(7)));
                positions.add(new BlockPos(Int1088.fromLong(3), 64, high.addSaturated(Int1088.fromLong(7))));
            }
        }
        positions.add(new BlockPos(Int1088.MIN, 64, Int1088.MAX));
        positions.add(new BlockPos(Int1088.MAX, 64, Int1088.MIN));
        return positions;
    }

    @Test
    void readinessRemovalAndBudgetedDrainRetainEveryLimbAndSubTickOrder() {
        List<BlockPos> positions = positions();
        Set<ChunkPos> ready = new HashSet<>();
        LevelTicks<Object> ticks = new LevelTicks<>(ready::contains, () -> InactiveProfiler.INSTANCE);
        List<LevelChunkTicks<Object>> containers = new ArrayList<>();
        Object type = new Object();
        for (int i = 0; i < positions.size(); ++i) {
            BlockPos pos = positions.get(i);
            LevelChunkTicks<Object> container = new LevelChunkTicks<>();
            containers.add(container);
            ticks.addContainer(new ChunkPos(pos), container);
            ticks.schedule(new ScheduledTick<>(type, pos, 10L, i));
            // Deduplication is by exact position and type, not schedule time/order.
            ticks.schedule(new ScheduledTick<>(type, pos, 11L, i + 100L));
            assertTrue(ticks.hasScheduledTick(pos, type));
        }
        assertEquals(positions.size(), ticks.count());
        for (int i = 0; i < positions.size(); i += 2) ready.add(new ChunkPos(positions.get(i)));
        List<BlockPos> ran = new ArrayList<>();
        ticks.tick(10L, 3, (pos, value) -> {
            assertSame(type, value);
            ran.add(pos);
            assertFalse(ticks.willTickThisTick(pos, type));
            if (ran.size() == 1) assertTrue(ticks.willTickThisTick(positions.get(2), type));
        });
        assertEquals(List.of(positions.get(0), positions.get(2), positions.get(4)), ran);
        BlockPos removed = positions.get(6);
        ticks.removeContainer(new ChunkPos(removed));
        // A detached container must no longer update the level scheduler.
        containers.get(6).schedule(new ScheduledTick<>(new Object(), removed.above(), 0L, -1L));
        assertFalse(ticks.hasScheduledTick(removed, type));
        positions.forEach(pos -> ready.add(new ChunkPos(pos)));
        ticks.tick(10L, positions.size(), (pos, value) -> ran.add(pos));
        List<BlockPos> expected = new ArrayList<>(List.of(positions.get(0), positions.get(2), positions.get(4)));
        for (int i = 0; i < positions.size(); ++i) {
            if (i != 0 && i != 2 && i != 4 && i != 6) expected.add(positions.get(i));
        }
        assertEquals(expected, ran);
        assertEquals(0, ticks.count());
    }

    @Test
    void clearAreaOnlyInvalidatesTheExactContainer() {
        LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
        List<BlockPos> positions = positions();
        Object type = new Object();
        for (BlockPos pos : positions) {
            ticks.addContainer(new ChunkPos(pos), new LevelChunkTicks<>());
            ticks.schedule(new ScheduledTick<>(type, pos, 1L, 0L));
        }
        for (int i = 0; i < positions.size(); ++i) {
            BlockPos pos = positions.get(i);
            ticks.clearArea(BoundingBox.fromCorners(pos, pos));
            assertFalse(ticks.hasScheduledTick(pos, type));
            assertEquals(positions.size() - i - 1, ticks.count());
            for (int j = i + 1; j < positions.size(); ++j) assertTrue(ticks.hasScheduledTick(positions.get(j), type));
        }
        ticks.tick(1L, positions.size(), (pos, value) -> fail("Cleared tick ran: " + pos));
    }

    @Test
    void protoAndSavedTickListsPreserveOrderAndFilterByFullChunkIdentity() {
        ProtoChunkTicks<Object> proto = new ProtoChunkTicks<>();
        Object type = new Object();
        List<BlockPos> positions = positions();
        for (BlockPos pos : positions) {
            proto.schedule(new ScheduledTick<>(type, pos, 20L, TickPriority.HIGH, 0L));
            proto.schedule(new ScheduledTick<>(type, pos, 30L, TickPriority.LOW, 1L));
        }
        assertEquals(positions.size(), proto.count());
        assertEquals(positions, proto.scheduledTicks().stream().map(SavedTick::pos).toList());
        ListTag saved = (ListTag) proto.save(0L, value -> "test");
        for (BlockPos pos : positions) {
            assertTrue(proto.hasScheduledTick(pos, type));
            ChunkPos chunk = new ChunkPos(pos);
            ProtoChunkTicks<Object> restored = ProtoChunkTicks.load(saved, id -> Optional.of(type), chunk);
            assertEquals(1, restored.count());
            assertEquals(pos, restored.scheduledTicks().get(0).pos());
            LevelChunkTicks<Object> live = LevelChunkTicks.load(saved, id -> Optional.of(type), chunk);
            assertEquals(1, live.count());
            assertTrue(live.hasScheduledTick(pos, type));
            live.unpack(100L);
            ScheduledTick<Object> tick = live.poll();
            assertEquals(pos, tick.pos());
            assertSame(type, tick.type());
            assertEquals(TickPriority.HIGH, tick.priority());
            assertEquals(100L, tick.triggerTick());
            assertEquals(-1L, tick.subTickOrder());
            assertFalse(live.hasScheduledTick(pos, type));
        }
    }

    @Test
    void unpackedPendingTicksKeepInsertionOrderAheadOfNewTicks() {
        Int1088 origin = Int1088.ONE.shiftLeftSaturated(1024);
        BlockPos first = new BlockPos(origin, 64, origin);
        BlockPos second = first.offset(1, 0, 0);
        BlockPos third = first.offset(2, 0, 0);
        Object type = new Object();
        LevelChunkTicks<Object> ticks = new LevelChunkTicks<>(List.of(
                new SavedTick<>(type, first, 5, TickPriority.NORMAL),
                new SavedTick<>(type, second, 5, TickPriority.NORMAL)));
        ticks.unpack(10L);
        ticks.schedule(new ScheduledTick<>(type, third, 15L, 0L));
        assertEquals(List.of(first, second, third), List.of(ticks.poll().pos(), ticks.poll().pos(), ticks.poll().pos()));
        assertEquals(0, ticks.count());
    }

    @Test
    void copyDuringDrainPreservesAlreadyRunCollectedAndFutureTicksAtWideTargets() {
        for (Int1088 origin : List.of(Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX.subtract(Int1088.fromLong(3)))) {
            BlockPos source = new BlockPos(origin, 64, origin);
            BlockPos destination = new BlockPos(origin, 80, origin);
            LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true, () -> InactiveProfiler.INSTANCE);
            LevelChunkTicks<Object> container = new LevelChunkTicks<>();
            ticks.addContainer(new ChunkPos(source), container);
            Object type = new Object();
            for (int i = 0; i < 3; ++i) {
                ticks.schedule(new ScheduledTick<>(type, source.offset(i, 0, 0), i == 2 ? 20L : 10L, TickPriority.HIGH, 5L + i));
            }
            List<BlockPos> ran = new ArrayList<>();
            ticks.tick(10L, 2, (pos, value) -> {
                ran.add(pos);
                if (ran.size() == 1) {
                    assertTrue(ticks.willTickThisTick(source.offset(1, 0, 0), type));
                    ticks.copyAreaTo(BoundingBox.fromCorners(source, source.offset(2, 0, 0)), destination);
                    assertTrue(ticks.willTickThisTick(source.offset(1, 0, 0), type));
                    assertFalse(ticks.willTickThisTick(destination.offset(1, 0, 0), type));
                }
            });
            assertEquals(List.of(source, source.offset(1, 0, 0)), ran);
            List<ScheduledTick<Object>> pending = container.getAll().sorted(ScheduledTick.DRAIN_ORDER).toList();
            assertEquals(4, pending.size());
            assertEquals(List.of(destination, destination.offset(1, 0, 0), source.offset(2, 0, 0), destination.offset(2, 0, 0)),
                    pending.stream().map(ScheduledTick::pos).toList());
            assertEquals(List.of(8L, 9L, 7L, 10L), pending.stream().map(ScheduledTick::subTickOrder).toList());
            assertTrue(pending.stream().allMatch(tick -> tick.priority() == TickPriority.HIGH && tick.type() == type));
            ticks.tick(20L, 10, (pos, value) -> ran.add(pos));
            assertEquals(6, ran.size());
            assertEquals(0, ticks.count());
        }
    }
}
