package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class StructureManager {
   private final LevelAccessor level;
   private final WorldGenSettings worldGenSettings;
   private final StructureCheck structureCheck;

   public StructureManager(LevelAccessor p_220464_, WorldGenSettings p_220465_, StructureCheck p_220466_) {
      this.level = p_220464_;
      this.worldGenSettings = p_220465_;
      this.structureCheck = p_220466_;
   }

   public StructureManager forWorldGenRegion(WorldGenRegion p_220469_) {
      if (p_220469_.getLevel() != this.level) {
         throw new IllegalStateException("Using invalid structure manager (source level: " + p_220469_.getLevel() + ", region: " + p_220469_);
      } else {
         return new StructureManager(p_220469_, this.worldGenSettings, this.structureCheck);
      }
   }

   public List<StructureStart> startsForStructure(ChunkPos p_220478_, Predicate<Structure> p_220479_) {
      if (!WorldBounds.isValidChunk(p_220478_.x, p_220478_.z)) {
         return List.of();
      }
      ChunkAccess referencesChunk = this.getChunkForStructureQuery(p_220478_.x, p_220478_.z, ChunkStatus.STRUCTURE_REFERENCES);
      if (referencesChunk == null) {
         return List.of();
      }
      Map<Structure, ObjectSet<ChunkPos>> map = referencesChunk.getAllReferences();
      ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();

      for(Entry<Structure, ObjectSet<ChunkPos>> entry : map.entrySet()) {
         Structure structure = entry.getKey();
         if (p_220479_.test(structure)) {
            this.fillStartsForStructure(structure, entry.getValue(), builder::add);
         }
      }

      return builder.build();
   }

   public List<StructureStart> startsForStructure(SectionPos p_220505_, Structure p_220506_) {
      if (!WorldBounds.isValidChunk(p_220505_.x(), p_220505_.z())) {
         return List.of();
      }
      ChunkAccess referencesChunk = this.getChunkForStructureQuery(p_220505_.x(), p_220505_.z(), ChunkStatus.STRUCTURE_REFERENCES);
      if (referencesChunk == null) {
         return List.of();
      }
      ObjectSet<ChunkPos> longset = referencesChunk.getReferencesForStructure(p_220506_);
      ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
      this.fillStartsForStructure(p_220506_, longset, builder::add);
      return builder.build();
   }

   public void fillStartsForStructure(Structure p_220481_, ObjectSet<ChunkPos> objectSet, Consumer<StructureStart> p_220483_) {
      for(ChunkPos i : objectSet) {
         if (!WorldBounds.isValidChunk(i.x, i.z)) {
            continue;
         }
         SectionPos sectionpos = SectionPos.of(i, this.level.getMinSection());
         ChunkAccess startsChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_STARTS);
         if (startsChunk == null) {
            continue;
         }
         StructureStart structurestart = this.getStartForStructure(sectionpos, p_220481_, startsChunk);
         if (structurestart != null && structurestart.isValid()) {
            p_220483_.accept(structurestart);
         }
      }

   }

   @Nullable
   public StructureStart getStartForStructure(SectionPos p_220513_, Structure p_220514_, StructureAccess p_220515_) {
      return p_220515_.getStartForStructure(p_220514_);
   }

   public void setStartForStructure(SectionPos p_220517_, Structure p_220518_, StructureStart p_220519_, StructureAccess p_220520_) {
      p_220520_.setStartForStructure(p_220518_, p_220519_);
   }

   public void addReferenceForStructure(SectionPos p_220508_, Structure p_220509_, ChunkPos p_220510_, StructureAccess p_220511_) {
      p_220511_.addReferenceForStructure(p_220509_, p_220510_);
   }

   /**
    * Reads structure metadata without ever synchronously loading a chunk during
    * a normal server tick.  Location/advancement predicates run from the tick
    * thread, so forcing STRUCTURE_REFERENCES here can wait for generation of a
    * chunk which is outside the player's loaded area.  A world-generation
    * region still has to use its bounded cache and may load from that cache.
    */
   @Nullable
   private ChunkAccess getChunkForStructureQuery(long chunkX, long chunkZ, ChunkStatus status) {
      if (!WorldBounds.isValidChunk(chunkX, chunkZ)) {
         return null;
      }
      if (this.level instanceof ServerLevel serverLevel) {
         return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ, status);
      }
      if (this.level instanceof WorldGenRegion region) {
         // WorldGenRegion is a bounded, already-scheduled generation window;
         // retain its normal status guarantee without touching the global
         // server chunk cache.
         return region.getChunk(chunkX, chunkZ, status, true);
      }
      return this.level.getChunk(chunkX, chunkZ, status, true);
   }

   public boolean shouldGenerateStructures() {
      return this.worldGenSettings.generateStructures();
   }

   public StructureStart getStructureAt(BlockPos p_220495_, Structure p_220496_) {
      for(StructureStart structurestart : this.startsForStructure(SectionPos.of(p_220495_), p_220496_)) {
         if (structurestart.getBoundingBox().isInside(p_220495_)) {
            return structurestart;
         }
      }

      return StructureStart.INVALID_START;
   }

   public StructureStart getStructureWithPieceAt(BlockPos p_220489_, ResourceKey<Structure> p_220490_) {
      Structure structure = this.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).get(p_220490_);
      return structure == null ? StructureStart.INVALID_START : this.getStructureWithPieceAt(p_220489_, structure);
   }

   public StructureStart getStructureWithPieceAt(BlockPos p_220492_, TagKey<Structure> p_220493_) {
      Registry<Structure> registry = this.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);

      for(StructureStart structurestart : this.startsForStructure(new ChunkPos(p_220492_), (p_220503_) -> {
         return registry.getHolder(registry.getId(p_220503_)).map((p_220472_) -> {
            return p_220472_.is(p_220493_);
         }).orElse(false);
      })) {
         if (this.structureHasPieceAt(p_220492_, structurestart)) {
            return structurestart;
         }
      }

      return StructureStart.INVALID_START;
   }

   public StructureStart getStructureWithPieceAt(BlockPos p_220525_, Structure p_220526_) {
      for(StructureStart structurestart : this.startsForStructure(SectionPos.of(p_220525_), p_220526_)) {
         if (this.structureHasPieceAt(p_220525_, structurestart)) {
            return structurestart;
         }
      }

      return StructureStart.INVALID_START;
   }

   public boolean structureHasPieceAt(BlockPos p_220498_, StructureStart p_220499_) {
      for(StructurePiece structurepiece : p_220499_.getPieces()) {
         if (structurepiece.getBoundingBox().isInside(p_220498_)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasAnyStructureAt(BlockPos p_220487_) {
      SectionPos sectionpos = SectionPos.of(p_220487_);
      if (!WorldBounds.isValidChunk(sectionpos.x(), sectionpos.z())) {
         return false;
      }
      ChunkAccess referencesChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES);
      return referencesChunk != null && referencesChunk.hasAnyStructureReferences();
   }

   public Map<Structure, ObjectSet<ChunkPos>> getAllStructuresAt(BlockPos p_220523_) {
      SectionPos sectionpos = SectionPos.of(p_220523_);
      if (!WorldBounds.isValidChunk(sectionpos.x(), sectionpos.z())) {
         return Map.of();
      }
      ChunkAccess referencesChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES);
      return referencesChunk == null ? Map.of() : referencesChunk.getAllReferences();
   }

   public StructureCheckResult checkStructurePresence(ChunkPos p_220474_, Structure p_220475_, boolean p_220476_) {
      return this.structureCheck.checkStart(p_220474_, p_220475_, p_220476_);
   }

   public void addReference(StructureStart p_220485_) {
      p_220485_.addReference();
      this.structureCheck.incrementReference(p_220485_.getChunkPos(), p_220485_.getStructure());
   }

   public RegistryAccess registryAccess() {
      return this.level.registryAccess();
   }
}