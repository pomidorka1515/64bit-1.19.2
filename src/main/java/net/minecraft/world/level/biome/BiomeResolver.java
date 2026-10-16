package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;

public interface BiomeResolver {
    Holder<Biome> getNoiseBiome(long pX, int pY, long pZ, Climate.Sampler pSampler);
}
