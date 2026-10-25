import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.*;

/** Read-only End arithmetic catalog. Run with compiled project classes and server dependencies. */
public final class EndBoundaryProbe {
    private static final double F2 = (Math.sqrt(3.0) - 1.0) / 2.0;
    private static final BigInteger ONE = BigInteger.ONE;

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static BigInteger first(BigInteger hi, Predicate<BigInteger> broken) {
        BigInteger lo = BigInteger.ZERO;
        if (!broken.test(hi)) throw new AssertionError("Upper bound is healthy");
        while (hi.subtract(lo).compareTo(ONE) > 0) {
            BigInteger mid = lo.add(hi).shiftRight(1);
            if (broken.test(mid)) hi = mid; else lo = mid;
        }
        if (!broken.test(hi) || broken.test(hi.subtract(ONE))) throw new AssertionError("Neighbor check");
        return hi;
    }

    private static BigInteger ceilSqrt(BigInteger value) {
        BigInteger root = value.sqrt();
        return root.multiply(root).equals(value) ? root : root.add(ONE);
    }

    private static void ringBands() {
        System.out.println("| Rings transect | band | first NaN block | last NaN block | width | next finite width |");
        System.out.println("| --- | ---: | ---: | ---: | ---: | ---: |");
        for (int factor : new int[] {1, 2}) {
            for (int band = 0; band < 6; band++) {
                BigInteger base = BigInteger.valueOf(band).shiftLeft(64);
                BigInteger start = ceilSqrt(base.add(ONE.shiftLeft(63)).divide(BigInteger.valueOf(factor)));
                BigInteger end = ceilSqrt(base.add(ONE.shiftLeft(64)).divide(BigInteger.valueOf(factor)));
                BigInteger next = ceilSqrt(base.add(ONE.shiftLeft(64)).add(ONE.shiftLeft(63)).divide(BigInteger.valueOf(factor)));
                // Check the real Java long expression and both adjacent q samples.
                for (BigInteger q : new BigInteger[] {start.subtract(ONE), start, end.subtract(ONE), end}) {
                    boolean expected = q.compareTo(start) >= 0 && q.compareTo(end) < 0;
                    long v = q.longValueExact();
                    if ((v * v * factor < 0) != expected) throw new AssertionError("Ring band");
                }
                System.out.printf("| %s | %d | %,d | %,d | %,d | %,d |%n", factor == 1 ? "axis" : "X=Z", band + 1,
                        start.multiply(BigInteger.valueOf(8)), end.multiply(BigInteger.valueOf(8)).subtract(ONE),
                        end.subtract(start).multiply(BigInteger.valueOf(8)), next.subtract(end).multiply(BigInteger.valueOf(8)));
            }
        }
    }

    private static void simplex(boolean bit32, int diagonal, int sign, boolean sum) {
        Predicate<BigInteger> broken = n -> {
            double x = n.multiply(BigInteger.valueOf(sign)).doubleValue();
            double z = diagonal == 1 ? x : diagonal == -1 ? -x : 0;
            double skew = (x + z) * F2;
            double a = x + skew, b = z + skew;
            if (sum) {
                // Search this only before individual floor failure; the sum is an int only in BIT_32.
                return Math.floor(a) + Math.floor(b) > Integer.MAX_VALUE
                        || Math.floor(a) + Math.floor(b) < Integer.MIN_VALUE;
            }
            return bit32 ? (double) Mth.floor(a) != Math.floor(a) || (double) Mth.floor(b) != Math.floor(b)
                    : a >= 0x1p63 || a < -0x1p63 || b >= 0x1p63 || b < -0x1p63;
        };
        BigInteger edge = first(ONE.shiftLeft(bit32 ? 35 : 67), broken);
        System.out.printf("simplex %s %s diagonal=%d sign=%d input=%,d center-block=%,d%n",
                bit32 ? "32-lossy" : "64-lossy", sum ? "floor-sum" : "floor", diagonal, sign,
                edge.multiply(BigInteger.valueOf(sign)), edge.multiply(BigInteger.valueOf(16L * sign)));
    }

    private static void blended() throws Exception {
        Method route = NoiseSampling.class.getDeclaredMethod("route", PreciseNoiseCoordinate.Split.class, double.class);
        route.setAccessible(true);
        // End settings use the legacy seed source, unlike the Overworld.
        BlendedNoise noise = new BlendedNoise(new LegacyRandomSource(0), .25, .25, 80, 160, 4);
        for (String stack : new String[] {"minLimitNoise", "maxLimitNoise", "mainNoise"}) {
            ImprovedNoise octave = ((PerlinNoise) field(noise, stack)).getOctaveNoise(0);
            double scale = 684.412 * .25 / (stack.equals("mainNoise") ? 80 : 1);
            for (boolean precise : new boolean[] {false, true}) {
                try (var ignored = GenerationContext.open(new GeneratorConfig(
                        precise ? GeneratorMode.BIT_64 : GeneratorMode.BIT_32,
                        precise ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY))) {
                    System.out.printf("blended %s %s", precise ? "64-precise" : "32-lossy", stack);
                    for (double offset : new double[] {octave.xo, octave.zo}) {
                        for (int sign : new int[] {1, -1}) {
                            BigInteger edge = first(ONE.shiftLeft(70), n -> {
                                BigInteger block = n.multiply(BigInteger.valueOf(sign));
                                if (!precise) {
                                    double point = block.doubleValue() * (684.412 * .25);
                                    if (stack.equals("mainNoise")) point /= 80;
                                    point += offset;
                                    return (double) Mth.floor(point) != Math.floor(point);
                                }
                                var split = new PreciseNoiseCoordinate.Split();
                                WorldgenCoordinate.scale(block.shiftRight(64).longValue(), block.longValue(), scale, 0, split);
                                try { route.invoke(null, split, offset); }
                                catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                                return split.fraction < 0 || split.fraction >= 1;
                            });
                            System.out.printf(" | %,d", edge.multiply(BigInteger.valueOf(sign)));
                        }
                    }
                    System.out.println();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        blended();
        for (boolean bit32 : new boolean[] {true, false}) {
            for (int diagonal : new int[] {0, 1, -1}) {
                for (int sign : new int[] {1, -1}) {
                    simplex(bit32, diagonal, sign, false);
                    if (bit32 && diagonal != -1) simplex(true, diagonal, sign, true);
                }
            }
        }
        BigInteger square = first(ONE.shiftLeft(32), n -> n.multiply(n).bitLength() > 63);
        BigInteger diagonal = first(ONE.shiftLeft(32), n -> n.multiply(n).shiftLeft(1).bitLength() > 63);
        System.out.printf("rings axis q=%,d block=%,d diagonal q=%,d block=%,d%n", square, square.multiply(BigInteger.valueOf(8)), diagonal, diagonal.multiply(BigInteger.valueOf(8)));
        System.out.printf("lossy outer-center gate first window axis block=%,d diagonal block=%,d%n",
                square.subtract(BigInteger.valueOf(12)).multiply(BigInteger.valueOf(16)),
                diagonal.subtract(BigInteger.valueOf(12)).multiply(BigInteger.valueOf(16)));
        ringBands();
        for (String sizeAxis : new String[] {"X", "Z", "diagonal"}) {
            for (long n = 0; ; n++) {
                long x = sizeAxis.equals("Z") ? 0 : n, z = sizeAxis.equals("X") ? 0 : n;
                float actual = ((float) x * 3439F + (float) z * 147F) % 13F + 9F;
                float exact = (float) ((x * 3439L + z * 147L) % 13L) + 9F;
                if (actual != exact) {
                    System.out.printf("lossy island size axis=%s input=%,d center-block=%,d actual=%s exact=%s%n",
                            sizeAxis, n, n * 16, actual, exact);
                    break;
                }
            }
        }
        for (int bits : new int[] {24, 53}) {
            BigInteger n = ONE.shiftLeft(bits).add(ONE);
            boolean lost = bits == 24 ? (long) n.floatValue() != n.longValueExact()
                    : (long) n.doubleValue() != n.longValueExact();
            if (!lost) throw new AssertionError("Integer conversion");
            System.out.printf("candidate integer-to-%s first loss input=%,d center-block=%,d%n",
                    bits == 24 ? "float" : "double", n, n.multiply(BigInteger.valueOf(16)));
        }
        boolean previousRings = EndRingsMode.isEnabled();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.PRECISE))) {
            var density = DensityFunctions.endIslands(0L);
            for (boolean rings : new boolean[] {true, false}) {
                EndRingsMode.setEnabled(rings);
                for (boolean diag : new boolean[] {false, true}) {
                    long edge = (diag ? diagonal : square).longValueExact() * 8;
                    for (int sign : new int[] {1, -1}) {
                        double before = density.compute(new DensityFunction.SinglePointContext(sign * (edge - 1), 64, diag ? sign * (edge - 1) : 0));
                        double after = density.compute(new DensityFunction.SinglePointContext(sign * edge, 64, diag ? sign * edge : 0));
                        if (!Double.isFinite(before) || (rings ? !Double.isNaN(after) : !Double.isFinite(after))) throw new AssertionError("Ring density");
                        System.out.printf("density rings=%s diagonal=%s sign=%d before=%s after=%s%n", rings, diag, sign, before, after);
                    }
                }
            }
        } finally { EndRingsMode.setEnabled(previousRings); }
        // Exact split simplex deliberately ignores failureMagnitude and uses modulo lattice indices.
        var simplex = new SimplexNoise(new LegacyRandomSource(0));
        for (int exponent : new int[] {31, 53, 63, 64, 100, 123}) {
            for (int sign : new int[] {1, -1}) {
                BigInteger n = ONE.shiftLeft(exponent).multiply(BigInteger.valueOf(sign)).add(BigInteger.valueOf(123));
                double value = simplex.getValueAt(n.shiftRight(64).longValue(), n.longValue(), 0, 0);
                if (!Double.isFinite(value) || Math.abs(value) > 1) throw new AssertionError("Precise simplex " + n);
            }
        }
        System.out.println("precise simplex finite/range checks passed through input 2^123 (block scale 2^127)");
    }
}
