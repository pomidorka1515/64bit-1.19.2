package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class SavedTickInt1088Test {
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
