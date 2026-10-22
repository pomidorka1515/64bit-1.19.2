package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.Test;

class BeehiveExactPositionTest {
    @Test
    void positionlessWorldgenBeeReceivesFullWidthHiveAnchor() {
        for (Int128 x : new Int128[] {Int128.ZERO, Int128.of(1L, 17L), Int128.MIN, Int128.MAX}) {
            CompoundTag bee = new CompoundTag();
            bee.putString("id", "minecraft:bee");
            BlockPos hive = new BlockPos(x, 80, Int128.MAX);
            BeehiveBlockEntity.setReleasePosition(bee, hive);
            CompoundTag exact = bee.getCompound("ExactPos");
            assertEquals(x, NbtUtils.readInt128(exact, "BlockX"));
            assertEquals(Int128.MAX, NbtUtils.readInt128(exact, "BlockZ"));
            assertEquals(0.5D, exact.getDouble("SubX"));
            assertEquals(0.5D, exact.getDouble("SubZ"));
            assertEquals(80.0D, exact.getDouble("Y"));
            assertEquals("minecraft:bee", bee.getString("id"));
        }
    }

    @Test
    void oldOccupantPositionIsReplacedWithoutMutatingStoredTemplate() {
        CompoundTag stored = new CompoundTag();
        BeehiveBlockEntity.setReleasePosition(stored, new BlockPos(0, 0, 0));
        CompoundTag release = stored.copy();
        BeehiveBlockEntity.removeIgnoredBeeTags(release);
        BeehiveBlockEntity.setReleasePosition(release, new BlockPos(Int128.MIN, 100, Int128.MAX));
        assertTrue(release.contains("ExactPos", 10));
        assertEquals(Int128.MIN, NbtUtils.readInt128(release.getCompound("ExactPos"), "BlockX"));
        assertEquals(Int128.ZERO, NbtUtils.readInt128(stored.getCompound("ExactPos"), "BlockX"));
    }
}
