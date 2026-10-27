package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class SavedTickInt1088Test {
    @Test
    void savedTicksRoundTripEveryLimbWithStrictAxisValidationAndUnchangedMetadata() {
        Object type = new Object();
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            long[] words = new long[Int1088.LIMBS];
            words[limb] = 1L;
            for (Int1088 axis : List.of(Int1088.fromFixedLimbs(words), Int1088.fromFixedLimbs(words).negate(), Int1088.MIN, Int1088.MAX)) {
                for (TickPriority priority : TickPriority.values()) {
                    SavedTick<Object> tick = new SavedTick<>(type, new BlockPos(axis, Integer.MIN_VALUE, axis), Integer.MAX_VALUE, priority);
                    CompoundTag tag = tick.save(value -> "test");
                    assertEquals(Tag.TAG_INT, tag.getTagType("y"));
                    assertEquals(Tag.TAG_INT, tag.getTagType("t"));
                    assertEquals(17, tag.getLongArray("x").length);
                    assertEquals(17, tag.getLongArray("z").length);
                    assertEquals(tick, SavedTick.loadTick(tag, id -> Optional.of(type)).orElseThrow());
                    assertEquals((long) Integer.MAX_VALUE + 100, tick.unpack(100L, 19L).triggerTick());
                    for (String key : List.of("x", "z")) {
                        for (int length : new int[] {0, 1, 2, 16, 18}) {
                            CompoundTag invalid = tag.copy();
                            invalid.putLongArray(key, new long[length]);
                            CompoundTag snapshot = invalid.copy();
                            ListTag list = new ListTag();
                            list.add(invalid);
                            assertThrows(IllegalArgumentException.class,
                                    () -> SavedTick.loadTickList(list, id -> Optional.of(type), new ChunkPos(tick.pos()), value -> {}));
                            assertEquals(snapshot, invalid);
                        }
                        CompoundTag invalid = tag.copy();
                        invalid.putLong(key, 0L);
                        assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(invalid, id -> Optional.of(type)));
                        invalid.remove(key);
                        assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(invalid, id -> Optional.of(type)));
                    }
                }
            }
        }
    }

    @Test
    void pendingTickSnapshotsMutableWideAddressesBeforeHashingAndUnpacking() {
        for (Int1088 origin : List.of(Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX)) {
            BlockPos original = new BlockPos(origin, 64, origin);
            BlockPos.MutableBlockPos cursor = original.mutable();
            Object type = new Object();
            SavedTick<Object> saved = new SavedTick<>(type, cursor, 37, TickPriority.HIGH);
            LevelChunkTicks<Object> live = new LevelChunkTicks<>(List.of(saved));
            int hash = SavedTick.UNIQUE_TICK_HASH.hashCode(saved);
            cursor.set(7L, 65, 9L);
            assertEquals(original, saved.pos());
            assertEquals(hash, SavedTick.UNIQUE_TICK_HASH.hashCode(saved));
            assertTrue(live.hasScheduledTick(original, type));
            assertEquals(original, SavedTick.loadTick(saved.save(value -> "test"), id -> Optional.of(type)).orElseThrow().pos());
            live.unpack(100L);
            ScheduledTick<Object> tick = live.poll();
            assertEquals(original, tick.pos());
            assertEquals(137L, tick.triggerTick());
            assertEquals(TickPriority.HIGH, tick.priority());
            assertEquals(-1L, tick.subTickOrder());
            assertFalse(live.hasScheduledTick(original, type));
        }
    }

    @Test
    void persistedTickAndBlockEntityReadersPreserveFullCoordinates() {
        Object type = new Object();
        BlockPos pos = new BlockPos(Int1088.ONE.shiftLeftSaturated(1024), 64, Int1088.MIN);
        SavedTick<Object> tick = new SavedTick<>(type, pos, 37, TickPriority.HIGH);
        CompoundTag tag = tick.save(value -> "test");
        assertEquals(17, tag.getLongArray("x").length);
        assertEquals(17, tag.getLongArray("z").length);
        assertEquals(tick, SavedTick.loadTick(tag, id -> Optional.of(type)).orElseThrow());
        assertEquals(pos, BlockEntity.getPosFromTag(tag));
        tag.putLongArray("x", new long[16]);
        assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(tag, id -> Optional.of(type)));
        assertThrows(IllegalArgumentException.class, () -> BlockEntity.getPosFromTag(tag));
    }
}
