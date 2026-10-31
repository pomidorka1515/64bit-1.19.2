package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import net.minecraft.world.level.levelgen.synth.NoiseSampling;

public interface Aquifer {
    static Aquifer create(NoiseChunk pChunk, ChunkPos pChunkPos, NoiseRouter pNoiseRouter, PositionalRandomFactory pPositionalRandomFactory, int pMinY, int pHeight, Aquifer.FluidPicker pGlobalFluidPicker) {
        return new Aquifer.NoiseBasedAquifer(pChunk, pChunkPos, pNoiseRouter, pPositionalRandomFactory, pMinY, pHeight, pGlobalFluidPicker);
    }

    static Aquifer createDisabled(final Aquifer.FluidPicker pDefaultFluid) {
        return new Aquifer() {
            @Nullable
            public BlockState computeSubstance(DensityFunction.FunctionContext p_208172_, double p_208173_) {
                if (p_208173_ > 0.0D) return null;
                return pDefaultFluid.computeFluid(coordinate(p_208172_, true), p_208172_.blockY(), coordinate(p_208172_, false)).at(p_208172_.blockY());
            }

            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Nullable
    BlockState computeSubstance(DensityFunction.FunctionContext pContext, double pSubstance);

    boolean shouldScheduleFluidUpdate();

    public interface FluidPicker {
        Aquifer.FluidStatus computeFluid(Int1088 pX, int pY, Int1088 pZ);

        default Aquifer.FluidStatus computeFluid(long x, int y, long z) {
            return computeFluid(Int1088.fromLong(x), y, Int1088.fromLong(z));
        }
    }

    private static Int1088 coordinate(DensityFunction.FunctionContext context, boolean x) {
        return ContextCoordinates.copy(context, x);
    }

    public static final class FluidStatus {
        final int fluidLevel;
        final BlockState fluidType;

        public FluidStatus(int pFluidLevel, BlockState pFluidType) {
            this.fluidLevel = pFluidLevel;
            this.fluidType = pFluidType;
        }

        public BlockState at(int pY) {
            // The configured sea level is inclusive: an ocean may occupy the
            // block at sea level itself, not only the blocks below it.
            return pY <= this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
        }
    }

    public static class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final Aquifer.FluidStatus[] aquiferCache;
        private final java.util.Set<Aquifer.FluidStatus> extrapolatedWater = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        // Centers are bounded chunk-local coordinates, not world positions.
        private final int[] centerX;
        private final int[] centerY;
        private final int[] centerZ;
        // Like interpolation and fluid-update state, this scratch is chunk-thread confined.
        private double pressureNoise;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final Int1088 originX;
        private final Int1088 originZ;
        private final Int1088 originGridX;
        private final Int1088 originGridZ;
        private final Int1088[] columnX = new Int1088[16];
        private final Int1088[] columnZ = new Int1088[16];
        private final Int1088[] localX = new Int1088[16];
        private final Int1088[] localZ = new Int1088[16];
        private final long minGridX;
        private final int minGridY;
        private final long minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][] {{-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}};

        NoiseBasedAquifer(NoiseChunk pNoiseChunk, ChunkPos pChunkPos, NoiseRouter pNoiseRouter, PositionalRandomFactory pPositionalRandomFactory, int pMinY, int pHeight, Aquifer.FluidPicker pGlobalFluidPicker) {
            this.noiseChunk = pNoiseChunk;
            this.barrierNoise = pNoiseRouter.barrierNoise();
            this.fluidLevelFloodednessNoise = pNoiseRouter.fluidLevelFloodednessNoise();
            this.fluidLevelSpreadNoise = pNoiseRouter.fluidLevelSpreadNoise();
            this.lavaNoise = pNoiseRouter.lavaNoise();
            this.erosion = pNoiseRouter.erosion();
            this.depth = pNoiseRouter.depth();
            this.positionalRandomFactory = pPositionalRandomFactory;
            this.originX = pChunkPos.getMinBlockX();
            this.originZ = pChunkPos.getMinBlockZ();
            this.originGridX = this.originX.shiftRight(4);
            this.originGridZ = this.originZ.shiftRight(4);
            for (int offset = 0; offset < 16; ++offset) {
                this.columnX[offset] = pChunkPos.getBlockX(offset);
                this.columnZ[offset] = pChunkPos.getBlockZ(offset);
                this.localX[offset] = this.columnX[offset];
                this.localZ[offset] = this.columnZ[offset];
            }
            // Geometry is chunk-local; noise, random and picker inputs stay absolute.
            long minGridBlockX = 0L;
            long maxGridBlockX = 15L;
            long minGridBlockZ = 0L;
            long maxGridBlockZ = 15L;
            this.minGridX = WorldBounds.addChunkOffset(this.gridX(minGridBlockX), -1L);
            this.globalFluidPicker = pGlobalFluidPicker;
            long i = WorldBounds.addChunkOffset(this.gridX(maxGridBlockX), 1L);
            this.gridSizeX = (int) (WorldBounds.signedDifferenceAsLong(i, this.minGridX) + 1L);
            this.minGridY = this.gridY(pMinY) - 1;
            int j = this.gridY(pMinY + pHeight) + 1;
            int k = j - this.minGridY + 1;
            this.minGridZ = WorldBounds.addChunkOffset(this.gridZ(minGridBlockZ), -1L);
            long l = WorldBounds.addChunkOffset(this.gridZ(maxGridBlockZ), 1L);
            this.gridSizeZ = (int) (WorldBounds.signedDifferenceAsLong(l, this.minGridZ) + 1L);
            int i1 = this.gridSizeX * k * this.gridSizeZ;
            this.aquiferCache = new Aquifer.FluidStatus[i1];
            this.centerX = new int[i1];
            this.centerY = new int[i1];
            this.centerZ = new int[i1];
            Arrays.fill(this.centerY, Integer.MIN_VALUE);
        }

        private int getIndex(long pGridX, int pGridY, long pGridZ) {
            long i = pGridX - this.minGridX;
            int j = pGridY - this.minGridY;
            long k = pGridZ - this.minGridZ;
            return (int) ((j * this.gridSizeZ + k) * this.gridSizeX + i);
        }

        @Nullable
        public BlockState computeSubstance(DensityFunction.FunctionContext pContext, double pSubstance) {
            if (pSubstance > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                Int1088 globalX = this.columnCoordinate(pContext, true);
                int j = pContext.blockY();
                Int1088 globalZ = this.columnCoordinate(pContext, false);
                // Full-width geometry stays local; noise/random/surface sampling translate to global limbs.
                long i = this.localCoordinate(globalX, true);
                long k = this.localCoordinate(globalZ, false);
                Aquifer.FluidStatus aquifer$fluidstatus = this.globalFluidPicker.computeFluid(globalX, j, globalZ);
                if (aquifer$fluidstatus.at(j).is(Blocks.LAVA)) {
                    this.shouldScheduleFluidUpdate = false;
                    return Blocks.LAVA.defaultBlockState();
                } else {
                    long l = this.gridX(WorldBounds.subtractBlockOffset(i, 5L));
                    int i1 = Math.floorDiv(j + 1, 12);
                    long j1 = this.gridZ(WorldBounds.subtractBlockOffset(k, 5L));
                    double k1 = Double.POSITIVE_INFINITY;
                    double l1 = Double.POSITIVE_INFINITY;
                    double i2 = Double.POSITIVE_INFINITY;
                    int j2 = -1;
                    int k2 = -1;
                    int l2 = -1;

                    for (int i3 = 0; i3 <= 1; ++i3) {
                        for (int j3 = -1; j3 <= 1; ++j3) {
                            for (int k3 = 0; k3 <= 1; ++k3) {
                                long l3 = WorldBounds.addChunkOffset(l, i3);
                                int i4 = i1 + j3;
                                long j4 = WorldBounds.addChunkOffset(j1, k3);
                                int k4 = this.getIndex(l3, i4, j4);
                                if (this.centerY[k4] == Integer.MIN_VALUE) {
                                    RandomSource randomsource = this.positionalRandomFactory.at(WorldBounds.addChunkOffset(this.originGridX, l3), i4, WorldBounds.addChunkOffset(this.originGridZ, j4));
                                    this.centerX[k4] = (int) (l3 * 16 + randomsource.nextInt(10));
                                    this.centerY[k4] = i4 * 12 + randomsource.nextInt(9);
                                    this.centerZ[k4] = (int) (j4 * 16 + randomsource.nextInt(10));
                                }

                                // Both centers and query offsets are bounded chunk-local longs.
                                double i6 = this.centerX[k4] - i;
                                int j5 = this.centerY[k4] - j;
                                double k5 = this.centerZ[k4] - k;
                                double l5 = i6 * i6 + (double) j5 * j5 + k5 * k5;
                                if (k1 >= l5) {
                                    l2 = k2;
                                    k2 = j2;
                                    j2 = k4;
                                    i2 = l1;
                                    l1 = k1;
                                    k1 = l5;
                                } else if (l1 >= l5) {
                                    l2 = k2;
                                    k2 = k4;
                                    i2 = l1;
                                    l1 = l5;
                                } else if (i2 >= l5) {
                                    l2 = k4;
                                    i2 = l5;
                                }
                            }
                        }
                    }

                    Aquifer.FluidStatus aquifer$fluidstatus1 = this.getAquiferStatus(j2);
                    double d1 = similarity(k1, l1);
                    BlockState blockstate = aquifer$fluidstatus1.at(j);
                    if (d1 <= 0.0D) {
                        this.shouldScheduleFluidUpdate = d1 >= FLOWING_UPDATE_SIMULARITY;
                        return this.filterWaterOutput(aquifer$fluidstatus1, blockstate);
                    } else if (blockstate.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(globalX, j - 1, globalZ).at(j - 1).is(Blocks.LAVA)) {
                        this.shouldScheduleFluidUpdate = true;
                        return this.filterWaterOutput(aquifer$fluidstatus1, blockstate);
                    } else {
                        return this.computePressureSubstance(pContext, pSubstance, aquifer$fluidstatus1,
                                blockstate, k2, l2, d1, k1, l1, i2);
                    }
                }
            }
        }

        @Nullable
        private BlockState computePressureSubstance(DensityFunction.FunctionContext context, double substance,
                Aquifer.FluidStatus first, BlockState state, int secondIndex, int thirdIndex,
                double firstSecondSimilarity, double firstDistance, double secondDistance, double thirdDistance) {
            this.pressureNoise = Double.NaN;
            Aquifer.FluidStatus second = this.getAquiferStatus(secondIndex);
            double pressure = firstSecondSimilarity * this.calculatePressure(context, first, second);
            if (substance + pressure > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
            Aquifer.FluidStatus third = this.getAquiferStatus(thirdIndex);
            double firstThirdSimilarity = similarity(firstDistance, thirdDistance);
            if (firstThirdSimilarity > 0.0D) {
                pressure = firstSecondSimilarity * firstThirdSimilarity * this.calculatePressure(context, first, third);
                if (substance + pressure > 0.0D) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }
            double secondThirdSimilarity = similarity(secondDistance, thirdDistance);
            if (secondThirdSimilarity > 0.0D) {
                pressure = firstSecondSimilarity * secondThirdSimilarity * this.calculatePressure(context, second, third);
                if (substance + pressure > 0.0D) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }
            this.shouldScheduleFluidUpdate = true;
            return this.filterWaterOutput(first, state);
        }

        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private long localCoordinate(Int1088 coordinate, boolean x) {
            int offset = (int) (coordinate.lowLimb() & 15L);
            Int1088[] local = x ? this.localX : this.localZ;
            // Identity, not low bits alone, proves membership in this aligned chunk.
            if (coordinate == local[offset]) return offset;
            long difference = WorldBounds.signedDifferenceAsLong(coordinate, x ? this.originX : this.originZ);
            if (difference == offset) local[offset] = coordinate;
            return difference;
        }

        private Int1088 columnCoordinate(DensityFunction.FunctionContext context, boolean x) {
            if (context == this.noiseChunk) {
                Int1088 interpolated = x ? this.noiseChunk.interpolatedBlockX() : this.noiseChunk.interpolatedBlockZ();
                if (interpolated != null) return interpolated;
            }
            // Low bits select a candidate, never identity: verify every significant limb.
            int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
            long low = x ? context.blockXLow() : context.blockZLow();
            Int1088 candidate = (x ? this.columnX : this.columnZ)[(int) (low & 15L)];
            if (candidate.limbCount() == length) {
                int index = 0;
                while (index < length && candidate.limb(index) == (x ? context.blockXLimb(index) : context.blockZLimb(index))) {
                    ++index;
                }
                if (index == length) return candidate;
            }
            return coordinate(context, x);
        }

        private BlockState filterWaterOutput(Aquifer.FluidStatus status, BlockState state) {
            if (state.is(Blocks.WATER) && this.extrapolatedWater.contains(status)) {
                this.shouldScheduleFluidUpdate = false;
                return Blocks.AIR.defaultBlockState();
            }
            return state;
        }

        private static double similarity(double k1, double l1) {
            double d0 = 25.0D;
            return 1.0D - Math.abs(l1 - k1) / 25.0D;
        }

        private double calculatePressure(DensityFunction.FunctionContext pContext, Aquifer.FluidStatus pFirstFluid, Aquifer.FluidStatus pSecondFluid) {
            int i = pContext.blockY();
            BlockState blockstate = pFirstFluid.at(i);
            BlockState blockstate1 = pSecondFluid.at(i);
            if ((!blockstate.is(Blocks.LAVA) || !blockstate1.is(Blocks.WATER)) && (!blockstate.is(Blocks.WATER) || !blockstate1.is(Blocks.LAVA))) {
                int j = Math.abs(pFirstFluid.fluidLevel - pSecondFluid.fluidLevel);
                if (j == 0) {
                    return 0.0D;
                } else {
                    double d0 = 0.5D * (double) (pFirstFluid.fluidLevel + pSecondFluid.fluidLevel);
                    double d1 = (double) i + 0.5D - d0;
                    double d2 = (double) j / 2.0D;
                    double d3 = 0.0D;
                    double d4 = 2.5D;
                    double d5 = 1.5D;
                    double d6 = 3.0D;
                    double d7 = 10.0D;
                    double d8 = 3.0D;
                    double d9 = d2 - Math.abs(d1);
                    double d10;
                    if (d1 > 0.0D) {
                        double d11 = 0.0D + d9;
                        if (d11 > 0.0D) {
                            d10 = d11 / 1.5D;
                        } else {
                            d10 = d11 / 2.5D;
                        }
                    } else {
                        double d15 = 3.0D + d9;
                        if (d15 > 0.0D) {
                            d10 = d15 / 3.0D;
                        } else {
                            d10 = d15 / 10.0D;
                        }
                    }

                    double d16 = 2.0D;
                    double d12;
                    if (!(d10 < -2.0D) && !(d10 > 2.0D)) {
                        double d13 = this.pressureNoise;
                        if (Double.isNaN(d13)) {
                            double d14 = this.barrierNoise.compute(pContext);
                            this.pressureNoise = d14;
                            d12 = d14;
                        } else {
                            d12 = d13;
                        }
                    } else {
                        d12 = 0.0D;
                    }

                    return 2.0D * (d12 + d10);
                }
            } else {
                return 2.0D;
            }
        }

        private long gridX(long pX) {
            return Math.floorDiv(pX, 16);
        }

        private int gridY(int pY) {
            return Math.floorDiv(pY, 12);
        }

        private long gridZ(long pZ) {
            return Math.floorDiv(pZ, 16);
        }

        private Aquifer.FluidStatus getAquiferStatus(int k1) {
            Aquifer.FluidStatus aquifer$fluidstatus = this.aquiferCache[k1];
            if (aquifer$fluidstatus != null) {
                return aquifer$fluidstatus;
            } else {
                Aquifer.FluidStatus aquifer$fluidstatus1 = this.computeFluid(this.centerX[k1], this.centerY[k1], this.centerZ[k1]);
                this.aquiferCache[k1] = aquifer$fluidstatus1;
                return aquifer$fluidstatus1;
            }
        }

        private Aquifer.FluidStatus computeFluid(long pX, int pY, long pZ) {
            return this.computeFluid(pX, pY, pZ, true);
        }

        private Aquifer.FluidStatus computeFluidUnfiltered(long pX, int pY, long pZ) {
            return this.computeFluid(pX, pY, pZ, false);
        }

        private Aquifer.FluidStatus computeFluid(long pX, int pY, long pZ, boolean filterWater) {
            Aquifer.FluidStatus aquifer$fluidstatus = this.pickFluid(pX, pY, pZ);
            int i = Integer.MAX_VALUE;
            int j = pY + 12;
            int k = pY - 12;
            boolean flag = false;

            for (int[] aint : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                long l = pX + SectionPos.sectionToBlockCoord(aint[0]);
                long i1 = pZ + SectionPos.sectionToBlockCoord(aint[1]);
                int j1 = this.noiseChunk.preliminarySurfaceLevel(WorldBounds.addBlockOffset(this.originX, l), WorldBounds.addBlockOffset(this.originZ, i1));
                int k1 = j1 + 8;
                boolean flag1 = aint[0] == 0 && aint[1] == 0;
                if (flag1 && k > k1) {
                    return aquifer$fluidstatus;
                }

                boolean flag2 = j > k1;
                if (flag2 || flag1) {
                    Aquifer.FluidStatus aquifer$fluidstatus1 = this.pickFluid(l, k1, i1);
                    if (!aquifer$fluidstatus1.at(k1).isAir()) {
                        if (flag1) {
                            flag = true;
                        }

                        if (flag2) {
                            return aquifer$fluidstatus1;
                        }
                    }
                }

                i = Math.min(i, j1);
            }

            SurfaceLevel surface = this.computeSurfaceLevel(pX, pY, pZ, aquifer$fluidstatus, i, flag);
            int l1 = surface.level();
            Aquifer.FluidStatus status = new Aquifer.FluidStatus(l1, this.computeFluidType(pX, pY, pZ, aquifer$fluidstatus, l1));
            // Only tag locally computed water whose actual floodedness sample extrapolated.
            // Surface/ocean early returns can share picker statuses and must remain untouched.
            // Keep fluid levels/types unchanged for pressure and lava calculations.
            if (filterWater && surface.extrapolated() && status.fluidType.is(Blocks.WATER)
                    && l1 != DimensionType.WAY_BELOW_MIN_Y) {
                this.extrapolatedWater.add(status);
            }
            return status;
        }

        /**
         * One context allocation per sample. Full-width coordinates stay local for the
         * aquifer geometry and are translated here to exact global limbs.
         */
        private Aquifer.FluidStatus pickFluid(long x, int y, long z) {
            return this.globalFluidPicker.computeFluid(WorldBounds.addBlockOffset(this.originX, x), y, WorldBounds.addBlockOffset(this.originZ, z));
        }

        private DensityFunction.SinglePointContext singlePointContext(long pX, int pY, long pZ) {
            return new DensityFunction.SinglePointContext(WorldBounds.addBlockOffset(this.originX, pX), pY, WorldBounds.addBlockOffset(this.originZ, pZ));
        }

        /** Translates to the global coordinate first, then divides by {@code 1 << pShift}. */
        private DensityFunction.SinglePointContext singlePointContext(long pX, int pY, long pZ, int pShift) {
            return new DensityFunction.SinglePointContext(WorldBounds.addBlockOffset(this.originX, pX).shiftRight(pShift), pY, WorldBounds.addBlockOffset(this.originZ, pZ).shiftRight(pShift));
        }

        private record SurfaceLevel(int level, boolean extrapolated) {}

        private SurfaceLevel computeSurfaceLevel(long p_223910_, int p_223911_, long p_223912_, Aquifer.FluidStatus p_223913_, int p_223914_, boolean p_223915_) {
            DensityFunction.SinglePointContext densityfunction$singlepointcontext = this.singlePointContext(p_223910_, p_223911_, p_223912_);
            double d0;
            double d1;
            boolean extrapolated = false;
            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion.compute(densityfunction$singlepointcontext), this.depth.compute(densityfunction$singlepointcontext))) {
                d0 = -1.0D;
                d1 = -1.0D;
            } else {
                int i = p_223914_ + 8 - p_223911_;
                int j = 64;
                double d2 = p_223915_ ? Mth.clampedMap((double) i, 0.0D, 64.0D, 1.0D, 0.0D) : 0.0D;
                NoiseSampling.Sample floodedness = NoiseSampling.sampleWithExtrapolation(
                        () -> this.fluidLevelFloodednessNoise.compute(densityfunction$singlepointcontext));
                extrapolated = floodedness.extrapolated();
                double d3 = Mth.clamp(floodedness.value(), -1.0D, 1.0D);
                double d4 = Mth.map(d2, 1.0D, 0.0D, -0.3D, 0.8D);
                double d5 = Mth.map(d2, 1.0D, 0.0D, -0.8D, 0.4D);
                d0 = d3 - d5;
                d1 = d3 - d4;
            }

            int k;
            if (d1 > 0.0D) {
                k = p_223913_.fluidLevel;
            } else if (d0 > 0.0D) {
                k = this.computeRandomizedFluidSurfaceLevel(p_223910_, p_223911_, p_223912_, p_223914_);
            } else {
                k = DimensionType.WAY_BELOW_MIN_Y;
            }

            return new SurfaceLevel(k, extrapolated);
        }

        private int computeRandomizedFluidSurfaceLevel(long p_223910_, int p_223900_, long p_223901_, int p_223902_) {
            int i = 16;
            int j = 40;
            int l = Math.floorDiv(p_223900_, 40);
            int j1 = l * 40 + 20;
            int k1 = 10;
            double d0 = this.fluidLevelSpreadNoise.compute(this.singlePointContext(p_223910_, l, p_223901_, 4)) * 10.0D;
            int l1 = Mth.quantize(d0, 3);
            int i2 = j1 + l1;
            return Math.min(p_223902_, i2);
        }

        private BlockState computeFluidType(long p_223904_, int p_223905_, long p_223906_, Aquifer.FluidStatus p_223907_, int p_223908_) {
            BlockState blockstate = p_223907_.fluidType;
            if (p_223908_ <= -10 && p_223908_ != DimensionType.WAY_BELOW_MIN_Y && p_223907_.fluidType != Blocks.LAVA.defaultBlockState()) {
                int l = Math.floorDiv(p_223905_, 40);
                double d0 = this.lavaNoise.compute(this.singlePointContext(p_223904_, l, p_223906_, 6));
                if (Math.abs(d0) > 0.3D) {
                    blockstate = Blocks.LAVA.defaultBlockState();
                }
            }

            return blockstate;
        }
    }
}
