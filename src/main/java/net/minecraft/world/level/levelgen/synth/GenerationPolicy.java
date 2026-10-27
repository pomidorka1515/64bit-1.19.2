package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;

/** Sampling policies read only the captured world-owned configuration. */
public class GenerationPolicy {

    protected GenerationPolicy() {
    }

    /** Returns whether the original 32-bit Far Lands arithmetic is active globally. */
    public static boolean isEnabled() {
        return GenerationContext.current().mode().uses32BitArithmetic();
    }

    /** Returns whether BlendedNoise should retain its original 32-bit arithmetic. */
    public static boolean usesLegacyBlendedNoise() {
        return isEnabled();
    }

    /** Returns whether finite, no-Far-Lands coordinate safeguards are active. */
    public static boolean usesNoFarlandsSafeguards() {
        return GenerationContext.current().mode() == GeneratorMode.PATCHED;
    }

    /** Returns whether generator noise should retain split integer/fraction coordinates. */
    public static boolean usesPreciseCoordinates() {
        return GenerationContext.current().precision() == CoordinatePrecision.PRECISE;
    }

    /** Returns whether the explicit no-Far-Lands mode is selected. */
    public static boolean isOff() {
        return GenerationContext.current().mode() == GeneratorMode.PATCHED;
    }

    /** Applies finite-coordinate handling to all modes without Far Lands arithmetic. */
    public static double scaledNoiseCoordinate(long coordinate, double scale) {
        return usesNoFarlandsSafeguards() ? WorldBounds.scaledNoiseCoordinate(coordinate, scale) : (double) coordinate * scale;
    }



    /** Applies finite-coordinate handling to shifted noise in no-Far-Lands modes. */
    public static double shiftedNoiseCoordinate(long coordinate, double scale, double shift) {
        return usesNoFarlandsSafeguards() ? WorldBounds.clampAbsoluteDouble(WorldBounds.scaledNoiseCoordinate(coordinate, scale) + shift) : (double) coordinate * scale + shift;
    }

    static long floor(double value, boolean farlands) {
        return farlands ? (long) Mth.floor(value) : Mth.lfloor(value);
    }

    static double floorSum(long first, long second, boolean farlands) {
        return farlands ? (double) ((int) first + (int) second) : (double) first + (double) second;
    }

    static double floorSum(long first, long second, long third, boolean farlands) {
        return farlands ? (double) ((int) first + (int) second + (int) third) : (double) first + (double) second + (double) third;
    }

}
