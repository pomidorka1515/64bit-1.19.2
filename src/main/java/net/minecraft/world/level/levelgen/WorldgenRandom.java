package net.minecraft.world.level.levelgen;

import java.util.function.LongFunction;
import net.minecraft.util.RandomSource;

public class WorldgenRandom extends LegacyRandomSource {
    private final RandomSource randomSource;
    private int count;

    public WorldgenRandom(RandomSource pRandomSource) {
        super(0L);
        this.randomSource = pRandomSource;
    }

    public int getCount() {
        return this.count;
    }

    public RandomSource fork() {
        return this.randomSource.fork();
    }

    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    public int next(int pBits) {
        ++this.count;
        RandomSource randomsource = this.randomSource;
        if (randomsource instanceof LegacyRandomSource legacyrandomsource) {
            return legacyrandomsource.next(pBits);
        } else {
            return (int) (this.randomSource.nextLong() >>> 64 - pBits);
        }
    }

    public synchronized void setSeed(long pSeed) {
        if (this.randomSource != null) {
            this.randomSource.setSeed(pSeed);
        }
    }

    public long setDecorationSeed(long pLevelSeed, long pMinChunkBlockX, long pMinChunkBlockZ) {
        this.setSeed(pLevelSeed);
        long i = this.nextLong() | 1L;
        long j = this.nextLong() | 1L;
        long k = pMinChunkBlockX * i + pMinChunkBlockZ * j ^ pLevelSeed;
        this.setSeed(k);
        return k;
    }

    public void setFeatureSeed(long pDecorationSeed, int pIndex, int pDecorationStep) {
        long i = pDecorationSeed + (long) pIndex + (long) (10000 * pDecorationStep);
        this.setSeed(i);
    }

    public long setDecorationSeed(long seed, net.minecraft.core.Int1088 x, net.minecraft.core.Int1088 z) {
        return setDecorationSeed(seed, net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(x),
                net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(z));
    }

    public void setLargeFeatureSeed(long seed, net.minecraft.core.Int1088 x, net.minecraft.core.Int1088 z) {
        setLargeFeatureSeed(seed, net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(x),
                net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(z));
    }

    public void setLargeFeatureWithSalt(long seed, net.minecraft.core.Int1088 x, net.minecraft.core.Int1088 z, long salt) {
        setLargeFeatureWithSalt(seed, net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(x),
                net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(z), salt);
    }

    public void setLargeFeatureSeed(long pBaseSeed, long pChunkX, long pChunkZ) {
        this.setSeed(pBaseSeed);
        long i = this.nextLong();
        long j = this.nextLong();
        long k = (long) pChunkX * i ^ (long) pChunkZ * j ^ pBaseSeed;
        this.setSeed(k);
    }

    public void setLargeFeatureWithSalt(long pLevelSeed, long pRegionX, long pRegionZ, long pSalt) {
        long i = pRegionX * 341873128712L + pRegionZ * 132897987541L + pLevelSeed + pSalt;
        this.setSeed(i);
    }

    public static RandomSource seedSlimeChunk(long pChunkX, long pChunkZ, long pLevelSeed, long pSalt) {
        return RandomSource.create(pLevelSeed + (long) (pChunkX * pChunkX * 4987142) + (long) (pChunkX * 5947611) + (long) (pChunkZ * pChunkZ) * 4392871L + (long) (pChunkZ * 389711) ^ pSalt);
    }

    public static enum Algorithm {
        LEGACY(LegacyRandomSource::new),
        XOROSHIRO(XoroshiroRandomSource::new);

        private final LongFunction<RandomSource> constructor;

        private Algorithm(LongFunction<RandomSource> p_190082_) {
            this.constructor = p_190082_;
        }

        public RandomSource newInstance(long p_224688_) {
            return this.constructor.apply(p_224688_);
        }
    }
}
