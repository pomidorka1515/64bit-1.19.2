package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;

public interface BiomeResolver {
    default Holder<Biome> getNoiseBiome(long pX, int pY, long pZ, Climate.Sampler pSampler) {
        return this.getNoiseBiome(Int1088.fromLong(pX), pY, Int1088.fromLong(pZ), pSampler);
    }

    Holder<Biome> getNoiseBiome(Int1088 pX, int pY, Int1088 pZ, Climate.Sampler pSampler);
}
