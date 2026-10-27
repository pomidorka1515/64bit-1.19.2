package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public interface DensityFunction {
    Codec<DensityFunction> DIRECT_CODEC = DensityFunctions.DIRECT_CODEC;
    Codec<Holder<DensityFunction>> CODEC = RegistryFileCodec.create(Registry.DENSITY_FUNCTION_REGISTRY, DIRECT_CODEC);
    Codec<DensityFunction> HOLDER_HELPER_CODEC = CODEC.xmap(DensityFunctions.HolderHolder::new, (p_208226_) -> {
        if (p_208226_ instanceof DensityFunctions.HolderHolder densityfunctions$holderholder) {
            return densityfunctions$holderholder.function();
        } else {
            return new Holder.Direct<>(p_208226_);
        }
    });

    double compute(DensityFunction.FunctionContext pContext);

    void fillArray(double[] pArray, DensityFunction.ContextProvider pContextProvider);

    DensityFunction mapAll(DensityFunction.Visitor pVisitor);

    double minValue();

    double maxValue();

    KeyDispatchDataCodec<? extends DensityFunction> codec();

    default DensityFunction clamp(double pMinValue, double pMaxValue) {
        return new DensityFunctions.Clamp(this, pMinValue, pMaxValue);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    public interface ContextProvider {
        DensityFunction.FunctionContext forIndex(int pArrayIndex);

        void fillAllDirectly(double[] pValues, DensityFunction pFunction);
    }

    public interface FunctionContext {
        int blockXLimbCount();

        long blockXLimb(int index);

        int blockY();

        int blockZLimbCount();

        long blockZLimb(int index);

        default long blockXLow() { return blockXLimb(0); }

        default long blockZLow() { return blockZLimb(0); }

        default boolean blockXFitsLong() { return blockXLimbCount() == 1; }

        default boolean blockZFitsLong() { return blockZLimbCount() == 1; }

        default Blender getBlender() {
            return Blender.empty();
        }
    }

    public static record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {
        public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC.xmap((p_224011_) -> {
            return new DensityFunction.NoiseHolder(p_224011_, (NormalNoise) null);
        }, DensityFunction.NoiseHolder::noiseData);

        public NoiseHolder(Holder<NormalNoise.NoiseParameters> p_224001_) {
            this(p_224001_, (NormalNoise) null);
        }

        public double getValue(double p_224007_, double p_224008_, double p_224009_) {
            return this.noise == null ? 0.0D : this.noise.getValue(p_224007_, p_224008_, p_224009_);
        }

        public double getValue(long cellX, double fracX, double y, long cellZ, double fracZ) {
            return this.noise == null ? 0.0D : this.noise.getValue(cellX, fracX, y, cellZ, fracZ);
        }

        public double getValue(long cellX, double fracX, long cellY, double fracY, long cellZ, double fracZ) {
            return this.noise == null ? 0.0D : this.noise.getValue(cellX, fracX, cellY, fracY, cellZ, fracZ);
        }

        public double getValueScaled(long blockX, double xzScale, double y, long blockZ) {
            return this.noise == null ? 0.0D : this.noise.getValueScaled(blockX, xzScale, y, blockZ);
        }

        public double getValueScaled(long blockX, double xzScale, long blockY, double yScale, long blockZ) {
            return this.noise == null ? 0.0D : this.noise.getValueScaled(blockX, xzScale, blockY, yScale, blockZ);
        }

        public double getValueScaledShifted(long blockX, double xzScale, double shiftX, double y, long blockZ, double shiftZ) {
            return this.noise == null ? 0.0D : this.noise.getValueScaledShifted(blockX, xzScale, shiftX, y, blockZ, shiftZ);
        }

        public double maxValue() {
            return this.noise == null ? 2.0D : this.noise.maxValue();
        }

        public double getValueScaled(long[] x, int xo, int xl, double scale, long y, double yScale, long[] z, int zo, int zl) {
            return this.noise == null ? 0.0D : this.noise.getValueScaled(x, xo, xl, scale, y, yScale, z, zo, zl);
        }

        public double getValueScaledShifted(long[] x, int xo, int xl, double scale, double shiftX, double y, long[] z, int zo, int zl, double shiftZ) {
            return this.noise == null ? 0.0D : this.noise.getValueScaledShifted(x, xo, xl, scale, shiftX, y, z, zo, zl, shiftZ);
        }

        public double getValueScaledAxes(long[] x, int xo, int xl, long[] y, int yo, int yl, long[] z, int zo, int zl, double scale) {
            return this.noise == null ? 0.0D : this.noise.getValueScaledAxes(x, xo, xl, y, yo, yl, z, zo, zl, scale);
        }
    }

    public interface SimpleFunction extends DensityFunction {
        default void fillArray(double[] p_208241_, DensityFunction.ContextProvider p_208242_) {
            p_208242_.fillAllDirectly(p_208241_, this);
        }

        default DensityFunction mapAll(DensityFunction.Visitor p_208239_) {
            return p_208239_.apply(this);
        }
    }

    public static record SinglePointContext(Int1088 blockX, int blockY, Int1088 blockZ) implements DensityFunction.FunctionContext {
        public SinglePointContext(long x, int y, long z) {
            this(Int1088.fromLong(x), y, Int1088.fromLong(z));
        }

        public int blockXLimbCount() { return this.blockX.limbCount(); }
        public long blockXLimb(int index) { return this.blockX.limb(index); }
        public int blockZLimbCount() { return this.blockZ.limbCount(); }
        public long blockZLimb(int index) { return this.blockZ.limb(index); }
    }

    public interface Visitor {
        DensityFunction apply(DensityFunction p_224019_);

        default DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder p_224018_) {
            return p_224018_;
        }
    }
}
