package net.minecraft.world.level.levelgen;

import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;
import net.minecraft.core.Int1088Scratch;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.synth.GenerationPolicy;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;

/** Coordinate projections and cold copies of density-function limb views. */
public final class ContextCoordinates {
    private ContextCoordinates() {}

    public static double toDouble(Int1088 value) { return WorldgenCoordinate.toDouble(value); }

    public static double toDouble(long[] limbs, int offset, int length) {
        return WorldgenCoordinate.toDouble(limbs, offset, length);
    }

    public static double toDouble(DensityFunction.FunctionContext context, boolean x) {
        int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
        if (length == 1) return (double) (x ? context.blockXLow() : context.blockZLow());
        try (Int1088Scratch scratch = Int1088Scratch.acquire()) {
            copyLimbs(context, x, scratch.limbs(), length);
            return toDouble(scratch.limbs(), 0, length);
        }
    }

    public static double scaled(DensityFunction.FunctionContext context, boolean x, double scale, double shift) {
        if (x ? context.blockXFitsLong() : context.blockZFitsLong()) {
            return GenerationPolicy.shiftedNoiseCoordinate(x ? context.blockXLow() : context.blockZLow(), scale, shift);
        }
        return scaledWide(toDouble(context, x), scale, shift);
    }

    public static Int1088 copy(DensityFunction.FunctionContext context, boolean x) {
        if (context instanceof DensityFunction.SinglePointContext point) return x ? point.blockX() : point.blockZ();
        int length = x ? context.blockXLimbCount() : context.blockZLimbCount();
        if (length == 1) return Int1088.fromLong(x ? context.blockXLow() : context.blockZLow());
        try (Int1088Scratch scratch = Int1088Scratch.acquire()) {
            copyLimbs(context, x, scratch.limbs(), length);
            return copy(scratch.limbs(), 0, length);
        }
    }

    private static void copyLimbs(DensityFunction.FunctionContext context, boolean x, long[] out, int length) {
        for (int i = 0; i < length; ++i) out[i] = x ? context.blockXLimb(i) : context.blockZLimb(i);
    }

    public static double scaled(Int1088 value, double scale, double shift) {
        if (value.fitsLong()) return GenerationPolicy.shiftedNoiseCoordinate(value.lowLimb(), scale, shift);
        return scaledWide(toDouble(value), scale, shift);
    }

    public static double scaled(long[] limbs, int offset, int length, double scale, double shift) {
        if (Int1088Math.fitsLong(limbs, offset, length)) return GenerationPolicy.shiftedNoiseCoordinate(limbs[offset], scale, shift);
        return scaledWide(toDouble(limbs, offset, length), scale, shift);
    }

    private static double scaledWide(double coordinate, double scale, double shift) {
        double value = coordinate * scale;
        if (GenerationPolicy.usesNoFarlandsSafeguards() && !GenerationPolicy.usesPreciseCoordinates()) {
            value = Double.isNaN(value) ? 0 : Math.max(-0x1.0p63, Math.min(Math.nextDown(0x1.0p63), value));
            return WorldBounds.clampAbsoluteDouble(value + shift);
        }
        return value + shift;
    }

    public static Int1088 copy(Int1088 value) { return value.copy(); }
    public static Int1088 copy(long[] limbs, int offset, int length) { return Int1088.fromLimbs(limbs, offset, length); }
}
