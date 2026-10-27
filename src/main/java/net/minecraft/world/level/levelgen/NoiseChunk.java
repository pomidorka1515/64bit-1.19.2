package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;

public class NoiseChunk implements DensityFunction.ContextProvider, DensityFunction.FunctionContext {
    private final NoiseSettings noiseSettings;
    final int cellCountXZ;
    final int cellCountY;
    final int cellNoiseMinY;
    private final Int1088 firstCellX;
    private final Int1088 firstCellZ;
    final Int1088 firstNoiseX;
    final Int1088 firstNoiseZ;
    final List<NoiseChunk.NoiseInterpolator> interpolators;
    final List<NoiseChunk.CacheAllInCell> cellCaches;
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();
    private final Object2IntMap<ChunkPos> preliminarySurfaceLevel = new Object2IntOpenHashMap<>();
    private final Object2IntMap<ColumnPos> exactPreliminarySurfaceLevel = new Object2IntOpenHashMap<>();
    private final Aquifer aquifer;
    private final DensityFunction initialDensityNoJaggedness;
    private final NoiseChunk.BlockStateFiller blockStateRule;
    private final Blender blender;
    private final NoiseChunk.FlatCache blendAlpha;
    private final NoiseChunk.FlatCache blendOffset;
    private final DensityFunctions.BeardifierOrMarker beardifier;
    private ChunkPos lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
    private Blender.BlendingOutput lastBlendingOutput = new Blender.BlendingOutput(1.0D, 0.0D);
    final int noiseSizeXZ;
    final int cellWidth;
    final int cellHeight;
    boolean interpolating;
    boolean fillingCell;
    private Int1088 cellStartBlockX = Int1088.ZERO;
    int cellStartBlockY;
    private Int1088 cellStartBlockZ = Int1088.ZERO;
    private final Int1088 originX, originZ;
    private final long[] absoluteX = new long[Int1088.LIMBS];
    private final long[] absoluteZ = new long[Int1088.LIMBS];
    private final long[] cellX = new long[Int1088.LIMBS];
    private final long[] cellZ = new long[Int1088.LIMBS];
    private final long[] localOffset = new long[1];
    private Int1088 cachedCellX, cachedCellZ;
    private long cachedOffsetX, cachedOffsetZ;
    private int absoluteXLength = 1, absoluteZLength = 1;
    long inCellX;
    int inCellY;
    long inCellZ;
    long interpolationCounter;
    long arrayInterpolationCounter;
    int arrayIndex;
    private final DensityFunction.ContextProvider sliceFillingContextProvider = new DensityFunction.ContextProvider() {
        public DensityFunction.FunctionContext forIndex(int p_209253_) {
            NoiseChunk.this.cellStartBlockY = (p_209253_ + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
            ++NoiseChunk.this.interpolationCounter;
            NoiseChunk.this.inCellY = 0;
            NoiseChunk.this.arrayIndex = p_209253_;
            return NoiseChunk.this;
        }

        public void fillAllDirectly(double[] p_209255_, DensityFunction p_209256_) {
            for (int i2 = 0; i2 < NoiseChunk.this.cellCountY + 1; ++i2) {
                NoiseChunk.this.cellStartBlockY = (i2 + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                ++NoiseChunk.this.interpolationCounter;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = i2;
                p_209255_[i2] = p_209256_.compute(NoiseChunk.this);
            }
        }
    };

    public static NoiseChunk forChunk(ChunkAccess pChunk, RandomState pState, DensityFunctions.BeardifierOrMarker pBeardifierOrMarker, NoiseGeneratorSettings pNoiseGeneratorSettings, Aquifer.FluidPicker pFluidPicke, Blender pBlender) {
        NoiseSettings noisesettings = pNoiseGeneratorSettings.noiseSettings().clampToHeightAccessor(pChunk);
        ChunkPos chunkpos = pChunk.getPos();
        int i = 16 / noisesettings.getCellWidth();
        // Retain every limb through the density context and noise lattice.
        return new NoiseChunk(i, pState, chunkpos.getMinBlockX(), chunkpos.getMinBlockZ(), noisesettings, pBeardifierOrMarker, pNoiseGeneratorSettings, pFluidPicke, pBlender);
    }

    public NoiseChunk(int p_224343_, RandomState p_224344_, long p_224345_, long p_224346_, NoiseSettings p_224347_, DensityFunctions.BeardifierOrMarker p_224348_, NoiseGeneratorSettings p_224349_, Aquifer.FluidPicker p_224350_, Blender p_224351_) {
        this(p_224343_, p_224344_, Int1088.fromLong(p_224345_), Int1088.fromLong(p_224346_), p_224347_, p_224348_, p_224349_, p_224350_, p_224351_);
    }

    public NoiseChunk(int p_224343_, RandomState p_224344_, Int1088 x, Int1088 z, NoiseSettings p_224347_, DensityFunctions.BeardifierOrMarker p_224348_, NoiseGeneratorSettings p_224349_, Aquifer.FluidPicker p_224350_, Blender p_224351_) {
        this.originX = x;
        this.originZ = z;
        this.noiseSettings = p_224347_;
        this.cellWidth = p_224347_.getCellWidth();
        this.cellHeight = p_224347_.getCellHeight();
        this.cellCountXZ = p_224343_;
        this.cellCountY = Mth.intFloorDiv(p_224347_.height(), this.cellHeight);
        this.cellNoiseMinY = Mth.intFloorDiv(p_224347_.minY(), this.cellHeight);
        this.firstCellX = x.shiftRight(Integer.numberOfTrailingZeros(this.cellWidth));
        this.firstCellZ = z.shiftRight(Integer.numberOfTrailingZeros(this.cellWidth));
        this.interpolators = Lists.newArrayList();
        this.cellCaches = Lists.newArrayList();
        this.firstNoiseX = x.shiftRight(2);
        this.firstNoiseZ = z.shiftRight(2);
        this.noiseSizeXZ = QuartPos.fromBlock(p_224343_ * this.cellWidth);
        this.blender = p_224351_;
        this.beardifier = p_224348_;
        this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
        this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);

        for (int i = 0; i <= this.noiseSizeXZ; ++i) {
            Int1088 j = WorldBounds.addBlockOffset(this.firstNoiseX, i);
            Int1088 k = j.shiftLeftSaturated(2);

            for (int l = 0; l <= this.noiseSizeXZ; ++l) {
                Int1088 i1 = WorldBounds.addBlockOffset(this.firstNoiseZ, l);
                Int1088 j1 = i1.shiftLeftSaturated(2);
                Blender.BlendingOutput blender$blendingoutput = p_224351_.blendOffsetAndFactor(k, j1);
                this.blendAlpha.values[i][l] = blender$blendingoutput.alpha();
                this.blendOffset.values[i][l] = blender$blendingoutput.blendingOffset();
            }
        }

        NoiseRouter noiserouter = p_224344_.router();
        NoiseRouter noiserouter1 = noiserouter.mapAll(this::wrap);
        if (!p_224349_.isAquifersEnabled()) {
            this.aquifer = Aquifer.createDisabled(p_224350_);
        } else {
            this.aquifer = Aquifer.create(this, new ChunkPos(x.shiftRight(4), z.shiftRight(4)), noiserouter1, p_224344_.aquiferRandom(), p_224347_.minY(), p_224347_.height(), p_224350_);
        }

        ImmutableList.Builder<NoiseChunk.BlockStateFiller> builder = ImmutableList.builder();
        DensityFunction densityfunction = DensityFunctions.cacheAllInCell(DensityFunctions.add(noiserouter1.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)).mapAll(this::wrap);
        builder.add((p_209217_) -> {
            return this.aquifer.computeSubstance(p_209217_, densityfunction.compute(p_209217_));
        });
        if (p_224349_.oreVeinsEnabled()) {
            builder.add(OreVeinifier.create(noiserouter1.veinToggle(), noiserouter1.veinRidged(), noiserouter1.veinGap(), p_224344_.oreRandom()));
        }

        this.blockStateRule = new MaterialRuleList(builder.build());
        this.initialDensityNoJaggedness = noiserouter1.initialDensityWithoutJaggedness();
    }

    protected Climate.Sampler cachedClimateSampler(NoiseRouter pNoiseRouter, List<Climate.ParameterPoint> pPoints) {
        return new Climate.Sampler(pNoiseRouter.temperature().mapAll(this::wrap), pNoiseRouter.vegetation().mapAll(this::wrap), pNoiseRouter.continents().mapAll(this::wrap), pNoiseRouter.erosion().mapAll(this::wrap), pNoiseRouter.depth().mapAll(this::wrap), pNoiseRouter.ridges().mapAll(this::wrap), pPoints);
    }

    @Nullable
    protected BlockState getInterpolatedState() {
        return this.blockStateRule.calculate(this);
    }

    private void updateAbsoluteX() {
        if (this.cachedCellX != this.cellStartBlockX || this.cachedOffsetX != this.inCellX) {
            this.cellStartBlockX.copyFixed(this.cellX, 0);
            this.localOffset[0] = this.inCellX;
            this.absoluteXLength = Int1088Math.addSaturated(this.cellX, 0, this.cellStartBlockX.limbCount(), this.localOffset, 0, 1, this.absoluteX, 0);
            this.cachedCellX = this.cellStartBlockX;
            this.cachedOffsetX = this.inCellX;
        }
    }

    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    private void updateAbsoluteZ() {
        if (this.cachedCellZ != this.cellStartBlockZ || this.cachedOffsetZ != this.inCellZ) {
            this.cellStartBlockZ.copyFixed(this.cellZ, 0);
            this.localOffset[0] = this.inCellZ;
            this.absoluteZLength = Int1088Math.addSaturated(this.cellZ, 0, this.cellStartBlockZ.limbCount(), this.localOffset, 0, 1, this.absoluteZ, 0);
            this.cachedCellZ = this.cellStartBlockZ;
            this.cachedOffsetZ = this.inCellZ;
        }
    }

    public int blockXLimbCount() { updateAbsoluteX(); return this.absoluteXLength; }
    public long blockXLimb(int index) { updateAbsoluteX(); return index < this.absoluteXLength ? this.absoluteX[index] : this.absoluteX[this.absoluteXLength - 1] >> 63; }
    public int blockZLimbCount() { updateAbsoluteZ(); return this.absoluteZLength; }
    public long blockZLimb(int index) { updateAbsoluteZ(); return index < this.absoluteZLength ? this.absoluteZ[index] : this.absoluteZ[this.absoluteZLength - 1] >> 63; }

    private void setCellX(long offset) {
        long local = offset * this.cellWidth - (this.originX.limb(0) & (this.cellWidth - 1));
        this.cellStartBlockX = WorldBounds.addBlockOffset(this.originX, local);
    }

    private void setCellZ(long offset) {
        long local = offset * this.cellWidth - (this.originZ.limb(0) & (this.cellWidth - 1));
        this.cellStartBlockZ = WorldBounds.addBlockOffset(this.originZ, local);
    }

    public int preliminarySurfaceLevel(long pX, long pZ) {
        long i = QuartPos.toBlock(QuartPos.fromBlock(pX));
        long j = QuartPos.toBlock(QuartPos.fromBlock(pZ));
        if (FarlandsMode.usesPreciseCoordinates()) {
            // Precise sampling uses unclamped block-column keys.
            return this.exactPreliminarySurfaceLevel.computeIfAbsent(new ColumnPos(i, j),
                    (ToIntFunction<ColumnPos>) column -> this.computePreliminarySurfaceLevel(column.x(), column.z()));
        }
        return this.preliminarySurfaceLevel.computeIfAbsent(legacyNoiseColumnKey(i, j), (ToIntFunction<ChunkPos>) this::computePreliminarySurfaceLevel);
    }

    public int preliminarySurfaceLevel(Int1088 x, Int1088 z) {
        if (x.fitsLong() && z.fitsLong())
            return preliminarySurfaceLevel(x.limb(0), z.limb(0));
        return this.exactPreliminarySurfaceLevel.computeIfAbsent(new ColumnPos(x.shiftRight(2).shiftLeftSaturated(2), z.shiftRight(2).shiftLeftSaturated(2)),
                (ToIntFunction<ColumnPos>) column -> this.computePreliminarySurfaceLevel(column.x(), column.z()));
    }

    private int computePreliminarySurfaceLevel(Int1088 x, Int1088 z) {
        int min = this.noiseSettings.minY();
        for (int y = min + this.noiseSettings.height(); y >= min; y -= this.cellHeight) {
            if (this.initialDensityNoJaggedness.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.390625D)
                return y;
        }
        return Integer.MAX_VALUE;
    }

    /** Preserve the legacy cache domain for narrow lossy sampling, not world chunk identity. */
    private static ChunkPos legacyNoiseColumnKey(long x, long z) {
        long min = Long.MIN_VALUE >> 4;
        long max = Long.MAX_VALUE >> 4;
        return new ChunkPos(Math.max(min, Math.min(max, x)), Math.max(min, Math.min(max, z)));
    }

    private int computePreliminarySurfaceLevel(ChunkPos p_198250_) {
        return this.computePreliminarySurfaceLevel(p_198250_.x(), p_198250_.z());
    }

    private int computePreliminarySurfaceLevel(long i, long j) {
        int k = this.noiseSettings.minY();

        for (int l = k + this.noiseSettings.height(); l >= k; l -= this.cellHeight) {
            if (this.initialDensityNoJaggedness.compute(new DensityFunction.SinglePointContext(i, l, j)) > 0.390625D) {
                return l;
            }
        }

        return Integer.MAX_VALUE;
    }

    public Blender getBlender() {
        return this.blender;
    }

    private void fillSlice(boolean pIsSlice0, int offset) {
        this.setCellX(offset);
        this.inCellX = 0;

        for (int i = 0; i < this.cellCountXZ + 1; ++i) {
            this.setCellZ(i);
            this.inCellZ = 0;
            ++this.arrayInterpolationCounter;

            for (NoiseChunk.NoiseInterpolator noisechunk$noiseinterpolator : this.interpolators) {
                double[] adouble = (pIsSlice0 ? noisechunk$noiseinterpolator.slice0 : noisechunk$noiseinterpolator.slice1)[i];
                noisechunk$noiseinterpolator.fillArray(adouble, this.sliceFillingContextProvider);
            }
        }

        ++this.arrayInterpolationCounter;
    }

    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = true;
            this.interpolationCounter = 0L;
            this.fillSlice(true, 0);
        }
    }

    public void advanceCellX(int pIncrement) {
        this.fillSlice(false, pIncrement + 1);
        this.setCellX(pIncrement);
    }

    /** Cell widths are positive powers of two, so the minimum block is cell-aligned. */
    private static long cellToBlock(long cell, int width) {
        if (cell > Long.MAX_VALUE / width)
            return Long.MAX_VALUE;
        if (cell < Long.MIN_VALUE / width)
            return Long.MIN_VALUE;
        return cell * width;
    }

    public NoiseChunk forIndex(int pArrayIndex) {
        int i = Math.floorMod(pArrayIndex, this.cellWidth);
        int j = Math.floorDiv(pArrayIndex, this.cellWidth);
        int k = Math.floorMod(j, this.cellWidth);
        int l = this.cellHeight - 1 - Math.floorDiv(j, this.cellWidth);
        this.inCellX = k;
        this.inCellY = l;
        this.inCellZ = i;
        this.arrayIndex = pArrayIndex;
        return this;
    }

    public void fillAllDirectly(double[] pValues, DensityFunction pFunction) {
        this.arrayIndex = 0;

        for (int i = this.cellHeight - 1; i >= 0; --i) {
            this.inCellY = i;

            for (int j = 0; j < this.cellWidth; ++j) {
                this.inCellX = j;

                for (int k = 0; k < this.cellWidth; ++k) {
                    this.inCellZ = k;
                    pValues[this.arrayIndex++] = pFunction.compute(this);
                }
            }
        }
    }

    public void selectCellYZ(int pY, int pZ) {
        this.interpolators.forEach((p_209205_) -> {
            p_209205_.selectCellYZ(pY, pZ);
        });
        this.fillingCell = true;
        this.cellStartBlockY = (pY + this.cellNoiseMinY) * this.cellHeight;
        this.setCellZ(pZ);
        ++this.arrayInterpolationCounter;

        for (NoiseChunk.CacheAllInCell noisechunk$cacheallincell : this.cellCaches) {
            noisechunk$cacheallincell.noiseFiller.fillArray(noisechunk$cacheallincell.values, this);
        }

        ++this.arrayInterpolationCounter;
        this.fillingCell = false;
    }

    public void updateForY(int p_209192_, double p_209193_) {
        this.inCellY = p_209192_ - this.cellStartBlockY;
        this.interpolators.forEach((p_209238_) -> {
            p_209238_.updateForY(p_209193_);
        });
    }

    public void updateForX(long p_209231_, double p_209232_) {
        this.updateForX(Int1088.fromLong(p_209231_), p_209232_);
    }

    public void updateForX(Int1088 x, double p_209232_) {
        this.inCellX = WorldBounds.signedDifferenceAsLong(x, this.cellStartBlockX);
        this.interpolators.forEach((p_209229_) -> {
            p_209229_.updateForX(p_209232_);
        });
    }

    public void updateForZ(long p_209242_, double p_209243_) {
        this.updateForZ(Int1088.fromLong(p_209242_), p_209243_);
    }

    public void updateForZ(Int1088 z, double p_209243_) {
        this.inCellZ = WorldBounds.signedDifferenceAsLong(z, this.cellStartBlockZ);
        ++this.interpolationCounter;
        this.interpolators.forEach((p_209188_) -> {
            p_209188_.updateForZ(p_209243_);
        });
    }

    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = false;
        }
    }

    public void swapSlices() {
        this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    protected int cellWidth() {
        return this.cellWidth;
    }

    protected int cellHeight() {
        return this.cellHeight;
    }

    Blender.BlendingOutput getOrComputeBlendingOutput(Int1088 pChunkX, Int1088 pChunkZ) {
        ChunkPos i = new ChunkPos(pChunkX, pChunkZ);
        if (this.lastBlendingDataPos.equals(i)) {
            return this.lastBlendingOutput;
        } else {
            this.lastBlendingDataPos = i;
            Blender.BlendingOutput blender$blendingoutput = this.blender.blendOffsetAndFactor(pChunkX, pChunkZ);
            this.lastBlendingOutput = blender$blendingoutput;
            return blender$blendingoutput;
        }
    }

    protected DensityFunction wrap(DensityFunction p_209214_) {
        return this.wrapped.computeIfAbsent(p_209214_, this::wrapNew);
    }

    private DensityFunction wrapNew(DensityFunction p_209234_) {
        if (p_209234_ instanceof DensityFunctions.Marker) {
            DensityFunctions.Marker densityfunctions$marker = (DensityFunctions.Marker) p_209234_;
            Object object;
            switch (densityfunctions$marker.type()) {
                case Interpolated:
                    object = new NoiseChunk.NoiseInterpolator(densityfunctions$marker.wrapped());
                    break;
                case FlatCache:
                    object = new NoiseChunk.FlatCache(densityfunctions$marker.wrapped(), true);
                    break;
                case Cache2D:
                    object = new NoiseChunk.Cache2D(densityfunctions$marker.wrapped());
                    break;
                case CacheOnce:
                    object = new NoiseChunk.CacheOnce(densityfunctions$marker.wrapped());
                    break;
                case CacheAllInCell:
                    object = new NoiseChunk.CacheAllInCell(densityfunctions$marker.wrapped());
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            return (DensityFunction) object;
        } else {
            if (this.blender != Blender.empty()) {
                if (p_209234_ == DensityFunctions.BlendAlpha.INSTANCE) {
                    return this.blendAlpha;
                }

                if (p_209234_ == DensityFunctions.BlendOffset.INSTANCE) {
                    return this.blendOffset;
                }
            }

            if (p_209234_ == DensityFunctions.BeardifierMarker.INSTANCE) {
                return this.beardifier;
            } else if (p_209234_ instanceof DensityFunctions.HolderHolder) {
                DensityFunctions.HolderHolder densityfunctions$holderholder = (DensityFunctions.HolderHolder) p_209234_;
                return densityfunctions$holderholder.function().value();
            } else {
                return p_209234_;
            }
        }
    }

    class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {
        public DensityFunction wrapped() {
            return DensityFunctions.BlendAlpha.INSTANCE;
        }

        public DensityFunction mapAll(DensityFunction.Visitor p_224365_) {
            return this.wrapped().mapAll(p_224365_);
        }

        public double compute(DensityFunction.FunctionContext p_209264_) {
            return NoiseChunk.this.getOrComputeBlendingOutput(contextCoordinate(p_209264_, true), contextCoordinate(p_209264_, false)).alpha();
        }

        public void fillArray(double[] p_209266_, DensityFunction.ContextProvider p_209267_) {
            p_209267_.fillAllDirectly(p_209266_, this);
        }

        public double minValue() {
            return 0.0D;
        }

        public double maxValue() {
            return 1.0D;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendAlpha.CODEC;
        }
    }

    class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {
        public DensityFunction wrapped() {
            return DensityFunctions.BlendOffset.INSTANCE;
        }

        public DensityFunction mapAll(DensityFunction.Visitor p_224368_) {
            return this.wrapped().mapAll(p_224368_);
        }

        public double compute(DensityFunction.FunctionContext p_209276_) {
            return NoiseChunk.this.getOrComputeBlendingOutput(contextCoordinate(p_209276_, true), contextCoordinate(p_209276_, false)).blendingOffset();
        }

        public void fillArray(double[] p_209278_, DensityFunction.ContextProvider p_209279_) {
            p_209279_.fillAllDirectly(p_209278_, this);
        }

        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendOffset.CODEC;
        }
    }

    @FunctionalInterface
    public interface BlockStateFiller {
        @Nullable
        BlockState calculate(DensityFunction.FunctionContext pContext);
    }

    private static Int1088 contextCoordinate(DensityFunction.FunctionContext context, boolean x) {
        try (net.minecraft.core.Int1088Scratch scratch = net.minecraft.core.Int1088Scratch.acquire()) {
            int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
            long[] limbs = scratch.limbs();
            for (int i = 0; i < length; i++) limbs[i] = x ? context.blockXLimb(i) : context.blockZLimb(i);
            return Int1088.fromLimbs(limbs, 0, length);
        }
    }

    private static boolean matchesCoordinate(Int1088 value, DensityFunction.FunctionContext context, boolean x) {
        int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
        if (value == null || value.limbCount() != length) return false;
        for (int i = 0; i < length; i++) {
            if (value.limb(i) != (x ? context.blockXLimb(i) : context.blockZLimb(i))) return false;
        }
        return true;
    }

    static class Cache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastX, lastZ;
        private Int1088 lastExactX, lastExactZ;
        private boolean hasLastPos2D, hasLastExactPos2D;
        private double lastExactValue;
        private double lastValue;

        Cache2D(DensityFunction pFunction) {
            this.function = pFunction;
        }

        public double compute(DensityFunction.FunctionContext pContext) {
            long i = pContext.blockXLow();
            long j = pContext.blockZLow();
            if (FarlandsMode.usesPreciseCoordinates() || !pContext.blockXFitsLong() || !pContext.blockZFitsLong()) {
                if (!this.hasLastExactPos2D || !matchesCoordinate(this.lastExactX, pContext, true) || !matchesCoordinate(this.lastExactZ, pContext, false)) {
                    this.hasLastExactPos2D = true;
                    this.lastExactX = contextCoordinate(pContext, true);
                    this.lastExactZ = contextCoordinate(pContext, false);
                    this.lastExactValue = this.function.compute(pContext);
                }
                return this.lastExactValue;
            }
            long x = Math.max(Long.MIN_VALUE >> 4, Math.min(Long.MAX_VALUE >> 4, i));
            long z = Math.max(Long.MIN_VALUE >> 4, Math.min(Long.MAX_VALUE >> 4, j));
            if (this.hasLastPos2D && x == this.lastX && z == this.lastZ) {
                return this.lastValue;
            } else {
                this.hasLastPos2D = true;
                this.lastX = x;
                this.lastZ = z;
                double d0 = this.function.compute(pContext);
                this.lastValue = d0;
                return d0;
            }
        }

        public void fillArray(double[] pArray, DensityFunction.ContextProvider pContextProvider) {
            this.function.fillArray(pArray, pContextProvider);
        }

        public DensityFunction wrapped() {
            return this.function;
        }

        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Cache2D;
        }
    }

    class CacheAllInCell implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        final DensityFunction noiseFiller;
        final double[] values;

        CacheAllInCell(DensityFunction p_209301_) {
            this.noiseFiller = p_209301_;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
            NoiseChunk.this.cellCaches.add(this);
        }

        public double compute(DensityFunction.FunctionContext pContext) {
            if (pContext != NoiseChunk.this) {
                return this.noiseFiller.compute(pContext);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                long i = NoiseChunk.this.inCellX;
                int j = NoiseChunk.this.inCellY;
                long k = NoiseChunk.this.inCellZ;
                return i >= 0 && j >= 0 && k >= 0 && i < NoiseChunk.this.cellWidth && j < NoiseChunk.this.cellHeight && k < NoiseChunk.this.cellWidth ? this.values[(int) (((NoiseChunk.this.cellHeight - 1 - j) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + k)] : this.noiseFiller.compute(pContext);
            }
        }

        public void fillArray(double[] pArray, DensityFunction.ContextProvider pContextProvider) {
            pContextProvider.fillAllDirectly(pArray, this);
        }

        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheAllInCell;
        }
    }

    class CacheOnce implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastCounter;
        private long lastArrayCounter;
        private double lastValue;
        @Nullable
        private double[] lastArray;

        CacheOnce(DensityFunction p_209317_) {
            this.function = p_209317_;
        }

        public double compute(DensityFunction.FunctionContext pContext) {
            if (pContext != NoiseChunk.this) {
                return this.function.compute(pContext);
            } else if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                return this.lastArray[NoiseChunk.this.arrayIndex];
            } else if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            } else {
                this.lastCounter = NoiseChunk.this.interpolationCounter;
                double d0 = this.function.compute(pContext);
                this.lastValue = d0;
                return d0;
            }
        }

        public void fillArray(double[] pArray, DensityFunction.ContextProvider pContextProvider) {
            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                System.arraycopy(this.lastArray, 0, pArray, 0, pArray.length);
            } else {
                this.wrapped().fillArray(pArray, pContextProvider);
                if (this.lastArray != null && this.lastArray.length == pArray.length) {
                    System.arraycopy(pArray, 0, this.lastArray, 0, pArray.length);
                } else {
                    this.lastArray = (double[]) pArray.clone();
                }

                this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
            }
        }

        public DensityFunction wrapped() {
            return this.function;
        }

        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheOnce;
        }
    }

    class FlatCache implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction noiseFiller;
        final double[][] values;

        FlatCache(DensityFunction p_209330_, boolean p_209331_) {
            this.noiseFiller = p_209330_;
            this.values = new double[NoiseChunk.this.noiseSizeXZ + 1][NoiseChunk.this.noiseSizeXZ + 1];
            if (p_209331_) {
                for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; ++i) {
                    Int1088 j = WorldBounds.addBlockOffset(NoiseChunk.this.firstNoiseX, i);
                    Int1088 k = j.shiftLeftSaturated(2);

                    for (int l = 0; l <= NoiseChunk.this.noiseSizeXZ; ++l) {
                        Int1088 i1 = WorldBounds.addBlockOffset(NoiseChunk.this.firstNoiseZ, l);
                        Int1088 j1 = i1.shiftLeftSaturated(2);
                        this.values[i][l] = p_209330_.compute(new DensityFunction.SinglePointContext(k, 0, j1));
                    }
                }
            }
        }

        public double compute(DensityFunction.FunctionContext p_209333_) {
            Int1088 i = contextCoordinate(p_209333_, true).shiftRight(2);
            Int1088 j = contextCoordinate(p_209333_, false).shiftRight(2);
            long k = Int1088Math.signedDifferenceAsLong(i, NoiseChunk.this.firstNoiseX);
            long l = Int1088Math.signedDifferenceAsLong(j, NoiseChunk.this.firstNoiseZ);
            int i1 = this.values.length;
            return k >= 0 && l >= 0 && k < i1 && l < i1 ? this.values[(int) k][(int) l] : this.noiseFiller.compute(p_209333_);
        }

        public void fillArray(double[] p_209335_, DensityFunction.ContextProvider p_209336_) {
            p_209336_.fillAllDirectly(p_209335_, this);
        }

        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.FlatCache;
        }
    }

    interface NoiseChunkDensityFunction extends DensityFunction {
        DensityFunction wrapped();

        default double minValue() {
            return this.wrapped().minValue();
        }

        default double maxValue() {
            return this.wrapped().maxValue();
        }
    }

    public class NoiseInterpolator implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        double[][] slice0;
        double[][] slice1;
        private final DensityFunction noiseFiller;
        private double noise000;
        private double noise001;
        private double noise100;
        private double noise101;
        private double noise010;
        private double noise011;
        private double noise110;
        private double noise111;
        private double valueXZ00;
        private double valueXZ10;
        private double valueXZ01;
        private double valueXZ11;
        private double valueZ0;
        private double valueZ1;
        private double value;

        NoiseInterpolator(DensityFunction p_209345_) {
            this.noiseFiller = p_209345_;
            this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice(int p_188855_, int p_188856_) {
            int i = p_188856_ + 1;
            int j = p_188855_ + 1;
            double[][] adouble = new double[i][j];

            for (int k = 0; k < i; ++k) {
                adouble[k] = new double[j];
            }

            return adouble;
        }

        void selectCellYZ(int p_188864_, int p_188865_) {
            this.noise000 = this.slice0[p_188865_][p_188864_];
            this.noise001 = this.slice0[p_188865_ + 1][p_188864_];
            this.noise100 = this.slice1[p_188865_][p_188864_];
            this.noise101 = this.slice1[p_188865_ + 1][p_188864_];
            this.noise010 = this.slice0[p_188865_][p_188864_ + 1];
            this.noise011 = this.slice0[p_188865_ + 1][p_188864_ + 1];
            this.noise110 = this.slice1[p_188865_][p_188864_ + 1];
            this.noise111 = this.slice1[p_188865_ + 1][p_188864_ + 1];
        }

        void updateForY(double p_188851_) {
            this.valueXZ00 = Mth.lerp(p_188851_, this.noise000, this.noise010);
            this.valueXZ10 = Mth.lerp(p_188851_, this.noise100, this.noise110);
            this.valueXZ01 = Mth.lerp(p_188851_, this.noise001, this.noise011);
            this.valueXZ11 = Mth.lerp(p_188851_, this.noise101, this.noise111);
        }

        void updateForX(double p_188862_) {
            this.valueZ0 = Mth.lerp(p_188862_, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = Mth.lerp(p_188862_, this.valueXZ01, this.valueXZ11);
        }

        void updateForZ(double p_188867_) {
            this.value = Mth.lerp(p_188867_, this.valueZ0, this.valueZ1);
        }

        public double compute(DensityFunction.FunctionContext pContext) {
            if (pContext != NoiseChunk.this) {
                return this.noiseFiller.compute(pContext);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                return NoiseChunk.this.fillingCell ? Mth.lerp3((double) NoiseChunk.this.inCellX / (double) NoiseChunk.this.cellWidth, (double) NoiseChunk.this.inCellY / (double) NoiseChunk.this.cellHeight, (double) NoiseChunk.this.inCellZ / (double) NoiseChunk.this.cellWidth, this.noise000, this.noise100, this.noise010, this.noise110, this.noise001, this.noise101, this.noise011, this.noise111) : this.value;
            }
        }

        public void fillArray(double[] pArray, DensityFunction.ContextProvider pContextProvider) {
            if (NoiseChunk.this.fillingCell) {
                pContextProvider.fillAllDirectly(pArray, this);
            } else {
                this.wrapped().fillArray(pArray, pContextProvider);
            }
        }

        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        private void swapSlices() {
            double[][] adouble = this.slice0;
            this.slice0 = this.slice1;
            this.slice1 = adouble;
        }

        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }
    }
}
