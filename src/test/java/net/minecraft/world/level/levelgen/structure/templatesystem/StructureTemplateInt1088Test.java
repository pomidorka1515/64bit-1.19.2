package net.minecraft.world.level.levelgen.structure.templatesystem;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class StructureTemplateInt1088Test {
    @Test
    void transformsKeepFullWidthCoordinates() {
        Int1088 x = Int1088.of(Long.MAX_VALUE, 42L);
        Int1088 z = Int1088.of(Long.MIN_VALUE, -17L);
        BlockPos position = new BlockPos(x, 12, z);
        assertEquals(position, StructureTemplate.transform(position, Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
        assertEquals(new BlockPos(Int1088.ZERO.subtractSaturated(z), 12, x),
                StructureTemplate.transform(position, Mirror.NONE, Rotation.CLOCKWISE_90, BlockPos.ZERO));
        assertEquals(new BlockPos(x, 12, Int1088.ZERO.subtractSaturated(z)),
                StructureTemplate.transform(position, Mirror.LEFT_RIGHT, Rotation.NONE, BlockPos.ZERO));
    }

    @Test
    void localTemplateDataLoadsIntAndLongListsWithoutNarrowing() {
        for (boolean longs : new boolean[] {false, true}) {
            CompoundTag tag = new CompoundTag();
            ListTag size = new ListTag();
            for (int value : new int[] {17, 3, 29}) {
                size.add(longs ? LongTag.valueOf(value) : IntTag.valueOf(value));
            }
            tag.put("size", size);
            StructureTemplate template = new StructureTemplate();
            template.load(tag);
            assertEquals(new BlockPos(17, 3, 29), new BlockPos(template.getSize()));
        }
    }

    @Test
    void oversizedLocalCoordinatesAreRejected() {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(LongTag.valueOf(1L << 40));
        size.add(LongTag.valueOf(1));
        size.add(LongTag.valueOf(1));
        tag.put("size", size);
        assertThrows(ArithmeticException.class, () -> new StructureTemplate().load(tag));
    }
}
