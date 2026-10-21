package net.minecraft.world.ticks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class SavedTickInt128Test {
    @Test
    void persistedTickAndBlockEntityReadersPreserveBothCoordinateHalves() {
        Object type = new Object();
        BlockPos pos = new BlockPos(Int128.of(91L, -7L), 64, Int128.MIN_VALUE);
        SavedTick<Object> tick = new SavedTick<>(type, pos, 37, TickPriority.HIGH);
        CompoundTag tag = tick.save(value -> "test");
        assertEquals(tick, SavedTick.loadTick(tag, id -> Optional.of(type)).orElseThrow());
        assertEquals(pos, BlockEntity.getPosFromTag(tag));
        tag.remove("xHi");
        assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(tag, id -> Optional.of(type)));
        assertThrows(IllegalArgumentException.class, () -> BlockEntity.getPosFromTag(tag));
    }
}
