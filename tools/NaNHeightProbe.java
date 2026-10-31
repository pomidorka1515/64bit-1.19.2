package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.*;

/**
 * Read-only seed-0, stable-64/precise integer-coordinate profiles at transverse coordinate zero.
 * Run with tools/nan-height.init.gradle, task nanHeightProbe, -PnanArgs="...":
 * survey | edges | walls | points MAGNITUDE... | trace MAGNITUDE Y
 * | search FIELD TARGET LOWER_MAGNITUDE UPPER_MAGNITUDE [SIGNED_AXIS].
 * Fields: 0=direct base NaN, 1=direct base infinity, 2=direct final NaN,
 * 3=direct base NaN on Y multiples of eight. Heights are -64 through 319.
 * Searches are local brackets, not a proof of global monotonicity or a global first failure.
 */
public final class NaNHeightProbe {
    private static DensityFunction base;
    private static RandomState state;
    private static boolean alongX;
    private static int sign;

    private static BigInteger search(int field, int target, BigInteger lo, BigInteger hi) {
        if (profile(lo, false)[field] >= target || profile(hi, false)[field] < target)
            throw new IllegalArgumentException("Bad local bracket");
        while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
            BigInteger mid = lo.add(hi).shiftRight(1);
            if (profile(mid, false)[field] >= target) hi = mid; else lo = mid;
        }
        for (int d = -16; d <= 16; d++) {
            int count = profile(hi.add(BigInteger.valueOf(d)), false)[field];
            if ((count >= target) != (d >= 0)) throw new AssertionError("Nonmonotonic local edge");
        }
        System.out.printf("EDGE %s%s field=%d target=%d n=%s%n", sign > 0 ? "+" : "-", alongX ? "X" : "Z",
                field, target, hi.multiply(BigInteger.valueOf(sign)));
        profile(hi.subtract(BigInteger.ONE), true);
        profile(hi, true);
        return hi;
    }

    private static void trace(BigInteger magnitude, int y) throws Exception {
        var n = Int1088.parseDecimal(magnitude.multiply(BigInteger.valueOf(sign)).toString());
        var p = new DensityFunction.SinglePointContext(alongX ? n : Int1088.ZERO, y, alongX ? Int1088.ZERO : n);
        BlendedNoise.beginDebugCapture();
        base.compute(p);
        System.out.printf("TRACE %s%s n=%s y=%d %s %s%n", sign > 0 ? "+" : "-", alongX ? "X" : "Z",
                magnitude.multiply(BigInteger.valueOf(sign)), y, BlendedNoise.getDebugString(), BlendedNoise.getDebugString2());
        BlendedNoise.endDebugCapture();
        for (String name : new String[]{"minLimitNoise", "maxLimitNoise", "mainNoise"}) {
            var f = BlendedNoise.class.getDeclaredField(name); f.setAccessible(true);
            var stack = (PerlinNoise) f.get(base);
            double scale = 684.412 * .25 / (name.equals("mainNoise") ? 80 : 1);
            double sy = y * 684.412 * .125 / (name.equals("mainNoise") ? 160 : 1);
            double smear = 684.412 * .125 * 8 / (name.equals("mainNoise") ? 160 : 1);
            double sum = 0;
            for (int i = 0; i < (name.equals("mainNoise") ? 8 : 16); i++) {
                var noise = stack.getOctaveNoise(i);
                double value = NoiseSampling.sample(noise, p.blockX(), scale, 0, Int1088.ZERO, 1, sy,
                        p.blockZ(), scale, 0, -i, Math.scalb(smear, -i), Math.scalb(sy, -i));
                sum += Math.scalb(value, i);
                System.out.printf("OCTAVE %s %d value=%s sum=%s%n", name, i, value, sum);
                if (i < 2 && !Double.isFinite(value)) replay(noise, p, scale, sy, smear, i, value);
            }
        }
    }

    /** Replay the production operation order to expose the first overflowing intermediate. */
    private static void replay(ImprovedNoise noise, DensityFunction.SinglePointContext p, double scale,
            double sy, double smear, int octave, double expected) throws Exception {
        var x = new PreciseNoiseCoordinate.Split();
        var y = new PreciseNoiseCoordinate.Split();
        var z = new PreciseNoiseCoordinate.Split();
        WorldgenCoordinate.scale(p.blockX(), scale, -octave, x);
        WorldgenCoordinate.scale(Int1088.ZERO, 1, -octave, y);
        WorldgenCoordinate.scale(p.blockZ(), scale, -octave, z);
        var route = NoiseSampling.class.getDeclaredMethod("route", PreciseNoiseCoordinate.Split.class, double.class);
        route.setAccessible(true);
        route.invoke(null, x, noise.xo);
        route.invoke(null, y, Math.scalb(sy, -octave) + noise.yo);
        route.invoke(null, z, noise.zo);
        double smearScale = Math.scalb(smear, -octave), smearOffset = Math.scalb(sy, -octave);
        double input = smearOffset >= 0 && smearOffset < y.fraction ? smearOffset : y.fraction;
        double weirdY = y.fraction - Mth.floor(input / smearScale + (double)1.0E-7F) * smearScale;
        var permutation = ImprovedNoise.class.getDeclaredMethod("p", long.class);
        permutation.setAccessible(true);
        var grad = ImprovedNoise.class.getDeclaredMethod("gradDot", int.class, double.class, double.class, double.class);
        grad.setAccessible(true);
        double[] dots = new double[8];
        for (int bz = 0; bz < 2; bz++) for (int by = 0; by < 2; by++) for (int bx = 0; bx < 2; bx++) {
            int hash = (int)permutation.invoke(noise, x.lattice + bx);
            hash = (int)permutation.invoke(noise, hash + y.lattice + by);
            hash = (int)permutation.invoke(noise, hash + z.lattice + bz);
            dots[bz * 4 + by * 2 + bx] = (double)grad.invoke(null, hash, x.fraction - bx, weirdY - by, z.fraction - bz);
        }
        double fx = Mth.smoothstep(x.fraction), fy = Mth.smoothstep(y.fraction), fz = Mth.smoothstep(z.fraction);
        double[] a = new double[4];
        for (int i = 0; i < 4; i++) a[i] = Mth.lerp(fx, dots[2*i], dots[2*i+1]);
        double b0 = Mth.lerp(fy, a[0], a[1]), b1 = Mth.lerp(fy, a[2], a[3]);
        double result = Mth.lerp(fz, b0, b1);
        if (Double.doubleToLongBits(result) != Double.doubleToLongBits(expected)) throw new AssertionError("Replay mismatch");
        System.out.printf("REPLAY fraction=(%s,%s,%s) fade=(%s,%s,%s) dots=%s xLerps=%s yLerps=(%s,%s) zDifference=%s zProduct=%s result=%s%n",
                x.fraction, y.fraction, z.fraction, fx, fy, fz, java.util.Arrays.toString(dots), java.util.Arrays.toString(a),
                b0, b1, b1-b0, fz*(b1-b0), result);
    }

    private static int[] profile(BigInteger magnitude, boolean print) {
        Int1088 n = Int1088.parseDecimal(magnitude.multiply(BigInteger.valueOf(sign)).toString());
        int[] counts = new int[4];
        StringBuilder ys = new StringBuilder();
        for (int y = -64; y < 320; y++) {
            var p = new DensityFunction.SinglePointContext(alongX ? n : Int1088.ZERO, y,
                    alongX ? Int1088.ZERO : n);
            double v = base.compute(p);
            if (Double.isNaN(v)) { counts[0]++; ys.append(y).append(','); }
            else if (Double.isInfinite(v)) counts[1]++;
            if (Double.isNaN(state.router().finalDensity().compute(p))) counts[2]++;
            if (y % 8 == 0 && Double.isNaN(v)) counts[3]++;
        }
        if (print) System.out.printf("PROFILE %s%s n=%s baseNaN=%d baseInf=%d finalNaN=%d gridNaN=%d ys=%s%n",
                sign > 0 ? "+" : "-", alongX ? "X" : "Z", magnitude.multiply(BigInteger.valueOf(sign)),
                counts[0], counts[1], counts[2], counts[3], ys);
        return counts;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) args = new String[]{"survey"};
        Locale.setDefault(Locale.ROOT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS,
                CoordinatePrecision.PRECISE))) {
            state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                    BuiltinRegistries.NOISE, 0);
            base = state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                    new ResourceLocation("overworld/base_3d_noise")));
            for (boolean x : new boolean[]{true, false}) for (int s : new int[]{1, -1}) {
                alongX = x; sign = s;
                if (args.length > 5 && args[0].equals("search") && !args[5].equals((s > 0 ? "+" : "-") + (x ? "X" : "Z"))) continue;
                if (args[0].equals("walls")) {
                    BigInteger edge = new BigInteger(x ? "9176239949795123527441911776336772611302372340732"
                            : "32625691010195151058308609052507027565430190694040");
                    if (s < 0) edge = edge.add(BigInteger.ONE);
                    var column = DistantTerrainProbe.class.getDeclaredMethod("column", RandomState.class,
                            BigInteger.class, boolean.class, BigInteger.class, boolean.class);
                    column.setAccessible(true);
                    for (int d = -16; d <= 16; d++) {
                        BigInteger n = edge.add(BigInteger.valueOf(d)).multiply(BigInteger.valueOf(s));
                        int[] c = (int[]) column.invoke(null, state, n, x, BigInteger.ZERO, false);
                        System.out.printf("WALL_COLUMN %s%s delta=%d n=%s solid=%d interpolatedNaN=%d directFinalNaN=%d%n",
                                s > 0 ? "+" : "-", x ? "X" : "Z", d, n, c[0], c[2], c[3]);
                        if ((c[0] == 0) != (d >= 0)) throw new AssertionError("Local terrain wall");
                    }
                } else if (args[0].equals("trace")) {
                    trace(new BigInteger(args[1]), Integer.parseInt(args[2]));
                } else if (args[0].equals("edges")) {
                    BigInteger lo = new BigInteger(x ? "8000000000000000000000000000000000000000000000000"
                            : "16000000000000000000000000000000000000000000000000");
                    BigInteger hi = new BigInteger(x ? "9000000000000000000000000000000000000000000000000"
                            : "18000000000000000000000000000000000000000000000000");
                    search(0, 1, lo, hi);
                    search(3, 1, lo, hi);
                    if (x) search(0, 192, hi, new BigInteger("10000000000000000000000000000000000000000000000000"));
                } else if (args.length == 0 || args[0].equals("survey")) {
                    for (int e = 140; e <= 168; e++) for (int step = 0; step < 8; step++) {
                        BigInteger n = BigInteger.ONE.shiftLeft(e).multiply(BigInteger.valueOf(8 + step)).divide(BigInteger.valueOf(8));
                        int[] c = profile(n, false);
                        System.out.printf("SURVEY %s%s n=%s approx=%.9e baseNaN=%d baseInf=%d finalNaN=%d gridNaN=%d%n",
                                s > 0 ? "+" : "-", x ? "X" : "Z", n, n.doubleValue(), c[0], c[1], c[2], c[3]);
                    }
                } else if (args[0].equals("points")) {
                    for (int i = 1; i < args.length; i++) profile(new BigInteger(args[i]), true);
                } else if (args[0].equals("search")) {
                    int field = Integer.parseInt(args[1]), target = Integer.parseInt(args[2]);
                    BigInteger lo = new BigInteger(args[3]), hi = new BigInteger(args[4]);
                    search(field, target, lo, hi);
                } else throw new IllegalArgumentException("Unknown operation: " + args[0]);
            }
        }
    }
}
