package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public class RandomSpreadStructurePlacement extends StructurePlacement {
    public static final Codec<RandomSpreadStructurePlacement> CODEC = RecordCodecBuilder.<RandomSpreadStructurePlacement>mapCodec((p_204996_) -> {
                                                                                            return placementCodec(p_204996_).and(p_204996_.group(Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing), Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation), RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType))).apply(p_204996_, RandomSpreadStructurePlacement::new);
                                                                                        })
                                                                              .flatXmap((p_205002_) -> {
                                                                                  return p_205002_.spacing <= p_205002_.separation ? DataResult.error("Spacing has to be larger than separation") : DataResult.success(p_205002_);
                                                                              }, DataResult::success)
                                                                              .codec();
    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;

    public RandomSpreadStructurePlacement(Vec3i p_227000_, StructurePlacement.FrequencyReductionMethod p_227001_, float p_227002_, int p_227003_, Optional<StructurePlacement.ExclusionZone> p_227004_, int p_227005_, int p_227006_, RandomSpreadType p_227007_) {
        super(p_227000_, p_227001_, p_227002_, p_227003_, p_227004_);
        this.spacing = p_227005_;
        this.separation = p_227006_;
        this.spreadType = p_227007_;
    }

    public RandomSpreadStructurePlacement(int pSpacing, int pSeparation, RandomSpreadType pSpreadType, int pSalt) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, pSalt, Optional.empty(), pSpacing, pSeparation, pSpreadType);
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    public ChunkPos getPotentialStructureChunk(long p_227009_, long p_227010_, long p_227011_) {
        return getPotentialStructureChunk(p_227009_, net.minecraft.core.Int1088.fromLong(p_227010_), net.minecraft.core.Int1088.fromLong(p_227011_));
    }

    protected boolean isPlacementChunk(ChunkGenerator pGenerator, RandomState pRandomState, long pSeed, long pX, long pZ) {
        return this.getPotentialStructureChunk(pSeed, pX, pZ).equals(new ChunkPos(pX, pZ));
    }

    public ChunkPos getPotentialStructureChunk(long seed, net.minecraft.core.Int1088 x, net.minecraft.core.Int1088 z) {
        // Divide before selecting the seed path. A single region may straddle
        // the signed-long seam; every chunk in it must use the same region seed.
        net.minecraft.core.Int1088 rx = x.floorDivideSaturated(this.spacing);
        net.minecraft.core.Int1088 rz = z.floorDivideSaturated(this.spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, rx, rz, salt());
        int bound = this.spacing - this.separation;
        int dx = this.spreadType.evaluate(random, bound), dz = this.spreadType.evaluate(random, bound);
        return new ChunkPos(rx.multiplySaturated(this.spacing).addSaturated(net.minecraft.core.Int1088.fromLong(dx)),
                rz.multiplySaturated(this.spacing).addSaturated(net.minecraft.core.Int1088.fromLong(dz)));
    }

    protected boolean isPlacementChunk(ChunkGenerator generator, RandomState random, long seed, net.minecraft.core.Int1088 x, net.minecraft.core.Int1088 z) {
        return getPotentialStructureChunk(seed, x, z).equals(new ChunkPos(x, z));
    }

    public StructurePlacementType<?> type() {
        return StructurePlacementType.RANDOM_SPREAD;
    }
}
