package net.minecraft.data.structures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class StructureUpdater implements SnbtToNbt.Filter {

    public CompoundTag apply(String pStructureLocationPath, CompoundTag pTag) {
        return pStructureLocationPath.startsWith("data/minecraft/structures/") ? update(pStructureLocationPath, pTag) : pTag;
    }

    public static CompoundTag update(String pStructureLocationPath, CompoundTag pTag) {
        StructureTemplate structuretemplate = new StructureTemplate();
        structuretemplate.load(pTag);
        return structuretemplate.save(new CompoundTag());
    }
}
