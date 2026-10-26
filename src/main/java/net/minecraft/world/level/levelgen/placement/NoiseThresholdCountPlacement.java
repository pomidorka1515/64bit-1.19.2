package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;

public class NoiseThresholdCountPlacement extends RepeatingPlacement {
    public static final Codec<NoiseThresholdCountPlacement> CODEC = RecordCodecBuilder.create((p_191761_) -> {
        return p_191761_.group(Codec.DOUBLE.fieldOf("noise_level").forGetter((p_191771_) -> {
                            return p_191771_.noiseLevel;
                        }),
                                Codec.INT.fieldOf("below_noise").forGetter((p_191769_) -> {
                                    return p_191769_.belowNoise;
                                }),
                                Codec.INT.fieldOf("above_noise").forGetter((p_191763_) -> {
                                    return p_191763_.aboveNoise;
                                }))
                .apply(p_191761_, NoiseThresholdCountPlacement::new);
    });
    private final double noiseLevel;
    private final int belowNoise;
    private final int aboveNoise;

    private NoiseThresholdCountPlacement(double p_191753_, int p_191754_, int p_191755_) {
        this.noiseLevel = p_191753_;
        this.belowNoise = p_191754_;
        this.aboveNoise = p_191755_;
    }

    public static NoiseThresholdCountPlacement of(double pNoiseLevel, int pBelowNoise, int pAboveNoise) {
        return new NoiseThresholdCountPlacement(pNoiseLevel, pBelowNoise, pAboveNoise);
    }

    protected int count(RandomSource pRandom, BlockPos pPos) {
        double d0 = FarlandsMode.usesPreciseCoordinates()
                ? Biome.BIOME_INFO_NOISE.getValueScaled(pPos.getXHi(), pPos.getXLo(), pPos.getZHi(), pPos.getZLo(), 1.0D / 200.0D, false)
                : Biome.BIOME_INFO_NOISE.getValue(FarlandsMode.scaledNoiseCoordinate(pPos.getXHi(), pPos.getXLo(), 1.0D / 200.0D), FarlandsMode.scaledNoiseCoordinate(pPos.getZHi(), pPos.getZLo(), 1.0D / 200.0D), false);
        return d0 < this.noiseLevel ? this.belowNoise : this.aboveNoise;
    }

    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_THRESHOLD_COUNT;
    }
}
