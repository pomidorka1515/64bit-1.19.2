package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;

public abstract class NoiseBasedStateProvider extends BlockStateProvider {
    protected final long seed;
    protected final NormalNoise.NoiseParameters parameters;
    protected final float scale;
    protected final NormalNoise noise;

    protected static <P extends NoiseBasedStateProvider> Products.P3<RecordCodecBuilder.Mu<P>, Long, NormalNoise.NoiseParameters, Float> noiseCodec(RecordCodecBuilder.Instance<P> p_191426_) {
        return p_191426_.group(Codec.LONG.fieldOf("seed").forGetter((p_191435_) -> {
            return p_191435_.seed;
        }),
                NormalNoise.NoiseParameters.DIRECT_CODEC.fieldOf("noise").forGetter((p_191433_) -> {
                    return p_191433_.parameters;
                }),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter((p_191428_) -> {
                    return p_191428_.scale;
                }));
    }

    protected NoiseBasedStateProvider(long pSeed, NormalNoise.NoiseParameters pParameters, float pScale) {
        this.seed = pSeed;
        this.parameters = pParameters;
        this.scale = pScale;
        this.noise = NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(pSeed)), pParameters);
    }

    protected double getNoiseValue(BlockPos pPos, double pDelta) {
        // Keep high limbs until the mode-specific sampling boundary.
        long blockX = pPos.getXLo();
        long blockZ = pPos.getZLo();
        if (FarlandsMode.usesPreciseCoordinates() && (PreciseNoiseCoordinate.needsPrecisePath(pPos.getXHi(), blockX) || PreciseNoiseCoordinate.needsPrecisePath(pPos.getZHi(), blockZ))) {
            return this.noise.getValueScaled(pPos.getXHi(), blockX, pDelta, pPos.getY(), pDelta, pPos.getZHi(), blockZ);
        }

        return this.noise.getValue(FarlandsMode.scaledNoiseCoordinate(pPos.getXHi(), blockX, pDelta), (double) pPos.getY() * pDelta, FarlandsMode.scaledNoiseCoordinate(pPos.getZHi(), blockZ, pDelta));
    }
}
