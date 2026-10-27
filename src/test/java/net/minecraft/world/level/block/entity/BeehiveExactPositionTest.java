package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.Test;

class BeehiveExactPositionTest {
    @Test
    void positionlessWorldgenBeeReceivesFullWidthHiveAnchor() {
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.of(17L, 1L), Int1088.MIN, Int1088.MAX}) {
            CompoundTag bee = new CompoundTag();
            bee.putString("id", "minecraft:bee");
            BlockPos hive = new BlockPos(x, 80, Int1088.MAX);
            BeehiveBlockEntity.setReleasePosition(bee, hive);
            CompoundTag exact = bee.getCompound("ExactPos");
            assertEquals(x, NbtUtils.readInt1088(exact, "BlockX"));
            assertEquals(Int1088.MAX, NbtUtils.readInt1088(exact, "BlockZ"));
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
        BeehiveBlockEntity.setReleasePosition(release, new BlockPos(Int1088.MIN, 100, Int1088.MAX));
        assertTrue(release.contains("ExactPos", 10));
        assertEquals(Int1088.MIN, NbtUtils.readInt1088(release.getCompound("ExactPos"), "BlockX"));
        assertEquals(Int1088.ZERO, NbtUtils.readInt1088(stored.getCompound("ExactPos"), "BlockX"));
    }
}
