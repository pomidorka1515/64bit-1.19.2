package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public abstract class StructurePlacement {
    public static final Codec<StructurePlacement> CODEC = Registry.STRUCTURE_PLACEMENT_TYPE.byNameCodec().dispatch(StructurePlacement::type, StructurePlacementType::codec);
    private static final int HIGHLY_ARBITRARY_RANDOM_SALT = 10387320;
    private final Vec3i locateOffset;
    private final StructurePlacement.FrequencyReductionMethod frequencyReductionMethod;
    private final float frequency;
    private final int salt;
    private final Optional<StructurePlacement.ExclusionZone> exclusionZone;

    protected static <S extends StructurePlacement> Products.P5<RecordCodecBuilder.Mu<S>, Vec3i, StructurePlacement.FrequencyReductionMethod, Float, Integer, Optional<StructurePlacement.ExclusionZone>> placementCodec(RecordCodecBuilder.Instance<S> p_227042_) {
        return p_227042_.group(net.minecraft.world.level.levelgen.WorldgenPlacementCodecs.optionalField(Vec3i.offsetCodec(16), "locate_offset", Vec3i.ZERO).forGetter(StructurePlacement::locateOffset), StructurePlacement.FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method", StructurePlacement.FrequencyReductionMethod.DEFAULT).forGetter(StructurePlacement::frequencyReductionMethod), Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(StructurePlacement::frequency), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(StructurePlacement::salt), StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(StructurePlacement::exclusionZone));
    }

    protected StructurePlacement(Vec3i pLocateOffset, StructurePlacement.FrequencyReductionMethod pFrequencyReductionMethod, float pFrequency, int pSalt, Optional<StructurePlacement.ExclusionZone> pExclusionZone) {
        this.locateOffset = pLocateOffset;
        this.frequencyReductionMethod = pFrequencyReductionMethod;
        this.frequency = pFrequency;
        this.salt = pSalt;
        this.exclusionZone = pExclusionZone;
    }

    protected Vec3i locateOffset() {
        return this.locateOffset;
    }

    protected StructurePlacement.FrequencyReductionMethod frequencyReductionMethod() {
        return this.frequencyReductionMethod;
    }

    protected float frequency() {
        return this.frequency;
    }

    protected int salt() {
        return this.salt;
    }

    protected Optional<StructurePlacement.ExclusionZone> exclusionZone() {
        return this.exclusionZone;
    }

    /** Signed-long adapter; exclusion probes may cross into full-width coordinates. */
    public boolean isStructureChunk(ChunkGenerator pGenerator, RandomState pRandomState, long pSeed, long pX, long pZ) {
        return isStructureChunk(pGenerator, pRandomState, pSeed, net.minecraft.core.Int128.fromLong(pX), net.minecraft.core.Int128.fromLong(pZ));
    }

    protected abstract boolean isPlacementChunk(ChunkGenerator pGenerator, RandomState pRandomState, long pSeed, long pX, long pZ);

    public boolean isStructureChunk(ChunkGenerator generator, RandomState random, long seed, net.minecraft.core.Int128 x, net.minecraft.core.Int128 z) {
        if (!isPlacementChunk(generator, random, seed, x, z)) return false;
        long sx = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(x.hi(), x.lo());
        long sz = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(z.hi(), z.lo());
        if (this.frequency < 1.0F && !this.frequencyReductionMethod.shouldGenerate(seed, this.salt, sx, sz, this.frequency)) return false;
        if (this.exclusionZone.isPresent()) {
            ExclusionZone zone = this.exclusionZone.get();
            for (int dx = -zone.chunkCount(); dx <= zone.chunkCount(); ++dx)
                for (int dz = -zone.chunkCount(); dz <= zone.chunkCount(); ++dz)
                    if (zone.otherSet().value().placement().isStructureChunk(generator, random, seed,
                            net.minecraft.world.level.WorldBounds.addChunkOffset(x, dx), net.minecraft.world.level.WorldBounds.addChunkOffset(z, dz))) return false;
        }
        return true;
    }

    protected boolean isPlacementChunk(ChunkGenerator generator, RandomState random, long seed, net.minecraft.core.Int128 x, net.minecraft.core.Int128 z) {
        // Concentric rings are finite and remain centered at the original origin.
        return net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.fitsLong(x.hi(), x.lo())
                && net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.fitsLong(z.hi(), z.lo())
                && isPlacementChunk(generator, random, seed, x.lo(), z.lo());
    }

    public BlockPos getLocatePos(ChunkPos pChunkPos) {
        return (new BlockPos(pChunkPos.getMinBlockX(), 0, pChunkPos.getMinBlockZ())).offset(this.locateOffset());
    }

    public abstract StructurePlacementType<?> type();

    private static boolean probabilityReducer(long p_227034_, int p_227035_, long p_227036_, long p_227037_, float p_227038_) {
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenrandom.setLargeFeatureWithSalt(p_227034_, p_227035_, p_227036_, p_227037_);
        return worldgenrandom.nextFloat() < p_227038_;
    }

    private static boolean legacyProbabilityReducerWithDouble(long p_227049_, int p_227050_, long p_227051_, long p_227052_, float p_227053_) {
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenrandom.setLargeFeatureSeed(p_227049_, p_227051_, p_227052_);
        return worldgenrandom.nextDouble() < (double) p_227053_;
    }

    private static boolean legacyArbitrarySaltProbabilityReducer(long p_227061_, int p_227062_, long p_227063_, long p_227064_, float p_227065_) {
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenrandom.setLargeFeatureWithSalt(p_227061_, p_227063_, p_227064_, 10387320);
        return worldgenrandom.nextFloat() < p_227065_;
    }

    private static boolean legacyPillagerOutpostReducer(long p_227067_, int p_227068_, long p_227069_, long p_227070_, float p_227071_) {
        long i = p_227069_ >> 4;
        long j = p_227070_ >> 4;
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenrandom.setSeed((long) (i ^ j << 4) ^ p_227067_);
        worldgenrandom.nextInt();
        return worldgenrandom.nextInt((int) (1.0F / p_227071_)) == 0;
    }

    /** @deprecated */
    @Deprecated
    public static record ExclusionZone(Holder<StructureSet> otherSet, int chunkCount) {
        public static final Codec<StructurePlacement.ExclusionZone> CODEC = RecordCodecBuilder.create((p_227086_) -> {
            return p_227086_.group(RegistryFileCodec.create(Registry.STRUCTURE_SET_REGISTRY, StructureSet.DIRECT_CODEC, false).fieldOf("other_set").forGetter(StructurePlacement.ExclusionZone::otherSet), Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(StructurePlacement.ExclusionZone::chunkCount)).apply(p_227086_, StructurePlacement.ExclusionZone::new);
        });

        boolean isPlacementForbidden(ChunkGenerator pGenerator, RandomState pRandomState, long pSeed, long pX, long pZ) {
            return pGenerator.hasStructureChunkInRange(this.otherSet, pRandomState, pSeed, pX, pZ, this.chunkCount);
        }
    }

    @FunctionalInterface
    public interface FrequencyReducer {
        boolean shouldGenerate(long p_227099_, int p_227100_, long p_227101_, long p_227102_, float p_227103_);
    }

    public static enum FrequencyReductionMethod implements StringRepresentable {
        DEFAULT("default", StructurePlacement::probabilityReducer),
        LEGACY_TYPE_1("legacy_type_1", StructurePlacement::legacyPillagerOutpostReducer),
        LEGACY_TYPE_2("legacy_type_2", StructurePlacement::legacyArbitrarySaltProbabilityReducer),
        LEGACY_TYPE_3("legacy_type_3", StructurePlacement::legacyProbabilityReducerWithDouble);

        public static final Codec<StructurePlacement.FrequencyReductionMethod> CODEC = StringRepresentable.fromEnum(StructurePlacement.FrequencyReductionMethod::values);
        private final String name;
        private final StructurePlacement.FrequencyReducer reducer;

        private FrequencyReductionMethod(String p_227116_, StructurePlacement.FrequencyReducer p_227117_) {
            this.name = p_227116_;
            this.reducer = p_227117_;
        }

        public boolean shouldGenerate(long p_227120_, int p_227121_, long p_227122_, long p_227123_, float p_227124_) {
            return this.reducer.shouldGenerate(p_227120_, p_227121_, p_227122_, p_227123_, p_227124_);
        }

        public String getSerializedName() {
            return this.name;
        }
    }
}
