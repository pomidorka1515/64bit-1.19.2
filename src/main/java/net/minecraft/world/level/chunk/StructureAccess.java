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
   StructureStart getStartForStructure(Structure p_223434_);

   void setStartForStructure(Structure p_223437_, StructureStart p_223438_);

   ObjectSet<ChunkPos> getReferencesForStructure(Structure p_223439_);

   void addReferenceForStructure(Structure p_223435_, ChunkPos p_223436_);

   Map<Structure, ObjectSet<ChunkPos>> getAllReferences();

   void setAllReferences(Map<Structure, ObjectSet<ChunkPos>> p_223440_);
}