package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import net.minecraft.world.level.material.Material;

public class SurfaceSystem {
    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockState defaultBlock;
    private final int seaLevel;
    private final BlockState[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState pRandomState, BlockState pDefaultBlock, int pSeaLevel, PositionalRandomFactory pNoiseRandom) {
        this.defaultBlock = pDefaultBlock;
        this.seaLevel = pSeaLevel;
        this.noiseRandom = pNoiseRandom;
        this.clayBandsOffsetNoise = pRandomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(pNoiseRandom.fromHashOf(new ResourceLocation("clay_bands")));
        this.surfaceNoise = pRandomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = pRandomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = pRandomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = pRandomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = pRandomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = pRandomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = pRandomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = pRandomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    public void buildSurface(RandomState p_224649_, BiomeManager p_224650_, Registry<Biome> p_224651_, boolean p_224652_, WorldGenerationContext p_224653_, final ChunkAccess p_224654_, NoiseChunk p_224655_, SurfaceRules.RuleSource p_224656_) {
        final BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        final ChunkPos chunkpos = p_224654_.getPos();
        LevelHeightAccessor heightAccessor = p_224654_.getHeightAccessorForGeneration();
        int minBuildHeight = heightAccessor.getMinBuildHeight();
        int maxBuildHeight = heightAccessor.getMaxBuildHeight();
        BlockColumn blockcolumn = new BlockColumn() {
            public BlockState getBlock(int p_190006_) {
                return p_224654_.getBlockState(blockpos$mutableblockpos.setY(p_190006_));
            }

            public void setBlock(int p_190008_, BlockState p_190009_) {
                LevelHeightAccessor levelheightaccessor = p_224654_.getHeightAccessorForGeneration();
                if (p_190008_ >= levelheightaccessor.getMinBuildHeight() && p_190008_ < levelheightaccessor.getMaxBuildHeight()) {
                    p_224654_.setBlockState(blockpos$mutableblockpos.setY(p_190008_), p_190009_, false);
                    if (!p_190009_.getFluidState().isEmpty()) {
                        p_224654_.markPosForPostprocessing(blockpos$mutableblockpos);
                    }
                }
            }

            public String toString() {
                return "ChunkBlockColumn " + chunkpos;
            }
        };
        SurfaceRules.Context surfacerules$context = new SurfaceRules.Context(this, p_224649_, p_224654_, p_224655_,
                p_224650_::getBiome, p_224651_, p_224653_);
        SurfaceRules.SurfaceRule surfacerules$surfacerule = p_224656_.apply(surfacerules$context);
        BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; ++k) {
            for (int l = 0; l < 16; ++l) {
                Int1088 i1 = chunkpos.getBlockX(k);
                Int1088 j1 = chunkpos.getBlockZ(l);
                int k1 = clampSurfaceHeight(WorldBounds.addSaturated(p_224654_.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l), 1), minBuildHeight, maxBuildHeight);
                blockpos$mutableblockpos.setX(chunkpos.getBlockX(k)).setZ(chunkpos.getBlockZ(l));
                Holder<Biome> holder = p_224650_.getBiome(blockpos$mutableblockpos1.set(chunkpos.getBlockX(k), p_224652_ ? 0 : k1, chunkpos.getBlockZ(l)));
                if (holder.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockcolumn, i1, j1, k1, p_224654_);
                }

                int l1 = k1;
                surfacerules$context.updateXZ(i1, j1);
                int i2 = 0;
                int j2 = Integer.MIN_VALUE;
                int k2 = Integer.MAX_VALUE;
                int l2 = minBuildHeight;

                // Surface generation must only inspect the finite chunk column.  In
                // particular, never let a corrupt heightmap value become the start
                // of a descending int loop: Integer.MAX_VALUE would otherwise make
                // this worker scan billions of block positions.
                for (int i3 = l1; i3 >= l2; --i3) {
                    BlockState blockstate = blockcolumn.getBlock(i3);
                    if (blockstate.isAir()) {
                        i2 = 0;
                        j2 = Integer.MIN_VALUE;
                    } else if (!blockstate.getFluidState().isEmpty()) {
                        if (j2 == Integer.MIN_VALUE) {
                            j2 = i3 + 1;
                        }
                    } else {
                        if (k2 >= i3) {
                            k2 = l2;

                            // Do not probe the block below the generation range.  The
                            // old l2 - 1 sentinel was harmless with vanilla heights,
                            // but it also made an invalid height start much harder to
                            // contain at the long-coordinate edge.
                            for (int j3 = i3 - 1; j3 >= l2; --j3) {
                                BlockState blockstate1 = blockcolumn.getBlock(j3);
                                if (!this.isStone(blockstate1)) {
                                    k2 = WorldBounds.addSaturated(j3, 1);
                                    break;
                                }
                                if (j3 == l2)
                                    break;
                            }
                        }

                        ++i2;
                        int k3 = Math.max(0, i3 - k2 + 1);
                        surfacerules$context.updateY(i2, k3, j2, i1, i3, j1);
                        if (blockstate == this.defaultBlock) {
                            BlockState blockstate2 = surfacerules$surfacerule.tryApply(i1, i3, j1);
                            if (blockstate2 != null) {
                                blockcolumn.setBlock(i3, blockstate2);
                            }
                        }
                    }
                    if (i3 == l2)
                        break;
                }

                if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(surfacerules$context.getMinSurfaceLevel(), holder.value(), blockcolumn, blockpos$mutableblockpos1, i1, j1, k1, heightAccessor);
                }
            }
        }
    }

    protected int getSurfaceDepth(long p_189928_, long p_189929_) {
        return SurfaceArithmetic.depth(sampleNoise(this.surfaceNoise, p_189928_, 1.0D, p_189929_),
                this.noiseRandom.at(p_189928_, 0, p_189929_).nextDouble());
    }

    protected int getSurfaceDepth(Int1088 x, Int1088 z) {
        if (x.fitsLong() && z.fitsLong())
            return this.getSurfaceDepth(x.limb(0), z.limb(0));
        return SurfaceArithmetic.depth(sampleNoise(this.surfaceNoise, x, 1.0D, z),
                this.noiseRandom.at(x, 0, z).nextDouble());
    }

    protected double getSurfaceSecondary(long p_202190_, long p_202191_) {
        return SurfaceArithmetic.noise(sampleNoise(this.surfaceSecondaryNoise, p_202190_, 1.0D, p_202191_));
    }

    protected double getSurfaceSecondary(Int1088 x, Int1088 z) {
        if (x.fitsLong() && z.fitsLong())
            return this.getSurfaceSecondary(x.limb(0), z.limb(0));
        return SurfaceArithmetic.noise(sampleNoise(this.surfaceSecondaryNoise, x, 1.0D, z));
    }

    private static int clampSurfaceHeight(int height, int minBuildHeight, int maxBuildHeight) {
        return WorldBounds.clampBuildHeight(height, minBuildHeight, maxBuildHeight);
    }

    private boolean isStone(BlockState pState) {
        return !pState.isAir() && pState.getFluidState().isEmpty();
    }

    /** @deprecated */
    @Deprecated
    public Optional<BlockState> topMaterial(SurfaceRules.RuleSource p_189972_, CarvingContext p_189973_, Function<BlockPos, Holder<Biome>> p_189974_, ChunkAccess p_189975_, NoiseChunk p_189976_, BlockPos p_189977_, boolean p_189978_) {
        SurfaceRules.Context surfacerules$context = new SurfaceRules.Context(this, p_189973_.randomState(), p_189975_, p_189976_,
                p_189974_,
                p_189973_.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), p_189973_);
        SurfaceRules.SurfaceRule surfacerules$surfacerule = p_189972_.apply(surfacerules$context);
        Int1088 i = p_189977_.getX();
        int j = p_189977_.getY();
        Int1088 k = p_189977_.getZ();
        surfacerules$context.updateXZ(i, k);
        surfacerules$context.updateY(1, 1, p_189978_ ? WorldBounds.addSaturated(j, 1) : Integer.MIN_VALUE, i, j, k);
        BlockState blockstate = surfacerules$surfacerule.tryApply(i, j, k);
        return Optional.ofNullable(blockstate);
    }

    private void erodedBadlandsExtension(BlockColumn p_189955_, Int1088 p_189956_, Int1088 p_189957_, int p_189958_, LevelHeightAccessor p_189959_) {
        double d0 = 0.2D;
        double d1 = Math.min(Math.abs(WorldBounds.clampNoise(sampleNoise(this.badlandsSurfaceNoise, p_189956_, 1.0D, p_189957_)) * 8.25D), WorldBounds.clampNoise(sampleNoise(this.badlandsPillarNoise, p_189956_, 0.2D, p_189957_)) * 15.0D);
        if (!Double.isFinite(d1))
            return;
        if (!(d1 <= 0.0D)) {
            double d2 = 0.75D;
            double d3 = 1.5D;
            double d4 = Math.abs(WorldBounds.clampNoise(sampleNoise(this.badlandsPillarRoofNoise, p_189956_, 0.75D, p_189957_)) * 1.5D);
            double d5 = 64.0D + Math.min(d1 * d1 * 2.5D, Math.ceil(d4 * 50.0D) + 24.0D);
            int i = WorldBounds.clampBuildHeight(Mth.floor(d5), p_189959_.getMinBuildHeight(), p_189959_.getMaxBuildHeight());
            if (p_189958_ <= i) {
                for (int j = i; j >= p_189959_.getMinBuildHeight(); --j) {
                    BlockState blockstate = p_189955_.getBlock(j);
                    if (blockstate.is(this.defaultBlock.getBlock())) {
                        break;
                    }

                    if (blockstate.is(Blocks.WATER)) {
                        return;
                    }
                    if (j == p_189959_.getMinBuildHeight())
                        break;
                }

                for (int k = i; k >= p_189959_.getMinBuildHeight(); --k) {
                    if (!p_189955_.getBlock(k).isAir())
                        break;
                    p_189955_.setBlock(k, this.defaultBlock);
                    if (k == p_189959_.getMinBuildHeight())
                        break;
                }
            }
        }
    }

    private void frozenOceanExtension(int p_189935_, Biome p_189936_, BlockColumn p_189937_, BlockPos.MutableBlockPos p_189938_, Int1088 p_189939_, Int1088 p_189940_, int p_189941_, LevelHeightAccessor heightAccessor) {
        double d0 = 1.28D;
        double d1 = Math.min(Math.abs(WorldBounds.clampNoise(sampleNoise(this.icebergSurfaceNoise, p_189939_, 1.0D, p_189940_)) * 8.25D), WorldBounds.clampNoise(sampleNoise(this.icebergPillarNoise, p_189939_, 1.28D, p_189940_)) * 15.0D);
        if (!Double.isFinite(d1))
            return;
        if (!(d1 <= 1.8D)) {
            double d3 = 1.17D;
            double d4 = 1.5D;
            double d5 = Math.abs(WorldBounds.clampNoise(sampleNoise(this.icebergPillarRoofNoise, p_189939_, 1.17D, p_189940_)) * 1.5D);
            double d6 = Math.min(d1 * d1 * 1.2D, Math.ceil(d5 * 40.0D) + 14.0D);
            if (!Double.isFinite(d6))
                d6 = 0.0D;
            if (p_189936_.shouldMeltFrozenOceanIcebergSlightly(p_189938_.set(p_189939_, 63, p_189940_))) {
                d6 -= 2.0D;
            }

            double d2;
            if (d6 > 2.0D) {
                d2 = (double) this.seaLevel - d6 - 7.0D;
                d6 += (double) this.seaLevel;
            } else {
                d6 = 0.0D;
                d2 = 0.0D;
            }

            double d7 = d6;
            RandomSource randomsource = this.noiseRandom.at(p_189939_, 0, p_189940_);
            int i = 2 + randomsource.nextInt(4);
            int j = this.seaLevel + 18 + randomsource.nextInt(10);
            int k = 0;

            int minY = heightAccessor.getMinBuildHeight();
            int maxY = heightAccessor.getMaxBuildHeight();
            int start = WorldBounds.clampBuildHeight(Math.max(p_189941_, WorldBounds.addSaturated((int) d6, 1)), minY, maxY);
            int end = WorldBounds.clampBuildHeight(p_189935_, minY, maxY);
            if (start < end)
                return;
            for (int l = start; l >= end; --l) {
                if (p_189937_.getBlock(l).isAir() && l < (int) d7 && randomsource.nextDouble() > 0.01D || p_189937_.getBlock(l).getMaterial() == Material.WATER && l > (int) d2 && l < this.seaLevel && d2 != 0.0D && randomsource.nextDouble() > 0.15D) {
                    if (k <= i && l > j) {
                        p_189937_.setBlock(l, SNOW_BLOCK);
                        ++k;
                    } else {
                        p_189937_.setBlock(l, PACKED_ICE);
                    }
                }
            }
        }
    }

    private static BlockState[] generateBands(RandomSource p_224642_) {
        BlockState[] ablockstate = new BlockState[192];
        Arrays.fill(ablockstate, TERRACOTTA);

        for (int k = 0; k < ablockstate.length; ++k) {
            k += p_224642_.nextInt(5) + 1;
            if (k < ablockstate.length) {
                ablockstate[k] = ORANGE_TERRACOTTA;
            }
        }

        makeBands(p_224642_, ablockstate, 1, YELLOW_TERRACOTTA);
        makeBands(p_224642_, ablockstate, 2, BROWN_TERRACOTTA);
        makeBands(p_224642_, ablockstate, 1, RED_TERRACOTTA);
        int l = p_224642_.nextIntBetweenInclusive(9, 15);
        int i = 0;

        for (int j = 0; i < l && j < ablockstate.length; j += p_224642_.nextInt(16) + 4) {
            ablockstate[j] = WHITE_TERRACOTTA;
            if (j - 1 > 0 && p_224642_.nextBoolean()) {
                ablockstate[j - 1] = LIGHT_GRAY_TERRACOTTA;
            }

            if (j + 1 < ablockstate.length && p_224642_.nextBoolean()) {
                ablockstate[j + 1] = LIGHT_GRAY_TERRACOTTA;
            }

            ++i;
        }

        return ablockstate;
    }

    private static void makeBands(RandomSource p_224644_, BlockState[] p_224645_, int p_224646_, BlockState p_224647_) {
        int i = p_224644_.nextIntBetweenInclusive(6, 15);

        for (int j = 0; j < i; ++j) {
            int k = p_224646_ + p_224644_.nextInt(3);
            int l = p_224644_.nextInt(p_224645_.length);

            for (int i1 = 0; l + i1 < p_224645_.length && i1 < k; ++i1) {
                p_224645_[l + i1] = p_224647_;
            }
        }
    }

    protected BlockState getBand(long p_189931_, int p_189932_, long p_189933_) {
        double offsetNoise = WorldBounds.clampNoise(sampleNoise(this.clayBandsOffsetNoise, p_189931_, 1.0D, p_189933_));
        int i = Double.isFinite(offsetNoise) ? (int) Math.round(offsetNoise * 4.0D) : 0;
        int index = Math.floorMod(WorldBounds.addSaturated(p_189932_, i), this.clayBands.length);
        return this.clayBands[index];
    }

    protected BlockState getBand(Int1088 x, int p_189932_, Int1088 z) {
        if (x.fitsLong() && z.fitsLong())
            return this.getBand(x.limb(0), p_189932_, z.limb(0));
        double offsetNoise = WorldBounds.clampNoise(sampleNoise(this.clayBandsOffsetNoise, x, 1.0D, z));
        int i = Double.isFinite(offsetNoise) ? (int) Math.round(offsetNoise * 4.0D) : 0;
        int index = Math.floorMod(WorldBounds.addSaturated(p_189932_, i), this.clayBands.length);
        return this.clayBands[index];
    }

    static double sampleNoise(NormalNoise noise, Int1088 xValue, double scale, Int1088 zValue) {
        if (xValue.fitsLong() && zValue.fitsLong())
            return sampleNoise(noise, xValue.limb(0), scale, zValue.limb(0));
        if (FarlandsMode.usesPreciseCoordinates())
            return noise.getValueScaled(xValue, scale, 0L, 0.0D, zValue);
        double x = WorldgenCoordinate.toDouble(xValue) * scale;
        double z = WorldgenCoordinate.toDouble(zValue) * scale;
        x = Double.isNaN(x) ? 0.0D : Math.max(-0x1p63, Math.min(Math.nextDown(0x1p63), x));
        z = Double.isNaN(z) ? 0.0D : Math.max(-0x1p63, Math.min(Math.nextDown(0x1p63), z));
        return noise.getValue(x, 0.0D, z);
    }

    private static double sampleNoise(NormalNoise noise, long blockX, double scale, long blockZ) {
        if (FarlandsMode.usesPreciseCoordinates() && (PreciseNoiseCoordinate.needsPrecisePath(blockX) || PreciseNoiseCoordinate.needsPrecisePath(blockZ))) {
            return noise.getValueScaled(blockX, scale, 0L, 0.0D, blockZ);
        }

        return noise.getValue(WorldBounds.scaledNoiseCoordinate(blockX, scale), 0.0D, WorldBounds.scaledNoiseCoordinate(blockZ, scale));
    }
}
