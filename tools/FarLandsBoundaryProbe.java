import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.PreciseNoiseCoordinate;
import net.minecraft.world.level.levelgen.synth.NoiseSampling;

/** Run against compiled project classes and the standalone server's dependencies. */
public final class FarLandsBoundaryProbe {
    private static boolean precise;
    private static boolean extrapolation;
    private static final Method ROUTE;
    static {
        try {
            ROUTE = NoiseSampling.class.getDeclaredMethod("route", PreciseNoiseCoordinate.Split.class, double.class);
            ROUTE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static PreciseNoiseCoordinate.Split routed(BigInteger block, double scale, double factor, double octave, double offset) {
        var split = new PreciseNoiseCoordinate.Split();
        WorldgenCoordinate.scale(block.shiftRight(64).longValue(), block.longValue(),
                scale * factor, Math.getExponent(octave), split);
        try {
            ROUTE.invoke(null, split, offset);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return split;
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static double sample(long block, double scale, double factor, double octave, double offset) {
        // Preserve DensityFunctions -> NormalNoise -> PerlinNoise -> ImprovedNoise order.
        return (((double) block * scale) * factor) * octave + offset;
    }

    private static boolean broken(long block, double scale, double factor, double octave, double offset) {
        double point = sample(block, scale, factor, octave, offset);
        return (double) Mth.floor(point) != Math.floor(point);
    }

    private static long boundary(int sign, double scale, double factor, double octave, double offset) {
        long lo = 0, hi = 100_000_000_000L;
        while (hi - lo > 1) {
            long mid = lo + (hi - lo) / 2;
            if (broken(sign * mid, scale, factor, octave, offset)) hi = mid;
            else lo = mid;
        }
        long result = sign * hi;
        if (!broken(result, scale, factor, octave, offset)
                || broken(result - sign, scale, factor, octave, offset)) throw new AssertionError();
        return result;
    }

    private static double sample64(BigInteger block, double scale, double factor, double octave, double offset) {
        double coordinate = WorldgenCoordinate.toDouble(block.shiftRight(64).longValue(), block.longValue());
        return ((coordinate * scale) * factor) * octave + offset;
    }

    private static boolean broken64(BigInteger block, double scale, double factor, double octave, double offset) {
        if (precise) {
            if (extrapolation) {
                var split = routed(block, scale, factor, octave, offset);
                return split.fraction < 0.0 || split.fraction >= 1.0;
            }
            var split = new PreciseNoiseCoordinate.Split();
            WorldgenCoordinate.scale(block.shiftRight(64).longValue(), block.longValue(),
                    scale * factor, Math.getExponent(octave), split);
            long carry = (long) Math.floor(split.fraction + offset);
            long cell = split.lattice + carry;
            return !Double.isNaN(split.failureMagnitude)
                    || ((split.lattice ^ cell) & (carry ^ cell)) < 0;
        }
        double point = sample64(block, scale, factor, octave, offset);
        // A double cast of Long.MAX_VALUE rounds back to 2^63, hiding positive failure.
        return point >= 0x1.0p63 || point < -0x1.0p63;
    }

    private static BigInteger boundary64(int sign, double scale, double factor, double octave, double offset) {
        BigInteger lo = BigInteger.ZERO, hi = BigInteger.ONE.shiftLeft(110);
        BigInteger direction = BigInteger.valueOf(sign);
        if (!broken64(hi.multiply(direction), scale, factor, octave, offset))
            throw new AssertionError("Search upper bound is healthy");
        while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
            BigInteger mid = lo.add(hi).shiftRight(1);
            if (broken64(mid.multiply(direction), scale, factor, octave, offset)) hi = mid;
            else lo = mid;
        }
        BigInteger result = hi.multiply(direction);
        if (!broken64(result, scale, factor, octave, offset)
                || broken64(result.subtract(direction), scale, factor, octave, offset)) throw new AssertionError();
        return result;
    }

    private static void densityCheck() {
        var state = net.minecraft.world.level.levelgen.RandomState.create(
                net.minecraft.data.BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                        net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD),
                net.minecraft.data.BuiltinRegistries.NOISE, 0L);
        BigInteger edge = new BigInteger("18118315395839352631");
        NormalNoise cheese = state.getOrCreateNoise(net.minecraft.world.level.levelgen.Noises.CAVE_CHEESE);
        for (long z : new long[] {0, 218, 4820284}) {
            for (int y : new int[] {64, 232, 240, 248}) {
                for (int dx = -7; dx <= 5; ++dx) {
                    BigInteger x = edge.add(BigInteger.valueOf(dx));
                    var context = new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(
                            x.shiftRight(64).longValue(), x.longValue(), y, 0L, z);
                    double raw = cheese.getValueScaled(context.blockXHi(), context.blockX(), 1.0, y,
                            2.0 / 3.0, 0L, z);
                    System.out.printf("density x=%s y=%d z=%d cheese=%.9g final=%.9g%n",
                            x, y, z, raw, state.router().finalDensity().compute(context));
                }
            }
        }
    }

    private static BigInteger checkedWall(ImprovedNoise noise, double scale, double factor, int octave) {
        BigInteger expected = null;
        for (double offset : new double[] {noise.xo, noise.zo}) {
            for (int sign : new int[] {1, -1}) {
                BigInteger value = boundary64(sign, scale, factor, Math.scalb(1.0, octave), offset).abs();
                if (expected != null && !expected.equals(value))
                    throw new AssertionError("Signed axes differ; cannot compress table");
                expected = value;
            }
        }
        return expected;
    }

    private static String signedWalls(ImprovedNoise noise, double scale, double factor, int octave) {
        StringBuilder result = new StringBuilder();
        for (double offset : new double[] {noise.xo, noise.zo}) {
            for (int sign : new int[] {1, -1}) {
                result.append(String.format(Locale.ROOT, " | %,d",
                        boundary64(sign, scale, factor, Math.scalb(1.0, octave), offset)));
            }
        }
        return result.toString();
    }

    private static void catalog() throws Exception {
        String[] specs = {
            "temperature:0.25", "vegetation:0.25", "continentalness:0.25", "erosion:0.25", "ridge:0.25",
            "temperature_large:0.25", "vegetation_large:0.25", "continentalness_large:0.25", "erosion_large:0.25",
            "offset:0.25", "aquifer_barrier:0.5", "aquifer_fluid_level_floodedness:0.67",
            "aquifer_fluid_level_spread:0.7142857142857143", "aquifer_lava:1", "cave_cheese:1", "pillar:25",
            "pillar_rareness:1", "pillar_thickness:1", "spaghetti_2d_elevation:1", "spaghetti_3d_thickness:1",
            "spaghetti_roughness:1", "spaghetti_roughness_modulator:1", "cave_layer:1", "noodle:1", "noodle_thickness:1",
            "spaghetti_2d_modulator:2", "spaghetti_2d_thickness:2", "spaghetti_3d_rarity:2",
            "cave_entrance:0.75", "ore_veininess:1.5", "ore_vein_a:4", "ore_vein_b:4", "ore_gap:1",
            "noodle_ridge_a:2.6666666666666665", "noodle_ridge_b:2.6666666666666665", "jagged:1500",
            "surface:1", "surface_secondary:1", "clay_bands_offset:1", "badlands_pillar:0.2",
            "badlands_pillar_roof:0.75", "badlands_surface:1", "iceberg_surface:1", "iceberg_pillar:1.28",
            "iceberg_pillar_roof:1.17", "surface_swamp:1", "calcite:1", "gravel:1", "packed_ice:1",
            "powder_snow:1", "nether_state_selector:1", "ice:1", "soul_sand_layer:1", "gravel_layer:1",
            "patch:1", "netherrack:1", "nether_wart:1"
        };
        boolean bit32 = GenerationContext.current().mode().uses32BitArithmetic();
        System.out.println(bit32 ? "| Input / stack | horizontal scale | +X | −X | +Z | −Z |"
                : "| Input | horizontal scale | second stack N | first stack N |");
        System.out.println(bit32 ? "| --- | ---: | ---: | ---: | ---: | ---: |" : "| --- | ---: | ---: | ---: |");
        for (String spec : specs) {
            String[] parts = spec.split(":");
            catalogRow(parts[0], Double.parseDouble(parts[1]));
        }
        for (String name : new String[] {"spaghetti_2d", "spaghetti_3d_1", "spaghetti_3d_2"}) {
            for (double rarity : name.equals("spaghetti_2d") ? new double[] {.5, .75, 1, 2, 3}
                    : new double[] {.75, 1, 1.5, 2}) catalogRow(name, 1.0 / rarity);
        }
        var terrain = new net.minecraft.world.level.levelgen.synth.BlendedNoise(
                new XoroshiroRandomSource(0L).forkPositional().fromHashOf("minecraft:terrain"),
                .25, .125, 80, 160, 8);
        for (String stack : new String[] {"minLimitNoise", "maxLimitNoise", "mainNoise"}) {
            var perlin = (PerlinNoise) field(terrain, stack);
            double scale = 684.412 * .25 / (stack.equals("mainNoise") ? 80 : 1);
            if (bit32) System.out.printf("| blended / %s | %s%s |%n", stack, scale,
                    signedWalls(perlin.getOctaveNoise(0), scale, 1, 0));
            else System.out.printf("| blended / %s | %s | %,d | — |%n", stack, scale,
                    checkedWall(perlin.getOctaveNoise(0), scale, 1, 0));
        }
    }

    private static void catalogRow(String name, double scale) throws Exception {
        var parameters = net.minecraft.data.BuiltinRegistries.NOISE.get(
                new net.minecraft.resources.ResourceLocation(name));
        if (parameters == null) throw new IllegalArgumentException(name);
        var normal = NormalNoise.create(new XoroshiroRandomSource(0L).forkPositional()
                .fromHashOf("minecraft:" + name), parameters);
        BigInteger[] walls = new BigInteger[2];
        int column = 0;
        for (String stack : new String[] {"second", "first"}) {
            var levels = (ImprovedNoise[]) field(field(normal, stack), "noiseLevels");
            int index = levels.length - 1;
            while (levels[index] == null) --index;
            if (GenerationContext.current().mode().uses32BitArithmetic()) {
                System.out.printf("| %s / %s | %s%s |%n", name, stack, scale,
                        signedWalls(levels[index], scale, stack.equals("second") ? 1.0181268882175227 : 1,
                                parameters.firstOctave() + index));
                continue;
            }
            walls[column++] = checkedWall(levels[index], scale,
                    stack.equals("second") ? 1.0181268882175227 : 1, parameters.firstOctave() + index);
        }
        if (!GenerationContext.current().mode().uses32BitArithmetic())
            System.out.printf("| %s | %s | %,d | %,d |%n", name, scale, walls[0], walls[1]);
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        boolean bit64 = args.length > 0 && args[0].equals("64");
        precise = args.length > 1 && args[1].equals("precise");
        extrapolation = args.length > 2 && args[2].equals("extrapolation");
        if (args.length > 2 && args[2].equals("catalog")) extrapolation = true;
        try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(
                bit64 ? GeneratorMode.BIT_64 : GeneratorMode.BIT_32,
                precise ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY))) {
        if (args.length > 2 && args[2].equals("catalog")) {
            catalog();
            return;
        }
        if (args.length > 2 && args[2].equals("density")) {
            densityCheck();
            return;
        }
        for (String name : new String[] {"cave_cheese", "aquifer_lava", "pillar", "ice"}) {
            int first = name.equals("cave_cheese") ? -8 : name.equals("pillar") ? -7 : name.equals("ice") ? -4 : -1;
            double[] amplitudes = name.equals("cave_cheese") ? new double[] {.5, 1, 2, 1, 2, 1, 0, 2, 0}
                    : name.equals("pillar") ? new double[] {1, 1} : name.equals("ice") ? new double[] {1, 1, 1, 1} : new double[] {1};
            var root = new XoroshiroRandomSource(0L).forkPositional();
            NormalNoise normal = NormalNoise.create(root.fromHashOf("minecraft:" + name), first, amplitudes);
            double scale = name.equals("pillar") ? 25.0 : 1.0;
            for (String stack : new String[] {"second", "first"}) {
                PerlinNoise perlin = (PerlinNoise) field(normal, stack);
                ImprovedNoise[] levels = (ImprovedNoise[]) field(perlin, "noiseLevels");
                int index = levels.length - 1;
                while (levels[index] == null) --index;
                ImprovedNoise noise = levels[index];
                double factor = stack.equals("second") ? 1.0181268882175227 : 1.0;
                double octave = Math.scalb(1.0, first + index);
                System.out.printf("%s %s octave=%d xo=%.17g zo=%.17g%n", name, stack, first + index, noise.xo, noise.zo);
                for (int axis = 0; axis < 2; ++axis) {
                    double offset = axis == 0 ? noise.xo : noise.zo;
                    for (int sign : new int[] {1, -1}) {
                        if (bit64 || precise) {
                            BigInteger b = boundary64(sign, scale, factor, octave, offset);
                            if (precise) {
                                var split = new PreciseNoiseCoordinate.Split();
                                WorldgenCoordinate.scale(b.shiftRight(64).longValue(), b.longValue(),
                                        scale * factor, Math.getExponent(octave), split);
                                System.out.printf("  %s%s %,d adjacent=%,d pre-route-cell=%d fraction=%.17g failure=%s%n",
                                        sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", b,
                                        b.subtract(BigInteger.valueOf(sign)), split.lattice, split.fraction,
                                        Double.toHexString(split.failureMagnitude));
                                for (int delta : new int[] {-sign, 0, sign}) {
                                    BigInteger atEdge = b.add(BigInteger.valueOf(delta));
                                    var result = routed(atEdge, scale, factor, octave, offset);
                                    System.out.printf("    %,d routed-cell=%d fraction=%.17g%n", atEdge, result.lattice, result.fraction);
                                }
                                BigInteger at = b.add(BigInteger.valueOf(sign * 16384L));
                                BigInteger x = axis == 0 ? at : BigInteger.ZERO;
                                BigInteger z = axis == 1 ? at : BigInteger.ZERO;
                                System.out.printf("    distance=16384 y=64 raw=%.9g%n", normal.getValueScaled(
                                        x.shiftRight(64).longValue(), x.longValue(), scale, 64,
                                        name.equals("pillar") ? .3 : name.equals("cave_cheese") ? 2.0 / 3.0 : 1.0,
                                        z.shiftRight(64).longValue(), z.longValue()));
                                continue;
                            }
                            double point = sample64(b, scale, factor, octave, offset);
                            System.out.printf("  %s%s %,d adjacent=%,d point=%s floor=%d%n",
                                    sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", b,
                                    b.subtract(BigInteger.valueOf(sign)), Double.toHexString(point), Mth.lfloor(point));
                            if (stack.equals("second") && (name.equals("pillar") || name.equals("cave_cheese"))) {
                                BigInteger at = b.add(BigInteger.valueOf(sign * 16384L));
                                double coordinate = WorldgenCoordinate.toDouble(at.shiftRight(64).longValue(), at.longValue());
                                double yScale = name.equals("pillar") ? .3 : 2.0 / 3.0;
                                System.out.printf("    distance=16384 y=64 raw=%.9g%n", normal.getValue(
                                        axis == 0 ? coordinate * scale : 0, 64 * yScale, axis == 1 ? coordinate * scale : 0));
                            }
                            continue;
                        }
                        long b = boundary(sign, scale, factor, octave, offset);
                        System.out.printf("  %s%s %,d adjacent=%,d point=%.17g floor=%d%n", sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", b, b - sign,
                                sample(b, scale, factor, octave, offset), Mth.floor(sample(b, scale, factor, octave, offset)));
                        if (stack.equals("second") && (name.equals("pillar") || name.equals("cave_cheese"))) {
                            for (int distance : new int[] {0, 4, 16, 256, 4096}) {
                                long at = b + sign * (long) distance;
                                double yScale = name.equals("pillar") ? .3 : 2.0 / 3.0;
                                System.out.printf("    distance=%d y=64 raw=%.9g%n", distance,
                                        normal.getValue(axis == 0 ? at * scale : 0, 64 * yScale, axis == 1 ? at * scale : 0));
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
