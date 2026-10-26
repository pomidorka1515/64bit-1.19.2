package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.GenerationPolicy;

/** Keep intentional surface overflow separate from bounds on generation loops. */
final class SurfaceArithmetic {
    private SurfaceArithmetic() {}

    static double noise(double value) {
        return GenerationPolicy.usesNoFarlandsSafeguards() ? WorldBounds.clampNoise(value) : value;
    }

    static int depth(double noise, double random) {
        double depth = noise(noise) * 2.75D + 3.0D + random * 0.25D;
        if (!GenerationPolicy.usesNoFarlandsSafeguards())
            return (int) depth;
        if (!Double.isFinite(depth))
            depth = 3.0D;
        return WorldBounds.clampSurfaceDepth((int) Math.round(depth), DimensionType.Y_SIZE);
    }

    static int boundDepth(int depth, int generationDepth) {
        return GenerationPolicy.usesNoFarlandsSafeguards() ? WorldBounds.clampSurfaceDepth(depth, generationDepth) : depth;
    }

    static int add(int first, int second) {
        return GenerationPolicy.usesNoFarlandsSafeguards() ? WorldBounds.addSaturated(first, second) : first + second;
    }

    static int multiply(int first, int second) {
        return GenerationPolicy.usesNoFarlandsSafeguards() ? WorldBounds.multiplySaturated(first, second) : first * second;
    }
}
