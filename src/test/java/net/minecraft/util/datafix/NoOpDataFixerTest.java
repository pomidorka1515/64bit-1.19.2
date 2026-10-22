package net.minecraft.util.datafix;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.References;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoOpDataFixerTest {
    @Test
    void returnsOriginalDynamicForEveryDataFixTypeAndVersionDirection() {
        DataFixer fixer = DataFixers.getDataFixer();
        CompoundTag tag = new CompoundTag();
        tag.putString("format", "int128");
        Dynamic<Tag> input = new Dynamic<>(NbtOps.INSTANCE, tag);

        for (DataFixTypes type : DataFixTypes.values()) {
            assertSame(input, fixer.update(type.getType(), input, 0, Integer.MAX_VALUE));
            assertSame(input, fixer.update(type.getType(), input, 100, 100));
            assertSame(input, fixer.update(type.getType(), input, 100, 0));
        }
        assertEquals("int128", tag.getString("format"));
        assertSame(fixer, DataFixers.getDataFixer());
    }

    @Test
    void nbtUpdatesReturnOriginalTagWithoutConsultingFixerOrVersion() {
        CompoundTag tag = new CompoundTag();
        tag.putString("format", "int128");

        assertSame(tag, NbtUtils.update(null, null, tag, 0));
        assertSame(tag, NbtUtils.update(null, null, tag, 0, Integer.MAX_VALUE));
        assertEquals("int128", tag.getString("format"));
    }

    @Test
    void bootstrapDoesNotQueryDataFixerSchemas() {
        assertFalse(SharedConstants.CHECK_DATA_FIXER_SCHEMA);
        assertNull(Util.fetchChoiceType(References.ENTITY_TREE, "minecraft:pig"));
        assertNull(Util.fetchChoiceType(References.BLOCK_ENTITY, "minecraft:chest"));
    }
}
