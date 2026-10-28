package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.*;

/** Read-only seed-0 survey. Reports sampled failures, never assumes global monotonicity. */
public final class InfinityProbe {
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        boolean stableOffsetCheck = args.length == 1 && args[0].equals("--stable-offset-check");
        // Optional: MODE MIN_EXP MAX_EXP STEPS (defaults preserve the historical survey).
        int minExponent = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int maxExponent = args.length > 2 ? Integer.parseInt(args[2]) : 126;
        int steps = args.length > 3 ? Integer.parseInt(args[3]) : 16;
        if (minExponent < 4 || maxExponent > 1086 || minExponent > maxExponent || steps < 1)
            throw new IllegalArgumentException("Expected 4 <= MIN_EXP <= MAX_EXP <= 1086, STEPS >= 1");
        BigInteger threshold = BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE.shiftLeft(970));
        if (!Double.isFinite(threshold.subtract(BigInteger.ONE).doubleValue())
                || threshold.doubleValue() != Double.POSITIVE_INFINITY) throw new AssertionError("double boundary");
        System.out.println("DOUBLE_MAX=" + BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE.shiftLeft(971)));
        System.out.println("DOUBLE_OVERFLOW=" + threshold);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (GeneratorMode mode : new GeneratorMode[]{GeneratorMode.BIT_32, GeneratorMode.BIT_64,
                GeneratorMode.BIT_32_STABLE_SHIFTS, GeneratorMode.BIT_64_STABLE_SHIFTS}) {
            if (stableOffsetCheck && mode != GeneratorMode.BIT_64_STABLE_SHIFTS) continue;
            if (args.length > 1 && !mode.name().equals(args[0])) continue;
            try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.PRECISE))) {
                var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
                Map<String, DensityFunction> nodes = new LinkedHashMap<>();
                for (var key : BuiltinRegistries.NOISE.keySet()) {
                    var noise = state.getOrCreateNoise(ResourceKey.create(Registry.NOISE_REGISTRY, key));
                    // Raw unit-scale references, not the call-site world-coordinate scales.
                    nodes.put("raw/" + key.getPath(), new DensityFunction.SimpleFunction() {
                        public double compute(DensityFunction.FunctionContext p) {
                            return noise.getValueScaled(ContextCoordinates.copy(p, true), 1, p.blockY(), 1, ContextCoordinates.copy(p, false));
                        }
                        public double minValue() { return Double.NEGATIVE_INFINITY; }
                        public double maxValue() { return Double.POSITIVE_INFINITY; }
                        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException(); }
                    });
                }
                for (var key : BuiltinRegistries.DENSITY_FUNCTION.keySet()) {
                    if (key.getPath().startsWith("overworld/") || key.getPath().equals("shift_x")
                            || key.getPath().equals("shift_z"))
                        nodes.put(key.getPath(), state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, key)));
                }
                state.debugCaves(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                        new net.minecraft.resources.ResourceLocation("overworld/sloped_cheese")))
                        .forEach((name, function) -> nodes.put("cave_branch/" + name, function));
                // Include pre-clamp cave arithmetic: clamps otherwise conceal infinity.
                var layer = new DensityFunctions.Noise(new DensityFunction.NoiseHolder(
                        BuiltinRegistries.NOISE.getHolderOrThrow(Noises.CAVE_LAYER),
                        state.getOrCreateNoise(Noises.CAVE_LAYER)), 1, 8);
                nodes.put("cave_layer/square_times_four", DensityFunctions.mul(DensityFunctions.constant(4), layer.square()));
                nodes.put("router/temperature", state.router().temperature());
                nodes.put("router/vegetation", state.router().vegetation());
                nodes.put("router/continents", state.router().continents());
                nodes.put("router/erosion", state.router().erosion());
                nodes.put("router/depth", state.router().depth());
                nodes.put("router/ridges", state.router().ridges());
                nodes.put("router/final", state.router().finalDensity());
                nodes.put("router/barrier", state.router().barrierNoise());
                nodes.put("router/floodedness", state.router().fluidLevelFloodednessNoise());
                nodes.put("router/spread", state.router().fluidLevelSpreadNoise());
                nodes.put("router/lava", state.router().lavaNoise());
                nodes.put("router/initial", state.router().initialDensityWithoutJaggedness());
                nodes.put("router/vein_toggle", state.router().veinToggle());
                nodes.put("router/vein_ridged", state.router().veinRidged());
                nodes.put("router/vein_gap", state.router().veinGap());
                if (stableOffsetCheck) {
                    BigInteger magnitude = BigInteger.ONE.shiftLeft(70);
                    for (int axis = 0; axis < 2; axis++) for (int sign : new int[]{1, -1}) {
                        BigInteger n = magnitude.multiply(BigInteger.valueOf(sign));
                        Int1088 coordinate = Int1088.parseDecimal(n.toString());
                        var p = new DensityFunction.SinglePointContext(axis == 0 ? coordinate : Int1088.ZERO, 64,
                                axis == 1 ? coordinate : Int1088.ZERO);
                        for (String field : new String[]{"overworld/offset", "overworld/depth", "overworld/sloped_cheese"})
                            System.out.printf("%s %s%s %s N=%s value=%s%n", mode, sign > 0 ? "+" : "-",
                                    axis == 0 ? "X" : "Z", field, magnitude, nodes.get(field).compute(p));
                    }
                    continue;
                }
                for (int axis = 0; axis < 2; axis++) for (int sign : new int[]{1, -1}) {
                    Map<String, Boolean> seen = new LinkedHashMap<>();
                    // Configurable samples per power of two; no claim of a first failing block.
                    for (int exponent = minExponent; exponent <= maxExponent; exponent++) for (int step = 0; step < steps; step++) {
                        BigInteger n = BigInteger.ONE.shiftLeft(exponent).multiply(BigInteger.valueOf((long)steps + step))
                                .divide(BigInteger.valueOf(steps));
                        n = n.multiply(BigInteger.valueOf(sign));
                        Int1088 coordinate = Int1088.parseDecimal(n.toString());
                        var p = new DensityFunction.SinglePointContext(axis == 0 ? coordinate : Int1088.ZERO, 64,
                                axis == 1 ? coordinate : Int1088.ZERO);
                        for (var entry : nodes.entrySet()) {
                            double value = entry.getValue().compute(p);
                            String kind = Double.isNaN(value) ? "NaN" : Double.isInfinite(value) ? "Infinity" : null;
                            if (kind != null && seen.putIfAbsent(entry.getKey() + kind, true) == null)
                                System.out.printf("%s %s%s %s %s %.8e %s value=%s%n", mode, sign > 0 ? "+" : "-", axis == 0 ? "X" : "Z", entry.getKey(), kind, n.abs().doubleValue(), n, value);
                        }
                    }
                }
            }
        }
    }
}
