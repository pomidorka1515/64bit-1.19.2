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

    public StructureManager(LevelAccessor pLevel, WorldGenSettings pWorldGenSettings, StructureCheck pStructureCheck) {
        this.level = pLevel;
        this.worldGenSettings = pWorldGenSettings;
        this.structureCheck = pStructureCheck;
    }

    public StructureManager forWorldGenRegion(WorldGenRegion pRegion) {
        if (pRegion.getLevel() != this.level) {
            throw new IllegalStateException("Using invalid structure manager (source level: " + pRegion.getLevel() + ", region: " + pRegion);
        } else {
            return new StructureManager(pRegion, this.worldGenSettings, this.structureCheck);
        }
    }

    public List<StructureStart> startsForStructure(ChunkPos pChunkPos, Predicate<Structure> pStructurePredicate) {
        if (!WorldBounds.isValidChunk(pChunkPos.x, pChunkPos.z)) {
            return List.of();
        }
        ChunkAccess referencesChunk = this.getChunkForStructureQuery(pChunkPos.x, pChunkPos.z, ChunkStatus.STRUCTURE_REFERENCES);
        if (referencesChunk == null) {
            return List.of();
        }
        Map<Structure, ObjectSet<ChunkPos>> map = referencesChunk.getAllReferences();
        ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();

        for (Entry<Structure, ObjectSet<ChunkPos>> entry : map.entrySet()) {
            Structure structure = entry.getKey();
            if (pStructurePredicate.test(structure)) {
                this.fillStartsForStructure(structure, entry.getValue(), builder::add);
            }
        }

        return builder.build();
    }

    public List<StructureStart> startsForStructure(SectionPos pSectionPos, Structure pStructure) {
        if (!WorldBounds.isValidChunk(pSectionPos.x(), pSectionPos.z())) {
            return List.of();
        }
        ChunkAccess referencesChunk = this.getChunkForStructureQuery(pSectionPos.x(), pSectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
        if (referencesChunk == null) {
            return List.of();
        }
        ObjectSet<ChunkPos> longset = referencesChunk.getReferencesForStructure(pStructure);
        ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
        this.fillStartsForStructure(pStructure, longset, builder::add);
        return builder.build();
    }

    public void fillStartsForStructure(Structure pStructure, ObjectSet<ChunkPos> objectSet, Consumer<StructureStart> pStartConsumer) {
        for (ChunkPos i : objectSet) {
            if (!WorldBounds.isValidChunk(i.x, i.z)) {
                continue;
            }
            SectionPos sectionpos = SectionPos.of(i, this.level.getMinSection());
            ChunkAccess startsChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_STARTS);
            if (startsChunk == null) {
                continue;
            }
            StructureStart structurestart = this.getStartForStructure(sectionpos, pStructure, startsChunk);
            if (structurestart != null && structurestart.isValid()) {
                pStartConsumer.accept(structurestart);
            }
        }
    }

    @Nullable
    public StructureStart getStartForStructure(SectionPos pSectionPos, Structure pStructure, StructureAccess pStructureAccess) {
        return pStructureAccess.getStartForStructure(pStructure);
    }

    public void setStartForStructure(SectionPos pSectionPos, Structure pStructure, StructureStart pStructureStart, StructureAccess pStructureAccess) {
        pStructureAccess.setStartForStructure(pStructure, pStructureStart);
    }

    public void addReferenceForStructure(SectionPos pSectionPos, Structure pStructure, ChunkPos pReference, StructureAccess pStructureAccess) {
        pStructureAccess.addReferenceForStructure(pStructure, pReference);
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

    public StructureStart getStructureAt(BlockPos pPos, Structure pStructure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(pPos), pStructure)) {
            if (structurestart.getBoundingBox().isInside(pPos)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pPos, ResourceKey<Structure> pStructureKey) {
        Structure structure = this.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).get(pStructureKey);
        return structure == null ? StructureStart.INVALID_START : this.getStructureWithPieceAt(pPos, structure);
    }

    public StructureStart getStructureWithPieceAt(BlockPos pPos, TagKey<Structure> pStructureTag) {
        Registry<Structure> registry = this.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);

        for (StructureStart structurestart : this.startsForStructure(new ChunkPos(pPos), (p_220503_) -> {
                 return registry.getHolder(registry.getId(p_220503_)).map((p_220472_) -> {
                                                                         return p_220472_.is(pStructureTag);
                                                                     })
                         .orElse(false);
             })) {
            if (this.structureHasPieceAt(pPos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pPos, Structure pStructure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(pPos), pStructure)) {
            if (this.structureHasPieceAt(pPos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public boolean structureHasPieceAt(BlockPos pPos, StructureStart pStructureStart) {
        for (StructurePiece structurepiece : pStructureStart.getPieces()) {
            if (structurepiece.getBoundingBox().isInside(pPos)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyStructureAt(BlockPos pPos) {
        SectionPos sectionpos = SectionPos.of(pPos);
        if (!WorldBounds.isValidChunk(sectionpos.x(), sectionpos.z())) {
            return false;
        }
        ChunkAccess referencesChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES);
        return referencesChunk != null && referencesChunk.hasAnyStructureReferences();
    }

    public Map<Structure, ObjectSet<ChunkPos>> getAllStructuresAt(BlockPos pPos) {
        SectionPos sectionpos = SectionPos.of(pPos);
        if (!WorldBounds.isValidChunk(sectionpos.x(), sectionpos.z())) {
            return Map.of();
        }
        ChunkAccess referencesChunk = this.getChunkForStructureQuery(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES);
        return referencesChunk == null ? Map.of() : referencesChunk.getAllReferences();
    }

    public StructureCheckResult checkStructurePresence(ChunkPos pChunkPos, Structure pStructure, boolean pSkipKnownStructures) {
        return this.structureCheck.checkStart(pChunkPos, pStructure, pSkipKnownStructures);
    }

    public void addReference(StructureStart pStructureStart) {
        pStructureStart.addReference();
        this.structureCheck.incrementReference(pStructureStart.getChunkPos(), pStructureStart.getStructure());
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }
}
