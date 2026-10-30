package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.*;

/** Seed-0 local transition verification only; does not run surface generation.
 * ./gradlew -I tools/blended-transition.init.gradle blendedTransitionProbe
 * Add -PtransitionArgs=--ranges for full-height finite/infinite/NaN ranges.
 */
public final class BlendedTransitionProbe {
    private static DensityFunction node(RandomState state, String name) {
        return state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                new ResourceLocation("overworld/" + name)));
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var column = DistantTerrainProbe.class.getDeclaredMethod("column", RandomState.class,
                BigInteger.class, boolean.class, BigInteger.class, boolean.class);
        column.setAccessible(true);
        try (var ignored = GenerationContext.open(new GeneratorConfig(
                GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
            var nodes = new LinkedHashMap<String, DensityFunction>();
            nodes.put("base", node(state, "base_3d_noise"));
            nodes.put("sloped", node(state, "sloped_cheese"));
            nodes.putAll(state.debugCaves(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                    new ResourceLocation("overworld/sloped_cheese"))));
            nodes.put("final", state.router().finalDensity());
            for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1}) {
                BigInteger edge = new BigInteger(x ? "9176239949795123527441911776336772611302372340732"
                        : "32625691010195151058308609052507027565430190694040");
                if (sign < 0) edge = edge.add(BigInteger.ONE);
                for (int delta : new int[]{-1, 0}) {
                    BigInteger n = edge.add(BigInteger.valueOf(delta)).multiply(BigInteger.valueOf(sign));
                    int[] c = (int[]) column.invoke(null, state, n, x, BigInteger.ZERO, true);
                    if (c[0] != (delta == 0 ? 0 : x ? 24 : 8))
                        throw new AssertionError("Local stone edge " + n);
                    for (var entry : nodes.entrySet()) {
                        int finite = 0, inf = 0, nan = 0;
                        StringBuilder ranges = new StringBuilder();
                        int start = -64, previous = -1;
                        for (int y = -64; y <= 320; y++) {
                            int kind = -1;
                            if (y < 320) {
                                var wide = Int1088.parseDecimal(n.toString());
                                double v = entry.getValue().compute(new DensityFunction.SinglePointContext(
                                        x ? wide : Int1088.ZERO, y, x ? Int1088.ZERO : wide));
                                kind = Double.isNaN(v) ? 2 : Double.isInfinite(v) ? 1 : 0;
                                if (kind == 0) finite++; else if (kind == 1) inf++; else nan++;
                            }
                            if (kind != previous) {
                                if (previous >= 0) ranges.append(start).append("..").append(y - 1)
                                        .append(':').append(new String[]{"F", "I", "N"}[previous]).append(' ');
                                start = y; previous = kind;
                            }
                        }
                        System.out.printf("PROFILE axis=%s%s delta=%d node=%s finite=%d inf=%d nan=%d ranges=%s%n",
                                sign > 0 ? "+" : "-", x ? "X" : "Z", delta,
                                entry.getKey(), finite, inf, nan,
                                args.length > 0 && args[0].equals("--ranges") ? ranges : "(use --ranges)");
                    }
                }
                if (sign > 0) {
                    // Corners of the final surviving cells and the first empty cell.
                    BigInteger grid = edge.shiftRight(2).shiftLeft(2);
                    for (int d : new int[]{-4, 0, 4}) for (int other : new int[]{0, 4})
                        for (int y : x ? new int[]{32, 40, 56, 64, 96, 104} : new int[]{-24, -16}) {
                            var wide = Int1088.parseDecimal(grid.add(BigInteger.valueOf(d)).toString());
                            var p = new DensityFunction.SinglePointContext(x ? wide : Int1088.fromLong(other),
                                    y, x ? Int1088.fromLong(other) : wide);
                            BlendedNoise.beginDebugCapture();
                            try {
                                double b = nodes.get("base").compute(p);
                                System.out.printf("CORNER axis=%s gridDelta=%d other=%d y=%d base=%s sloped=%s cheese=%s candidate=%s pillars=%s underground=%s final=%s %s%n",
                                        x ? "X" : "Z", d, other, y, b,
                                        nodes.get("sloped").compute(p), nodes.get("cheese_layer").compute(p),
                                        nodes.get("candidate").compute(p), nodes.get("gated_pillars").compute(p),
                                        nodes.get("underground").compute(p), nodes.get("final").compute(p),
                                        BlendedNoise.getDebugString2());
                            } finally { BlendedNoise.endDebugCapture(); }
                        }
                }
            }
            System.out.println("PASS: adjacent inward/outward columns on all four signed axes");
        }
    }
}
