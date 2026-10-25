import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.*;

/** Seed-0 arithmetic boundaries and a precisely defined density-grid transect, not a chunk renderer. */
public final class ShiftCollapseProbe {
    private static final double SECOND = 1.0181268882175227;
    private static final Method ROUTE;
    static {
        try {
            ROUTE = NoiseSampling.class.getDeclaredMethod("route", PreciseNoiseCoordinate.Split.class, double.class);
            ROUTE.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }

    private static boolean broken(BigInteger n, double factor, int octave, double offset, boolean precise) throws Exception {
        if (!precise) {
            double point = (((double) n.longValueExact() * .25) * factor) * Math.scalb(1, octave) + offset;
            return (double) Mth.floor(point) != Math.floor(point);
        }
        var split = new PreciseNoiseCoordinate.Split();
        WorldgenCoordinate.scale(n.shiftRight(64).longValue(), n.longValue(), .25 * factor, octave, split);
        ROUTE.invoke(null, split, offset);
        return split.fraction < 0 || split.fraction >= 1;
    }

    private static BigInteger boundary(int sign, double factor, int octave, double offset, boolean precise) throws Exception {
        BigInteger lo = BigInteger.ZERO, hi = BigInteger.ONE.shiftLeft(precise ? 80 : 36);
        while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
            BigInteger mid = lo.add(hi).shiftRight(1);
            if (broken(mid.multiply(BigInteger.valueOf(sign)), factor, octave, offset, precise)) hi = mid;
            else lo = mid;
        }
        BigInteger result = hi.multiply(BigInteger.valueOf(sign));
        if (!broken(result, factor, octave, offset, precise)
                || broken(result.subtract(BigInteger.valueOf(sign)), factor, octave, offset, precise)) throw new AssertionError();
        return result;
    }

    private static DensityFunction.SinglePointContext context(BigInteger n, boolean x) {
        return new DensityFunction.SinglePointContext(x ? n.shiftRight(64).longValue() : 0,
                x ? n.longValue() : 0, 64, x ? 0 : n.shiftRight(64).longValue(), x ? 0 : n.longValue());
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        boolean bit64 = args.length > 0 && args[0].equals("64");
        boolean precise = true;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(bit64 ? GeneratorMode.BIT_64 : GeneratorMode.BIT_32,
                precise ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                    BuiltinRegistries.NOISE, 0L);
            NormalNoise shift = state.getOrCreateNoise(Noises.SHIFT);
            BigInteger[] earliest = new BigInteger[4];
            for (String stack : new String[]{"second", "first"}) {
                PerlinNoise perlin = (PerlinNoise) field(shift, stack);
                ImprovedNoise[] levels = (ImprovedNoise[]) field(perlin, "noiseLevels");
                int index = levels.length - 1;
                while (levels[index] == null) --index;
                int octave = shift.parameters().firstOctave() + index;
                ImprovedNoise noise = levels[index];
                System.out.printf("%s octave=%d%n", stack, octave);
                // ShiftA: (X,0,Z); ShiftB: (Z,X,0). World X therefore tests noise Y too.
                for (int axis = 0; axis < 2; ++axis) for (int sign : new int[]{1, -1}) {
                    BigInteger a = boundary(sign, stack.equals("second") ? SECOND : 1, octave,
                            axis == 0 ? noise.xo : noise.zo, precise);
                    BigInteger b = boundary(sign, stack.equals("second") ? SECOND : 1, octave,
                            axis == 0 ? noise.yo : noise.xo, precise);
                    BigInteger first = a.abs().min(b.abs()).multiply(BigInteger.valueOf(sign));
                    int slot = axis * 2 + (sign < 0 ? 1 : 0);
                    if (stack.equals("second")) earliest[slot] = first;
                    System.out.printf("%s%s shift_x=%s shift_z=%s earliest=%s%n", sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", a, b, first);
                }
            }
            var r = state.router();
            for (int axis = 0; axis < 2; ++axis) for (int sign : new int[]{1, -1}) {
                BigInteger edge = earliest[axis * 2 + (sign < 0 ? 1 : 0)].abs();
                BigInteger plane = edge.add(BigInteger.valueOf(3)).shiftRight(2).shiftLeft(2);
                System.out.printf("TRANSECT %s%s first affected plane=%s other=0 y=64%n", sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", plane.multiply(BigInteger.valueOf(sign)));
                for (long d : new long[]{-4, 0, 4, 16, 256, 16384}) {
                    BigInteger n = plane.add(BigInteger.valueOf(d)).multiply(BigInteger.valueOf(sign));
                    var c = context(n, axis == 0);
                    double sx = shift.getValueScaledAxes(c.blockXHi(), c.blockX(), 0, 0, c.blockZHi(), c.blockZ(), .25) * 4;
                    double sz = shift.getValueScaledAxes(c.blockZHi(), c.blockZ(), c.blockXHi(), c.blockX(), 0, 0, .25) * 4;
                    if (!precise) {
                        double x = WorldgenCoordinate.toDouble(c.blockXHi(), c.blockX()) * .25;
                        double z = WorldgenCoordinate.toDouble(c.blockZHi(), c.blockZ()) * .25;
                        sx = shift.getValue(x, 0, z) * 4;
                        sz = shift.getValue(z, x, 0) * 4;
                    }
                    System.out.printf("sample=%s shift_x=%g shift_z=%g cont=%g erosion=%g depth=%g density=%g%n", n, sx, sz,
                            r.continents().compute(c), r.erosion().compute(c), r.depth().compute(c), r.finalDensity().compute(c));
                }
                // Exhaustive outward 4-block grid search. Report a local first, not a global visible wall.
                BigInteger firstClimate = null, firstDensity = null;
                long window = args.length > 1 ? Long.parseLong(args[1]) : 3_000_000;
                for (long d = -4; d <= window; d += 4) {
                    BigInteger n = plane.add(BigInteger.valueOf(d)).multiply(BigInteger.valueOf(sign));
                    var c = context(n, axis == 0);
                    double density = r.finalDensity().compute(c);
                    if (firstClimate == null && (!Double.isFinite(r.continents().compute(c)) || !Double.isFinite(r.erosion().compute(c)) || !Double.isFinite(r.ridges().compute(c)))) {
                        firstClimate = n;
                        System.out.printf("first nonfinite climate grid sample=%s density=%g%n", n, density);
                    }
                    if (!Double.isFinite(density)) {
                        firstDensity = n;
                        System.out.printf("first nonfinite density grid sample=%s previous=%g depth=%g erosion=%g%n", n,
                                r.finalDensity().compute(context(n.subtract(BigInteger.valueOf(sign * 4L)), axis == 0)), r.depth().compute(c), r.erosion().compute(c));
                        break;
                    }
                }
                if (firstDensity == null) System.out.println("no nonfinite density in search window");
            }
        }
    }
}
