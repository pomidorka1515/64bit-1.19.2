package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
import org.apache.commons.lang3.mutable.MutableDouble;

public interface Aquifer {
    static Aquifer create(NoiseChunk pChunk, ChunkPos pChunkPos, NoiseRouter pNoiseRouter, PositionalRandomFactory pPositionalRandomFactory, int pMinY, int pHeight, Aquifer.FluidPicker pGlobalFluidPicker) {
        return new Aquifer.NoiseBasedAquifer(pChunk, pChunkPos, pNoiseRouter, pPositionalRandomFactory, pMinY, pHeight, pGlobalFluidPicker);
    }

    static Aquifer createDisabled(final Aquifer.FluidPicker pDefaultFluid) {
        return new Aquifer() {
            @Nullable
            public BlockState computeSubstance(DensityFunction.FunctionContext p_208172_, double p_208173_) {
                return p_208173_ > 0.0D ? null : pDefaultFluid.computeFluid(p_208172_.blockXHi(), p_208172_.blockX(), p_208172_.blockY(), p_208172_.blockZHi(), p_208172_.blockZ()).at(p_208172_.blockY());
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
        Aquifer.FluidStatus computeFluid(long pX, int pY, long pZ);

        /** Full-width extension; existing sea-level pickers are independent of X/Z. */
        default Aquifer.FluidStatus computeFluid(long xHi, long xLo, int y, long zHi, long zLo) {
            return computeFluid(xLo, y, zLo);
        }
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
        private final BlockPos[] aquiferLocationCache;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final long originXHi;
        private final long originXLo;
        private final long originZHi;
        private final long originZLo;
        private final long originGridXHi;
        private final long originGridXLo;
        private final long originGridZHi;
        private final long originGridZLo;
        private final boolean fullWidth;
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
            Int128 minBlockX = pChunkPos.getMinBlockX();
            Int128 minBlockZ = pChunkPos.getMinBlockZ();
            this.originXHi = minBlockX.hi();
            this.originXLo = minBlockX.lo();
            this.originZHi = minBlockZ.hi();
            this.originZLo = minBlockZ.lo();
            this.originGridXHi = WorldgenCoordinate.shiftHigh(this.originXHi, 4);
            this.originGridXLo = WorldgenCoordinate.shiftLow(this.originXHi, this.originXLo, 4);
            this.originGridZHi = WorldgenCoordinate.shiftHigh(this.originZHi, 4);
            this.originGridZLo = WorldgenCoordinate.shiftLow(this.originZHi, this.originZLo, 4);
            this.fullWidth = !WorldgenCoordinate.fitsLong(this.originXHi, this.originXLo)
                    || !WorldgenCoordinate.fitsLong(this.originZHi, this.originZLo);
            // Full-width aquifers run the existing geometry on the chunk-local origin (0, 0).
            long minGridBlockX = this.fullWidth ? 0L : this.originXLo;
            long maxGridBlockX = this.fullWidth ? 15L : pChunkPos.getMaxBlockX().lo();
            long minGridBlockZ = this.fullWidth ? 0L : this.originZLo;
            long maxGridBlockZ = this.fullWidth ? 15L : pChunkPos.getMaxBlockZ().lo();
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
            this.aquiferLocationCache = new BlockPos[i1];
            Arrays.fill(this.aquiferLocationCache, null);
        }

        private int getIndex(long pGridX, int pGridY, long pGridZ) {
            long i = WorldBounds.signedDifferenceAsLong(pGridX, this.minGridX);
            int j = pGridY - this.minGridY;
            long k = WorldBounds.signedDifferenceAsLong(pGridZ, this.minGridZ);
            return (int) ((j * this.gridSizeZ + k) * this.gridSizeX + i);
        }

        @Nullable
        public BlockState computeSubstance(DensityFunction.FunctionContext pContext, double pSubstance) {
            long globalX = pContext.blockX();
            int j = pContext.blockY();
            long globalZ = pContext.blockZ();
            // Full-width geometry stays local; noise/random/surface sampling translate to global limbs.
            long i = this.fullWidth ? (long) (int) (globalX - this.originXLo) : globalX;
            long k = this.fullWidth ? (long) (int) (globalZ - this.originZLo) : globalZ;
            if (pSubstance > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                Aquifer.FluidStatus aquifer$fluidstatus = this.globalFluidPicker.computeFluid(pContext.blockXHi(), globalX, j, pContext.blockZHi(), globalZ);
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
                    BlockPos j2 = null;
                    BlockPos k2 = null;
                    BlockPos l2 = null;

                    for (int i3 = 0; i3 <= 1; ++i3) {
                        for (int j3 = -1; j3 <= 1; ++j3) {
                            for (int k3 = 0; k3 <= 1; ++k3) {
                                long l3 = WorldBounds.addChunkOffset(l, i3);
                                int i4 = i1 + j3;
                                long j4 = WorldBounds.addChunkOffset(j1, k3);
                                int k4 = this.getIndex(l3, i4, j4);
                                BlockPos i5 = this.aquiferLocationCache[k4];
                                BlockPos l4;
                                if (i5 != null) {
                                    l4 = i5;
                                } else {
                                    RandomSource randomsource;
                                    if (this.fullWidth) {
                                        // The grid is local, so translate it onto the exact global chunk lattice.
                                        long gridXHi = WorldgenCoordinate.addHigh(this.originGridXHi, this.originGridXLo, l3);
                                        long gridXLo = this.originGridXLo + l3;
                                        long gridZHi = WorldgenCoordinate.addHigh(this.originGridZHi, this.originGridZLo, j4);
                                        long gridZLo = this.originGridZLo + j4;
                                        randomsource = this.positionalRandomFactory.at(gridXHi, gridXLo, i4, gridZHi, gridZLo);
                                    } else {
                                        randomsource = this.positionalRandomFactory.at(l3, i4, j4);
                                    }
                                    l4 = new BlockPos(WorldBounds.addBlockOffset(WorldBounds.chunkToBlock(l3), randomsource.nextInt(10)), i4 * 12 + randomsource.nextInt(9), WorldBounds.addBlockOffset(WorldBounds.chunkToBlock(j4), randomsource.nextInt(10)));
                                    this.aquiferLocationCache[k4] = l4;
                                }

                                double i6 = WorldBounds.signedDifference(l4.getXLo(), i);
                                int j5 = l4.getY() - j;
                                double k5 = WorldBounds.signedDifference(l4.getZLo(), k);
                                double l5 = i6 * i6 + (double) j5 * j5 + k5 * k5;
                                if (k1 >= l5) {
                                    l2 = k2;
                                    k2 = j2;
                                    j2 = l4;
                                    i2 = l1;
                                    l1 = k1;
                                    k1 = l5;
                                } else if (l1 >= l5) {
                                    l2 = k2;
                                    k2 = l4;
                                    i2 = l1;
                                    l1 = l5;
                                } else if (i2 >= l5) {
                                    l2 = l4;
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
                        return blockstate;
                    } else if (blockstate.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(pContext.blockXHi(), globalX, j - 1, pContext.blockZHi(), globalZ).at(j - 1).is(Blocks.LAVA)) {
                        this.shouldScheduleFluidUpdate = true;
                        return blockstate;
                    } else {
                        MutableDouble mutabledouble = new MutableDouble(Double.NaN);
                        Aquifer.FluidStatus aquifer$fluidstatus2 = this.getAquiferStatus(k2);
                        double d2 = d1 * this.calculatePressure(pContext, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus2);
                        if (pSubstance + d2 > 0.0D) {
                            this.shouldScheduleFluidUpdate = false;
                            return null;
                        } else {
                            Aquifer.FluidStatus aquifer$fluidstatus3 = this.getAquiferStatus(l2);
                            double d0 = similarity(k1, i2);
                            if (d0 > 0.0D) {
                                double d3 = d1 * d0 * this.calculatePressure(pContext, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus3);
                                if (pSubstance + d3 > 0.0D) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            double d4 = similarity(l1, i2);
                            if (d4 > 0.0D) {
                                double d5 = d1 * d4 * this.calculatePressure(pContext, mutabledouble, aquifer$fluidstatus2, aquifer$fluidstatus3);
                                if (pSubstance + d5 > 0.0D) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            this.shouldScheduleFluidUpdate = true;
                            return blockstate;
                        }
                    }
                }
            }
        }

        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(double k1, double l1) {
            double d0 = 25.0D;
            return 1.0D - Math.abs(l1 - k1) / 25.0D;
        }

        private double calculatePressure(DensityFunction.FunctionContext pContext, MutableDouble pSubstance, Aquifer.FluidStatus pFirstFluid, Aquifer.FluidStatus pSecondFluid) {
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
                        double d13 = pSubstance.getValue();
                        if (Double.isNaN(d13)) {
                            double d14 = this.barrierNoise.compute(pContext);
                            pSubstance.setValue(d14);
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

        private Aquifer.FluidStatus getAquiferStatus(BlockPos j2) {
            long i = j2.getXLo();
            int j = j2.getY();
            long k = j2.getZLo();
            long l = this.gridX(i);
            int i1 = this.gridY(j);
            long j1 = this.gridZ(k);
            int k1 = this.getIndex(l, i1, j1);
            Aquifer.FluidStatus aquifer$fluidstatus = this.aquiferCache[k1];
            if (aquifer$fluidstatus != null) {
                return aquifer$fluidstatus;
            } else {
                Aquifer.FluidStatus aquifer$fluidstatus1 = this.computeFluid(i, j, k);
                this.aquiferCache[k1] = aquifer$fluidstatus1;
                return aquifer$fluidstatus1;
            }
        }

        private Aquifer.FluidStatus computeFluid(long pX, int pY, long pZ) {
            Aquifer.FluidStatus aquifer$fluidstatus = this.pickFluid(pX, pY, pZ);
            int i = Integer.MAX_VALUE;
            int j = pY + 12;
            int k = pY - 12;
            boolean flag = false;

            for (int[] aint : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                long l = pX + SectionPos.sectionToBlockCoord(aint[0]);
                long i1 = pZ + SectionPos.sectionToBlockCoord(aint[1]);
                int j1;
                if (this.fullWidth) {
                    long xHi = WorldgenCoordinate.addHigh(this.originXHi, this.originXLo, l);
                    long xLo = WorldgenCoordinate.addLow(this.originXHi, this.originXLo, l);
                    long zHi = WorldgenCoordinate.addHigh(this.originZHi, this.originZLo, i1);
                    long zLo = WorldgenCoordinate.addLow(this.originZHi, this.originZLo, i1);
                    j1 = this.noiseChunk.preliminarySurfaceLevel(xHi, xLo, zHi, zLo);
                } else {
                    j1 = this.noiseChunk.preliminarySurfaceLevel(l, i1);
                }
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

            int l1 = this.computeSurfaceLevel(pX, pY, pZ, aquifer$fluidstatus, i, flag);
            return new Aquifer.FluidStatus(l1, this.computeFluidType(pX, pY, pZ, aquifer$fluidstatus, l1));
        }

        /**
         * One context allocation per sample. Full-width coordinates stay local for the
         * aquifer geometry and are translated here to exact global limbs.
         */
        private Aquifer.FluidStatus pickFluid(long x, int y, long z) {
            if (!this.fullWidth) return this.globalFluidPicker.computeFluid(x >> 63, x, y, z >> 63, z);
            return this.globalFluidPicker.computeFluid(
                    WorldgenCoordinate.addHigh(this.originXHi, this.originXLo, x),
                    WorldgenCoordinate.addLow(this.originXHi, this.originXLo, x), y,
                    WorldgenCoordinate.addHigh(this.originZHi, this.originZLo, z),
                    WorldgenCoordinate.addLow(this.originZHi, this.originZLo, z));
        }

        private DensityFunction.SinglePointContext singlePointContext(long pX, int pY, long pZ) {
            if (this.fullWidth) {
                return new DensityFunction.SinglePointContext(
                        WorldgenCoordinate.addHigh(this.originXHi, this.originXLo, pX),
                         WorldgenCoordinate.addLow(this.originXHi, this.originXLo, pX),
                        pY,
                        WorldgenCoordinate.addHigh(this.originZHi, this.originZLo, pZ),
                         WorldgenCoordinate.addLow(this.originZHi, this.originZLo, pZ));
            }
            return new DensityFunction.SinglePointContext(pX, pY, pZ);
        }

        /** Translates to the global coordinate first, then divides by {@code 1 << pShift}. */
        private DensityFunction.SinglePointContext singlePointContext(long pX, int pY, long pZ, int pShift) {
            if (this.fullWidth) {
                long xHi = WorldgenCoordinate.addHigh(this.originXHi, this.originXLo, pX);
                long xLo = WorldgenCoordinate.addLow(this.originXHi, this.originXLo, pX);
                long zHi = WorldgenCoordinate.addHigh(this.originZHi, this.originZLo, pZ);
                long zLo = WorldgenCoordinate.addLow(this.originZHi, this.originZLo, pZ);
                return new DensityFunction.SinglePointContext(
                        WorldgenCoordinate.shiftHigh(xHi, pShift),
                        WorldgenCoordinate.shiftLow(xHi, xLo, pShift),
                        pY,
                        WorldgenCoordinate.shiftHigh(zHi, pShift),
                        WorldgenCoordinate.shiftLow(zHi, zLo, pShift));
            }
            long divisor = 1L << pShift;
            return new DensityFunction.SinglePointContext(Math.floorDiv(pX, divisor), pY, Math.floorDiv(pZ, divisor));
        }

        private int computeSurfaceLevel(long p_223910_, int p_223911_, long p_223912_, Aquifer.FluidStatus p_223913_, int p_223914_, boolean p_223915_) {
            DensityFunction.SinglePointContext densityfunction$singlepointcontext = this.singlePointContext(p_223910_, p_223911_, p_223912_);
            double d0;
            double d1;
            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion.compute(densityfunction$singlepointcontext), this.depth.compute(densityfunction$singlepointcontext))) {
                d0 = -1.0D;
                d1 = -1.0D;
            } else {
                int i = p_223914_ + 8 - p_223911_;
                int j = 64;
                double d2 = p_223915_ ? Mth.clampedMap((double) i, 0.0D, 64.0D, 1.0D, 0.0D) : 0.0D;
                double d3 = Mth.clamp(this.fluidLevelFloodednessNoise.compute(densityfunction$singlepointcontext), -1.0D, 1.0D);
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

            return k;
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
