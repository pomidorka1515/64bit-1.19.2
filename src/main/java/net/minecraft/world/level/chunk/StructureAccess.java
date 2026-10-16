package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public interface StructureAccess {
    @Nullable
    StructureStart getStartForStructure(Structure pStructure);

    void setStartForStructure(Structure pStructure, StructureStart pStructureStart);

    ObjectSet<ChunkPos> getReferencesForStructure(Structure pStructure);

    void addReferenceForStructure(Structure pStructure, ChunkPos pReference);

    Map<Structure, ObjectSet<ChunkPos>> getAllReferences();

    void setAllReferences(Map<Structure, ObjectSet<ChunkPos>> pStructureReferencesMap);
}
