package net.minecraft.world.level.levelgen.synth;

/** Shared exact-position routing boundary, followed by intentional arithmetic. */
public final class NoiseSampling {
    private static final ThreadLocal<Axes> SCRATCH = ThreadLocal.withInitial(Axes::new);
    private static final ThreadLocal<ExtrapolationCapture> EXTRAPOLATION_CAPTURE = new ThreadLocal<>();

    private NoiseSampling() {}

    /** Observes the actual interpolation fractions without changing the sampled value. */
    public static boolean hasExtrapolation(java.util.function.DoubleSupplier sample) {
        return sampleWithExtrapolation(sample).extrapolated();
    }

    /** Returns the sampled value and its provenance from the same evaluation. */
    public static Sample sampleWithExtrapolation(java.util.function.DoubleSupplier sample) {
        ExtrapolationCapture previous = EXTRAPOLATION_CAPTURE.get();
        ExtrapolationCapture capture = new ExtrapolationCapture();
        EXTRAPOLATION_CAPTURE.set(capture);
        try {
            double value = sample.getAsDouble();
            return new Sample(value, capture.extrapolated);
        } finally {
            if (previous == null) EXTRAPOLATION_CAPTURE.remove();
            else {
                previous.extrapolated |= capture.extrapolated;
                EXTRAPOLATION_CAPTURE.set(previous);
            }
        }
    }

    public record Sample(double value, boolean extrapolated) {}

    static void observeFractions(double x, double y, double z) {
        ExtrapolationCapture capture = EXTRAPOLATION_CAPTURE.get();
        if (capture != null && (!(x >= 0.0D && x < 1.0D)
                || !(y >= 0.0D && y < 1.0D) || !(z >= 0.0D && z < 1.0D))) {
            capture.extrapolated = true;
        }
    }

    private static final class ExtrapolationCapture {
        boolean extrapolated;
    }

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
                || mode.uses32BitArithmetic() && (cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE))) {
            double failure = !Double.isNaN(axis.failureMagnitude) ? axis.failureMagnitude + shift
                    : (double) axis.lattice + local;
            long floor = FarlandsMode.floor(failure, mode.uses32BitArithmetic());
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
