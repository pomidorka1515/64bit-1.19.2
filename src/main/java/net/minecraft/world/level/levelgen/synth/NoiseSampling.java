package net.minecraft.world.level.levelgen.synth;

/** Shared exact-position routing boundary, followed by intentional arithmetic. */
public final class NoiseSampling {
    private static final ThreadLocal<Axes> SCRATCH = ThreadLocal.withInitial(Axes::new);

    private NoiseSampling() {}

    public static double sample(ImprovedNoise noise,
            long xHi, long xLo, double xScale, double shiftX,
            long yHi, long yLo, double yScale, double shiftY,
            long zHi, long zLo, double zScale, double shiftZ,
            int octave, double smear, double smearOffset) {
        Axes axes = SCRATCH.get();
        WorldgenCoordinate.scale(xHi, xLo, xScale, octave, axes.x);
        WorldgenCoordinate.scale(yHi, yLo, yScale, octave, axes.y);
        WorldgenCoordinate.scale(zHi, zLo, zScale, octave, axes.z);
        route(axes.x, Math.scalb(shiftX, octave) + noise.xo);
        route(axes.y, Math.scalb(shiftY, octave) + noise.yo);
        route(axes.z, Math.scalb(shiftZ, octave) + noise.zo);
        return noise.sampleRouted(axes.x, axes.y, axes.z, smear, smearOffset);
    }

    private static void route(PreciseNoiseCoordinate.Split axis, double shift) {
        double local = axis.fraction + shift;
        long carry = (long) Math.floor(local);
        GeneratorMode mode = GenerationContext.current().mode();
        long cell = axis.lattice + carry;
        boolean overflow = ((axis.lattice ^ cell) & (carry ^ cell)) < 0L;
        if (mode != GeneratorMode.PATCHED && (!Double.isNaN(axis.failureMagnitude) || overflow
                || mode == GeneratorMode.BIT_32 && (cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE))) {
            double failure = !Double.isNaN(axis.failureMagnitude) ? axis.failureMagnitude + shift
                    : (double) axis.lattice + local;
            long floor = FarlandsMode.floor(failure, mode == GeneratorMode.BIT_32);
            axis.lattice = floor;
            axis.fraction = failure - (double) floor;
        } else {
            axis.lattice = cell;
            axis.fraction = local - Math.floor(local);
        }
    }

    private static final class Axes {
        final PreciseNoiseCoordinate.Split x = new PreciseNoiseCoordinate.Split();
        final PreciseNoiseCoordinate.Split y = new PreciseNoiseCoordinate.Split();
        final PreciseNoiseCoordinate.Split z = new PreciseNoiseCoordinate.Split();
    }
}
