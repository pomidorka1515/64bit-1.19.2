package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.apache.commons.lang3.mutable.MutableObject;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {
    public static final Codec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.create((p_224323_) -> {
        return commonCodec(p_224323_).and(p_224323_.group(RegistryOps.retrieveRegistry(Registry.NOISE_REGISTRY).forGetter((p_188716_) -> {
                                         return p_188716_.noises;
                                     }),
                                                  BiomeSource.CODEC.fieldOf("biome_source").forGetter((p_188711_) -> {
                                                      return p_188711_.biomeSource;
                                                  }),
                                                  NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((p_224278_) -> {
                                                      return p_224278_.settings;
                                                  })))
                .apply(p_224323_, p_224323_.stable(NoiseBasedChunkGenerator::new));
    });
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected final BlockState defaultBlock;
    private final Registry<NormalNoise.NoiseParameters> noises;
    protected final Holder<NoiseGeneratorSettings> settings;
    private final Aquifer.FluidPicker globalFluidPicker;

    public NoiseBasedChunkGenerator(Registry<StructureSet> p_224206_, Registry<NormalNoise.NoiseParameters> p_224207_, BiomeSource p_224208_, Holder<NoiseGeneratorSettings> p_224209_) {
        super(p_224206_, Optional.empty(), p_224208_);
        this.noises = p_224207_;
        this.settings = p_224209_;
        NoiseGeneratorSettings noisegeneratorsettings = this.settings.value();
        this.defaultBlock = noisegeneratorsettings.defaultBlock();
        Aquifer.FluidStatus aquifer$fluidstatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = noisegeneratorsettings.seaLevel();
        Aquifer.FluidStatus aquifer$fluidstatus1 = new Aquifer.FluidStatus(i, noisegeneratorsettings.defaultFluid());
        Aquifer.FluidStatus aquifer$fluidstatus2 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
        this.globalFluidPicker = (p_224274_, p_224275_, p_224276_) -> {
            return p_224275_ < Math.min(-54, i) ? aquifer$fluidstatus : aquifer$fluidstatus1;
        };
    }

    public CompletableFuture<ChunkAccess> createBiomes(Registry<Biome> pBiomeRegistry, Executor pExecutor, RandomState pRandom, Blender pBlender, StructureManager pStructureManager, ChunkAccess pChunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            this.doCreateBiomes(pBlender, pRandom, pStructureManager, pChunk);
            return pChunk;
        }), net.minecraft.world.level.levelgen.synth.GenerationContext.capture(Util.backgroundExecutor()));
    }

    private void doCreateBiomes(Blender pBlender, RandomState pRandom, StructureManager pStructureManager, ChunkAccess pChunk) {
        NoiseChunk noisechunk = pChunk.getOrCreateNoiseChunk((p_224340_) -> {
            return this.createNoiseChunk(p_224340_, pStructureManager, pBlender, pRandom);
        });
        BiomeResolver biomeresolver = BelowZeroRetrogen.getBiomeResolver(pBlender.getBiomeResolver(this.biomeSource), pChunk);
        net.minecraft.world.level.biome.Climate.Sampler sampler = noisechunk.cachedClimateSampler(pRandom.router(), this.settings.value().spawnTarget());
        if (pChunk.getPos().getMinBlockX().hi() != (pChunk.getPos().getMinBlockX().lo() >> 63)
                || pChunk.getPos().getMinBlockZ().hi() != (pChunk.getPos().getMinBlockZ().lo() >> 63)) {
            net.minecraft.core.Int128 qx = pChunk.getPos().getMinBlockX().shiftRight(2);
            net.minecraft.core.Int128 qz = pChunk.getPos().getMinBlockZ().shiftRight(2);
            // New-world generation has no old blending or below-zero retrogen data.
            pChunk.fillBiomesFromNoise((bx, by, bz, s) -> this.biomeSource.getNoiseBiome(
                    qx.add(net.minecraft.core.Int128.fromLong(bx & 3L)), by,
                    qz.add(net.minecraft.core.Int128.fromLong(bz & 3L)), s), sampler);
        } else pChunk.fillBiomesFromNoise(biomeresolver, sampler);
    }

    private NoiseChunk createNoiseChunk(ChunkAccess pChunk, StructureManager pStructureManager, Blender pBlender, RandomState pRandom) {
        return NoiseChunk.forChunk(pChunk, pRandom, Beardifier.forStructuresInChunk(pStructureManager, pChunk.getPos()), this.settings.value(), this.globalFluidPicker, pBlender);
    }

    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> pSettings) {
        return this.settings.is(pSettings);
    }

    public int getBaseHeight(long pX, long pZ, Heightmap.Types pType, LevelHeightAccessor pLevel, RandomState pRandom) {
        return this.iterateNoiseColumn(pLevel, pRandom, pX, pZ, (MutableObject<NoiseColumn>) null, pType.isOpaque()).orElse(pLevel.getMinBuildHeight());
    }

    public NoiseColumn getBaseColumn(long pX, long pZ, LevelHeightAccessor pHeight, RandomState pRandom) {
        MutableObject<NoiseColumn> mutableobject = new MutableObject<>();
        this.iterateNoiseColumn(pHeight, pRandom, pX, pZ, mutableobject, (Predicate<BlockState>) null);
        return mutableobject.getValue();
    }

    public int getBaseHeight(net.minecraft.core.Int128 x, net.minecraft.core.Int128 z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return iterateNoiseColumn(level, random, x, z, null, type.isOpaque()).orElse(level.getMinBuildHeight());
    }

    public NoiseColumn getBaseColumn(net.minecraft.core.Int128 x, net.minecraft.core.Int128 z, LevelHeightAccessor level, RandomState random) {
        MutableObject<NoiseColumn> column = new MutableObject<>();
        iterateNoiseColumn(level, random, x, z, column, null);
        return column.getValue();
    }

    private static volatile boolean wrapRouterDebugInfo;

    /**
    * Toggles the multiline NoiseRouter section shown on the debug screen.
    *
    * @return the newly selected state
    */
    public static boolean toggleWrapRouterDebugInfo() {
        wrapRouterDebugInfo = !wrapRouterDebugInfo;
        return wrapRouterDebugInfo;
    }

    public void addDebugScreenInfo(List<String> pInfo, RandomState pRandom, BlockPos pPos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        NoiseRouter noiserouter = pRandom.router();
        DensityFunction.SinglePointContext densityfunction$singlepointcontext = new DensityFunction.SinglePointContext(pPos.getX(), pPos.getY(), pPos.getZ());
        double d0 = noiserouter.ridges().compute(densityfunction$singlepointcontext);
        double d1 = noiserouter.temperature().compute(densityfunction$singlepointcontext);
        double d2 = noiserouter.vegetation().compute(densityfunction$singlepointcontext);
        double d3 = noiserouter.continents().compute(densityfunction$singlepointcontext);
        double d4 = noiserouter.erosion().compute(densityfunction$singlepointcontext);
        double d5 = noiserouter.depth().compute(densityfunction$singlepointcontext);
        double d6 = NoiseRouterData.peaksAndValleys((float) d0);
        double d7 = noiserouter.initialDensityWithoutJaggedness().compute(densityfunction$singlepointcontext);
        double d8 = noiserouter.finalDensity().compute(densityfunction$singlepointcontext);
        boolean flag = this.stable(NoiseGeneratorSettings.END);
        if (wrapRouterDebugInfo) {
            pInfo.add("(Verbose) NoiseRouter: ");
            pInfo.add("Temperature: " + formatDebugValue(decimalformat, d1, 1.5D));
            pInfo.add("Vegetation: " + formatDebugValue(decimalformat, d2, 1.5D));
            pInfo.add("Continents: " + formatDebugValue(decimalformat, d3, 1.5D));
            pInfo.add("Erosion: " + formatDebugValue(decimalformat, d4, 1.5D));
            pInfo.add("Depth: " + formatDebugValue(decimalformat, d5, 2.5D));
            pInfo.add("Weirdness: " + formatDebugValue(decimalformat, d0, 1.5D));
            pInfo.add("Peaks & Valleys: " + formatDebugValue(decimalformat, d6, 1.0D));
            pInfo.add("Initial Density: " + formatDebugValue(decimalformat, d7, flag ? -25.0D : -64.0D, flag ? 2.0D : 64.0D));
            pInfo.add("Final Density: " + formatDebugValue(decimalformat, d8, 3.0D));
        } else {
            pInfo.add("NoiseRouter");
            pInfo.add("T: " + formatDebugValue(decimalformat, d1, 1.5D) + " V: " + formatDebugValue(decimalformat, d2, 1.5D) + " C: " + formatDebugValue(decimalformat, d3, 1.5D) + " E: " + formatDebugValue(decimalformat, d4, 1.5D) + " D: " + formatDebugValue(decimalformat, d5, 2.5D));
            pInfo.add("W: " + formatDebugValue(decimalformat, d0, 1.5D) + " PV: " + formatDebugValue(decimalformat, d6, 1.0D) + " AS: " + formatDebugValue(decimalformat, d7, flag ? -25.0D : -64.0D, flag ? 2.0D : 64.0D) + " N: " + formatDebugValue(decimalformat, d8, 3.0D));
        }
        DensityFunction.NoiseHolder cheese = debugNoiseHolder(pRandom, Noises.CAVE_CHEESE);
        DensityFunction.NoiseHolder pillar = debugNoiseHolder(pRandom, Noises.PILLAR);
        DensityFunction.NoiseHolder shift = debugNoiseHolder(pRandom, Noises.SHIFT);
        double shiftX = this.settings.value().useLegacyRandomSource() ? 0.0D : new DensityFunctions.ShiftA(shift).compute(densityfunction$singlepointcontext);
        double shiftZ = this.settings.value().useLegacyRandomSource() ? 0.0D : new DensityFunctions.ShiftB(shift).compute(densityfunction$singlepointcontext);
        pInfo.add("cave_cheese: " + BlendedNoise.formatDebugValue(new DensityFunctions.Noise(cheese, 1.0D, 2.0D / 3.0D).compute(densityfunction$singlepointcontext))
                + " pillar: " + BlendedNoise.formatDebugValue(new DensityFunctions.Noise(pillar, 25.0D, 0.3D).compute(densityfunction$singlepointcontext)));
        pInfo.add("Shift X: " + BlendedNoise.formatDebugValue(shiftX)
                + " Shift Z: " + BlendedNoise.formatDebugValue(shiftZ));
        pInfo.add("aquifer_lava: " + BlendedNoise.formatDebugValue(noiserouter.lavaNoise().compute(densityfunction$singlepointcontext))
                + " aquifer_barrier: " + BlendedNoise.formatDebugValue(noiserouter.barrierNoise().compute(densityfunction$singlepointcontext)));
        pInfo.add("surface: " + BlendedNoise.formatDebugValue(SurfaceSystem.sampleNoise(pRandom.getOrCreateNoise(Noises.SURFACE), pPos.getX().hi(), pPos.getX().lo(), 1.0D, pPos.getZ().hi(), pPos.getZ().lo()))
                + " surface_secondary: " + BlendedNoise.formatDebugValue(SurfaceSystem.sampleNoise(pRandom.getOrCreateNoise(Noises.SURFACE_SECONDARY), pPos.getX().hi(), pPos.getX().lo(), 1.0D, pPos.getZ().hi(), pPos.getZ().lo())));
    }

    private static DensityFunction.NoiseHolder debugNoiseHolder(RandomState random, ResourceKey<NormalNoise.NoiseParameters> key) {
        NormalNoise noise = random.getOrCreateNoise(key);
        return new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise);
    }

    private static String formatDebugValue(DecimalFormat p_224307_, double p_224308_, double p_224309_) {
        return formatDebugValue(p_224307_, p_224308_, -p_224309_, p_224309_);
    }

    static String formatDebugValue(DecimalFormat format, double value, double min, double max) {
        if (Double.isFinite(value) && value >= min && value <= max) {
            return format.format(value);
        }
        return ChatFormatting.RED + String.format(java.util.Locale.ROOT, "%.3e", value) + ChatFormatting.RESET;
    }

    private OptionalInt iterateNoiseColumn(LevelHeightAccessor pLevel, RandomState pRandom, long pX, long pZ, @Nullable MutableObject<NoiseColumn> pColumn, @Nullable Predicate<BlockState> pStoppingState) {
        return iterateNoiseColumn(pLevel, pRandom, net.minecraft.core.Int128.fromLong(pX), net.minecraft.core.Int128.fromLong(pZ), pColumn, pStoppingState);
    }

    private OptionalInt iterateNoiseColumn(LevelHeightAccessor pLevel, RandomState pRandom, net.minecraft.core.Int128 x, net.minecraft.core.Int128 z, @Nullable MutableObject<NoiseColumn> pColumn, @Nullable Predicate<BlockState> pStoppingState) {
        long pX = x.lo(), pZ = z.lo();
        NoiseSettings noisesettings = this.settings.value().noiseSettings().clampToHeightAccessor(pLevel);
        int i = noisesettings.getCellHeight();
        int j = noisesettings.minY();
        int k = Mth.intFloorDiv(j, i);
        int l = Mth.intFloorDiv(noisesettings.height(), i);
        if (l <= 0) {
            return OptionalInt.empty();
        } else {
            BlockState[] ablockstate;
            if (pColumn == null) {
                ablockstate = null;
            } else {
                ablockstate = new BlockState[noisesettings.height()];
                pColumn.setValue(new NoiseColumn(j, ablockstate));
            }

            int i1 = noisesettings.getCellWidth();
            long j1 = Math.floorDiv(pX, i1);
            long k1 = Math.floorDiv(pZ, i1);
            int l1 = Math.floorMod(pX, i1);
            int i2 = Math.floorMod(pZ, i1);
            long j2 = j1 * i1;
            long k2 = k1 * i1;
            double d0 = (double) l1 / (double) i1;
            double d1 = (double) i2 / (double) i1;
            int shift = Integer.numberOfTrailingZeros(i1);
            NoiseChunk noisechunk = new NoiseChunk(1, pRandom, x.shiftRight(shift).shiftLeftSaturated(shift), z.shiftRight(shift).shiftLeftSaturated(shift), noisesettings, DensityFunctions.BeardifierMarker.INSTANCE, this.settings.value(), this.globalFluidPicker, Blender.empty());
            noisechunk.initializeForFirstCellX();
            noisechunk.advanceCellX(0);

            for (int l2 = l - 1; l2 >= 0; --l2) {
                noisechunk.selectCellYZ(l2, 0);

                for (int i3 = i - 1; i3 >= 0; --i3) {
                    int j3 = (k + l2) * i + i3;
                    double d2 = (double) i3 / (double) i;
                    noisechunk.updateForY(j3, d2);
                    noisechunk.updateForX(pX, d0);
                    noisechunk.updateForZ(pZ, d1);
                    BlockState blockstate = noisechunk.getInterpolatedState();
                    BlockState blockstate1 = blockstate == null ? this.defaultBlock : blockstate;
                    if (ablockstate != null) {
                        int k3 = l2 * i + i3;
                        ablockstate[k3] = blockstate1;
                    }

                    if (pStoppingState != null && pStoppingState.test(blockstate1)) {
                        noisechunk.stopInterpolation();
                        return OptionalInt.of(j3 + 1);
                    }
                }
            }

            noisechunk.stopInterpolation();
            return OptionalInt.empty();
        }
    }

    public void buildSurface(WorldGenRegion pLevel, StructureManager pStructureManager, RandomState pRandom, ChunkAccess pChunk) {
        if (!SharedConstants.debugVoidTerrain(pChunk.getPos())) {
            WorldGenerationContext worldgenerationcontext = new WorldGenerationContext(this, pLevel);
            this.buildSurface(pChunk, worldgenerationcontext, pRandom, pStructureManager, pLevel.getBiomeManager(), pLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), Blender.of(pLevel));
        }
    }

    @VisibleForTesting
    public void buildSurface(ChunkAccess pChunk, WorldGenerationContext pContext, RandomState pRandom, StructureManager pStructureManager, BiomeManager pBiomeManager, Registry<Biome> pBiomes, Blender pBlender) {
        NoiseChunk noisechunk = pChunk.getOrCreateNoiseChunk((p_224321_) -> {
            return this.createNoiseChunk(p_224321_, pStructureManager, pBlender, pRandom);
        });
        NoiseGeneratorSettings noisegeneratorsettings = this.settings.value();
        pRandom.surfaceSystem().buildSurface(pRandom, pBiomeManager, pBiomes, noisegeneratorsettings.useLegacyRandomSource(), pContext, pChunk, noisechunk, noisegeneratorsettings.surfaceRule());
    }

    public void applyCarvers(WorldGenRegion pLevel, long pSeed, RandomState pRandom, BiomeManager pBiomeManager, StructureManager pStructureManager, ChunkAccess pChunk, GenerationStep.Carving pStep) {
        BiomeManager biomemanager = pBiomeManager.withDifferentSource(new BiomeManager.NoiseBiomeSource() {
            public Holder<Biome> getNoiseBiome(long x, int y, long z) {
                return NoiseBasedChunkGenerator.this.biomeSource.getNoiseBiome(x, y, z, pRandom.sampler());
            }

            public Holder<Biome> getNoiseBiome(net.minecraft.core.Int128 x, int y, net.minecraft.core.Int128 z) {
                return NoiseBasedChunkGenerator.this.biomeSource.getNoiseBiome(x, y, z, pRandom.sampler());
            }
        });
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int i = 8;
        ChunkPos chunkpos = pChunk.getPos();
        NoiseChunk noisechunk = pChunk.getOrCreateNoiseChunk((p_224250_) -> {
            return this.createNoiseChunk(p_224250_, pStructureManager, Blender.of(pLevel), pRandom);
        });
        Aquifer aquifer = noisechunk.aquifer();
        CarvingContext carvingcontext = new CarvingContext(this, pLevel.registryAccess(), pChunk.getHeightAccessorForGeneration(), noisechunk, pRandom, this.settings.value().surfaceRule());
        CarvingMask carvingmask = ((ProtoChunk) pChunk).getOrCreateCarvingMask(pStep);

        for (int j = -8; j <= 8; ++j) {
            for (int k = -8; k <= 8; ++k) {
                ChunkPos chunkpos1 = new ChunkPos(WorldBounds.addChunkOffset(chunkpos.x(), j), WorldBounds.addChunkOffset(chunkpos.z(), k));
                ChunkAccess chunkaccess = pLevel.getChunk(chunkpos1.x(), chunkpos1.z());
                BiomeGenerationSettings biomegenerationsettings = chunkaccess.carverBiome(() -> {
                    return this.getBiomeGenerationSettings(this.biomeSource.getNoiseBiome(chunkpos1.getMinBlockX().shiftRight(2), 0, chunkpos1.getMinBlockZ().shiftRight(2), pRandom.sampler()));
                });
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers(pStep);
                int l = 0;

                for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
                    ConfiguredWorldCarver<?> configuredworldcarver = holder.value();
                    worldgenrandom.setLargeFeatureSeed(pSeed + (long) l, chunkpos1.x(), chunkpos1.z());
                    if (configuredworldcarver.isStartChunk(worldgenrandom)) {
                        configuredworldcarver.carve(carvingcontext, pChunk, biomemanager::getBiome, worldgenrandom, aquifer, chunkpos1, carvingmask);
                    }

                    ++l;
                }
            }
        }
    }

    public CompletableFuture<ChunkAccess> fillFromNoise(Executor pExecutor, Blender pBlender, RandomState pRandom, StructureManager pStructureManager, ChunkAccess pChunk) {
        NoiseSettings noisesettings = this.settings.value().noiseSettings().clampToHeightAccessor(pChunk.getHeightAccessorForGeneration());
        int i = noisesettings.minY();
        int j = Mth.intFloorDiv(i, noisesettings.getCellHeight());
        int k = Mth.intFloorDiv(noisesettings.height(), noisesettings.getCellHeight());
        if (k <= 0) {
            return CompletableFuture.completedFuture(pChunk);
        } else {
            int l = pChunk.getSectionIndex(k * noisesettings.getCellHeight() - 1 + i);
            int i1 = pChunk.getSectionIndex(i);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int j1 = l; j1 >= i1; --j1) {
                LevelChunkSection levelchunksection = pChunk.getSection(j1);
                levelchunksection.acquire();
                set.add(levelchunksection);
            }

            return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> {
                                        return this.doFill(pBlender, pStructureManager, pRandom, pChunk, j, k);
                                    }), net.minecraft.world.level.levelgen.synth.GenerationContext.capture(Util.backgroundExecutor())).whenCompleteAsync((p_224309_, p_224310_) -> {
                for (LevelChunkSection levelchunksection1 : set) {
                    levelchunksection1.release();
                }
            }, pExecutor);
        }
    }

    private ChunkAccess doFill(Blender pBlender, StructureManager pStructureManager, RandomState pRandom, ChunkAccess pChunk, int pMinCellY, int pCellCountY) {
        NoiseChunk noisechunk = pChunk.getOrCreateNoiseChunk((p_224255_) -> {
            return this.createNoiseChunk(p_224255_, pStructureManager, pBlender, pRandom);
        });
        Heightmap heightmap = pChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = pChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkpos = pChunk.getPos();
        long i = chunkpos.getMinBlockX().lo();
        long j = chunkpos.getMinBlockZ().lo();
        Aquifer aquifer = noisechunk.aquifer();
        noisechunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        int k = noisechunk.cellWidth();
        int l = noisechunk.cellHeight();
        int i1 = 16 / k;
        int j1 = 16 / k;

        for (int k1 = 0; k1 < i1; ++k1) {
            noisechunk.advanceCellX(k1);

            for (int l1 = 0; l1 < j1; ++l1) {
                LevelChunkSection levelchunksection = pChunk.getSection(pChunk.getSectionsCount() - 1);

                for (int i2 = pCellCountY - 1; i2 >= 0; --i2) {
                    noisechunk.selectCellYZ(i2, l1);

                    for (int j2 = l - 1; j2 >= 0; --j2) {
                        int k2 = (pMinCellY + i2) * l + j2;
                        int l2 = k2 & 15;
                        int i3 = pChunk.getSectionIndex(k2);
                        if (pChunk.getSectionIndex(levelchunksection.bottomBlockY()) != i3) {
                            levelchunksection = pChunk.getSection(i3);
                        }

                        double d0 = (double) j2 / (double) l;
                        noisechunk.updateForY(k2, d0);

                        for (int j3 = 0; j3 < k; ++j3) {
                            long k3 = WorldBounds.addBlockOffset(i, k1 * k + j3);
                            int l3 = (int) (k3 & 15);
                            double d1 = (double) j3 / (double) k;
                            noisechunk.updateForX(k3, d1);

                            for (int i4 = 0; i4 < k; ++i4) {
                                long j4 = WorldBounds.addBlockOffset(j, l1 * k + i4);
                                int k4 = (int) (j4 & 15);
                                double d2 = (double) i4 / (double) k;
                                noisechunk.updateForZ(j4, d2);
                                BlockState blockstate = noisechunk.getInterpolatedState();
                                if (blockstate == null) {
                                    blockstate = this.defaultBlock;
                                }

                                blockstate = this.debugPreliminarySurfaceLevel(noisechunk, k3, k2, j4, blockstate);
                                if (blockstate != AIR && !SharedConstants.debugVoidTerrain(pChunk.getPos())) {
                                    if (blockstate.getLightEmission() != 0 && pChunk instanceof ProtoChunk) {
                                        blockpos$mutableblockpos.set(chunkpos.getBlockX(k1 * k + j3), k2, chunkpos.getBlockZ(l1 * k + i4));
                                        ((ProtoChunk) pChunk).addLight(blockpos$mutableblockpos);
                                    }

                                    levelchunksection.setBlockState(l3, l2, k4, blockstate, false);
                                    heightmap.update(l3, k2, k4, blockstate);
                                    heightmap1.update(l3, k2, k4, blockstate);
                                    if (aquifer.shouldScheduleFluidUpdate() && !blockstate.getFluidState().isEmpty()) {
                                        blockpos$mutableblockpos.set(chunkpos.getBlockX(k1 * k + j3), k2, chunkpos.getBlockZ(l1 * k + i4));
                                        pChunk.markPosForPostprocessing(blockpos$mutableblockpos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noisechunk.swapSlices();
        }

        noisechunk.stopInterpolation();
        return pChunk;
    }

    private BlockState debugPreliminarySurfaceLevel(NoiseChunk pChunk, long pX, int pY, long pZ, BlockState pState) {
        return pState;
    }

    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    public int getSeaLevel() {
        return this.settings.value().seaLevel();
    }

    public int getMinY() {
        return this.settings.value().noiseSettings().minY();
    }

    public void spawnOriginalMobs(WorldGenRegion pLevel) {
        if (!this.settings.value().disableMobGeneration()) {
            ChunkPos chunkpos = pLevel.getCenter();
            Holder<Biome> holder = pLevel.getBiome(chunkpos.getWorldPosition().atY(pLevel.getMaxBuildHeight() - 1));
            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            worldgenrandom.setDecorationSeed(pLevel.getSeed(), chunkpos.getMinBlockX(), chunkpos.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(pLevel, holder, chunkpos, worldgenrandom);
        }
    }
}
