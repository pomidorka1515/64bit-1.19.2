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
import net.minecraft.world.level.levelgen.synth.*;

/** Read-only, seed-0 default-Overworld experiments, stable shifts / precise.
 * See stage-investigation.md for commands and the limits of the evidence.
 * Edges are local, never global endpoints.
 */
public final class StageInvestigation {
    private static final BigInteger EXAMPLE = new BigInteger("10595886870649046156976715037193051892505510936576");
    private static Int1088 wide(BigInteger n) { return Int1088.parseDecimal(n.toString()); }
    private static DensityFunction.SinglePointContext point(BigInteger n, boolean x, int other, int y) {
        return new DensityFunction.SinglePointContext(wide(x ? n : BigInteger.valueOf(other)), y,
                wide(x ? BigInteger.valueOf(other) : n));
    }
    private static DensityFunction base(RandomState state) {
        return state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                new ResourceLocation("overworld/base_3d_noise")));
    }
    private static int kind(double v) {
        return Double.isNaN(v) ? 3 : v == Double.POSITIVE_INFINITY ? 1 : v == Double.NEGATIVE_INFINITY ? 2 : 0;
    }
    private static void baseSummary(DensityFunction base, BigInteger n, boolean x, int other) {
        int[] counts = new int[4]; double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int y = -64; y < 320; y++) {
            double v = base.compute(point(n, x, other, y)); counts[kind(v)]++;
            if (Double.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
        }
        System.out.printf("BASE axis=%s other=%d n=%s finite=%d +Inf=%d -Inf=%d NaN=%d finiteMin=%s finiteMax=%s y64=%s y136=%s%n",
                x ? "X" : "Z", other, n, counts[0], counts[1], counts[2], counts[3], min, max,
                base.compute(point(n, x, other, 64)), base.compute(point(n, x, other, 136)));
        for (int y : new int[]{64, 136}) {
            BlendedNoise.beginDebugCapture();
            try {
                base.compute(point(n, x, other, y));
                System.out.printf("BLEND_DETAIL axis=%s other=%d n=%s y=%d %s%n", x ? "X" : "Z", other, n, y,
                        BlendedNoise.getDebugString2());
            } finally { BlendedNoise.endDebugCapture(); }
        }
    }
    private static int[] column(RandomState state, BigInteger n, boolean x, int other, boolean print) throws Exception {
        var method = DistantTerrainProbe.class.getDeclaredMethod("column", RandomState.class, BigInteger.class,
                boolean.class, BigInteger.class, boolean.class);
        method.setAccessible(true);
        return (int[]) method.invoke(null, state, n, x, BigInteger.valueOf(other), print);
    }
    private static void chunk(RandomState state, BigInteger n, boolean x, int other) throws Exception {
        var method = DistantTerrainProbe.class.getDeclaredMethod("chunk", RandomState.class, BigInteger.class, BigInteger.class);
        method.setAccessible(true);
        method.invoke(null, state, x ? n : BigInteger.valueOf(other), x ? BigInteger.valueOf(other) : n);
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("cap-edges")) {
            for (String axis : new String[]{"X", "Z"}) main(new String[]{"edge", axis, "0", "1",
                    BigInteger.ONE.shiftLeft(71).toString(), BigInteger.ONE.shiftLeft(72).toString(), axis.equals("X") ? "112" : "-9"});
            return;
        }
        if (args[0].equals("early-edges")) {
            for (String axis : new String[]{"X", "-X", "Z", "-Z"}) {
                boolean x = axis.endsWith("X");
                main(new String[]{"edge", axis, "0", "1", BigInteger.ONE.shiftLeft(71).toString(),
                        BigInteger.ONE.shiftLeft(axis.startsWith("-") ? 72 : 73).toString(), x ? "111" : "-17"});
            }
            return;
        }
        if (args[0].equals("edges")) {
            for (String axis : new String[]{"X", "-X", "Z", "-Z"}) {
                boolean x = axis.endsWith("X");
                for (int field : new int[]{0, 2}) {
                    int e = field == 0 ? (x ? 162 : 164) : (x ? 175 : 176);
                    main(new String[]{"edge", axis, "0", Integer.toString(field),
                            BigInteger.ONE.shiftLeft(e).toString(), BigInteger.ONE.shiftLeft(e + 1).toString()});
                }
                int e = x ? 172 : 174;
                main(new String[]{"ice-edge", axis, "0", "0", BigInteger.ONE.shiftLeft(e).toString(),
                        BigInteger.ONE.shiftLeft(e + 1).toString()});
            }
            return;
        }
        Locale.setDefault(Locale.ROOT); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
            var base = base(state);
            if (args[0].equals("verify")) {
                String[][] onsets = {
                    {"X", "2319144373482458742508", "209", "193", "16"},
                    {"-X", "2319144370667437144697", "136", "34", "112"},
                    {"Z", "2319144373009074352936", "207", "207", "16"},
                    {"-Z", "2319144370667437144697", "207", "80", "240"}
                };
                for (String[] row : onsets) {
                    int sign = row[0].startsWith("-") ? -1 : 1;
                    boolean x = row[0].endsWith("X"); BigInteger outward = new BigInteger(row[1]);
                    int[] inward = column(state, outward.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(sign)), x, 0, true);
                    int[] after = column(state, outward.multiply(BigInteger.valueOf(sign)), x, 0, true);
                    if (inward[0] != Integer.parseInt(row[2]) || inward[2] != 0
                            || after[0] != Integer.parseInt(row[3]) || after[2] != Integer.parseInt(row[4]))
                        throw new AssertionError("Documented local pillar onset " + row[0]);
                }
                for (int sign : new int[]{1, -1}) {
                    BigInteger n = EXAMPLE.multiply(BigInteger.valueOf(sign));
                    if (!Double.isNaN(base.compute(point(n, true, 0, 64)))
                            || base.compute(point(n, false, 0, 64)) != Double.POSITIVE_INFINITY
                            || base.compute(point(n, false, 51, 64)) != 1.6259840439343172E302)
                        throw new AssertionError("The disputed magnitude is not a universal nonfinite wall");
                }
                String[][] edges = {
                    {"X", "9176239949795123527441911776336772611302372340732", "24", "296", "320"},
                    {"-X", "9176239949795123527441911776336772611302372340733", "24", "296", "320"},
                    {"Z", "32625691010195151058308609052507027565430190694040", "8", "312", "320"},
                    {"-Z", "32625691010195151058308609052507027565430190694041", "8", "312", "320"}
                };
                for (String[] row : edges) {
                    int sign = row[0].startsWith("-") ? -1 : 1;
                    boolean x = row[0].endsWith("X"); BigInteger outward = new BigInteger(row[1]);
                    int[] inward = column(state, outward.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(sign)), x, 0, true);
                    int[] after = column(state, outward.multiply(BigInteger.valueOf(sign)), x, 0, true);
                    if (inward[0] != Integer.parseInt(row[2]) || inward[2] != Integer.parseInt(row[3])
                            || after[0] != 0 || after[2] != Integer.parseInt(row[4])) throw new AssertionError("Local material edge " + row[0]);
                }
                for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1}) {
                    BigInteger edge = new BigInteger(x ? "9813013650811219171978361558676210588886640071279262"
                            : "24275893298308782329793273950574867788247414451134739");
                    if (!ice(state, edge.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(sign)), x, 0, true)
                            || ice(state, edge.multiply(BigInteger.valueOf(sign)), x, 0, true)) throw new AssertionError("Local ice strength edge");
                }
                BigInteger z = BigInteger.ONE.shiftLeft(174);
                int[] ice = surfaceColumn(state, z, false, -4096, true);
                int[] last = surfaceColumn(state, z.add(BigInteger.valueOf(15)), false, -4087, true);
                if (ice[0] != 187 || ice[1] != 134 || last[0] == 0 || last[1] != 74)
                    throw new AssertionError("Ice beyond the previous sparse-survey record");
                BigInteger gap = new BigInteger("40454776760706754934217473087500167505156363933681632");
                for (int delta : new int[]{-1, 0, 16}) {
                    int[] result = surfaceColumn(state, gap.add(BigInteger.valueOf(delta)), false, -4096, true);
                    int expected = delta == -1 ? 145 : delta == 0 ? 0 : 142;
                    if (result[0] != expected || !ice(state, gap.add(BigInteger.valueOf(delta)), false, -4096, false))
                        throw new AssertionError("Biome-gated ice disappears and returns despite unchanged positive ice strength");
                }
                System.out.println("PASS: four-direction local noise-fill losses, local ice gates, axis/height counterexample, farther actual surface ice, biome-gated recurrence");
            } else if (args[0].equals("inspect")) {
                boolean x = args[1].endsWith("X"); int other = Integer.parseInt(args[2]);
                int y = Integer.parseInt(args[3]);
                for (int i = 4; i < args.length; i++) {
                    BigInteger n = new BigInteger(args[i]);
                    var p = point(n, x, other, y);
                    BlendedNoise.beginDebugCapture();
                    try {
                        double value = base.compute(p);
                        System.out.printf("INSPECT axis=%s other=%d y=%d n=%s base=%s final=%s %s%n",
                                x ? "X" : "Z", other, y, n, value, state.router().finalDensity().compute(p),
                                BlendedNoise.getDebugString2());
                    } finally { BlendedNoise.endDebugCapture(); }
                    column(state, n, x, other, true);
                }
            } else if (args[0].equals("base")) {
                for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1})
                    for (int other : new int[]{0, 51, 131}) baseSummary(base, EXAMPLE.multiply(BigInteger.valueOf(sign)), x, other);
            } else if (args[0].equals("survey")) {
                int first = Integer.parseInt(args[1]), last = Integer.parseInt(args[2]);
                for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1})
                    for (int e = first; e <= last; e++) {
                        BigInteger n = BigInteger.ONE.shiftLeft(e).multiply(BigInteger.valueOf(sign));
                        column(state, n, x, 0, true); baseSummary(base, n, x, 0);
                    }
            } else if (args[0].equals("chunks")) {
                for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1})
                    for (int i = 1; i < args.length; i++) {
                        BigInteger n = args[i].startsWith("p") ? BigInteger.ONE.shiftLeft(Integer.parseInt(args[i].substring(1))) : new BigInteger(args[i]);
                        n = n.multiply(BigInteger.valueOf(sign));
                        System.out.printf("DIRECTION=%s%s%n", sign > 0 ? "+" : "-", x ? "X" : "Z");
                        chunk(state, n, x, 0);
                    }
            } else if (args[0].equals("surface-columns")) {
                boolean x = args[1].endsWith("X"); int other = Integer.parseInt(args[2]);
                for (int i = 3; i < args.length; i++) surfaceColumn(state, new BigInteger(args[i]), x, other, true);
            } else if (args[0].equals("late-search")) {
                for (boolean x : new boolean[]{true, false}) for (int exponent : new int[]{174, 175, 176, 178, 180, 182, 190, 200, 300, 1000}) {
                    BigInteger n = BigInteger.ONE.shiftLeft(exponent);
                    var biomes = net.minecraft.world.level.biome.MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME);
                    var manager = new net.minecraft.world.level.biome.BiomeManager(
                            (qx, qy, qz) -> biomes.getNoiseBiome(qx, qy, qz, state.sampler()),
                            net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(0));
                    int gates = 0, frozen = 0, generated = 0;
                    java.util.Map<String, Integer> seen = new java.util.LinkedHashMap<>();
                    for (int other = -4096; other <= 4096; other++) {
                        if (!ice(state, n, x, other, false)) continue;
                        gates++;
                        var p = point(n, x, other, -54);
                        var biome = manager.getBiome(new net.minecraft.core.BlockPos(p.blockX(), -54, p.blockZ()));
                        String name = biome.unwrapKey().orElseThrow().location().toString();
                        seen.merge(name, 1, Integer::sum);
                        if (name.equals("minecraft:frozen_ocean") || name.equals("minecraft:deep_frozen_ocean")) {
                            frozen++;
                            if (generated++ < 2) chunk(state, n, x, other);
                        }
                    }
                    System.out.printf("LATE_SEARCH axis=%s exponent=%d n=%s other=-4096..4096 iceGates=%d frozenQueries=%d biomes=%s%n",
                            x ? "X" : "Z", exponent, n, gates, frozen, seen);
                }
            } else if (args[0].equals("ice")) {
                for (boolean x : new boolean[]{true, false}) for (int sign : new int[]{1, -1})
                    for (int e = Integer.parseInt(args[1]); e <= Integer.parseInt(args[2]); e++) {
                        BigInteger n = BigInteger.ONE.shiftLeft(e).multiply(BigInteger.valueOf(sign));
                        ice(state, n, x, 0, true);
                    }
            } else if (args[0].equals("edge") || args[0].equals("ice-edge") || args[0].equals("surface-edge")) {
                boolean iceEdge = args[0].equals("ice-edge");
                boolean surfaceEdge = args[0].equals("surface-edge");
                boolean x = args[1].endsWith("X"); int sign = args[1].startsWith("-") ? -1 : 1;
                int other = Integer.parseInt(args[2]), field = Integer.parseInt(args[3]);
                int topLimit = args.length > 6 ? Integer.parseInt(args[6]) : 111;
                // field 0: empty noise-fill column; field 2: all heights interpolated NaN.
                BigInteger lo = new BigInteger(args[4]), hi = new BigInteger(args[5]);
                java.util.function.Predicate<BigInteger> failed = n -> {
                    try {
                        if (iceEdge) return !ice(state, n.multiply(BigInteger.valueOf(sign)), x, other, false);
                        if (surfaceEdge) return surfaceColumn(state, n.multiply(BigInteger.valueOf(sign)), x, other, false)[0] == 0;
                        int[] c = column(state, n.multiply(BigInteger.valueOf(sign)), x, other, false);
                        return field == 0 ? c[0] == 0 : field == 1 ? c[1] <= topLimit : c[2] == 384;
                    } catch (Exception e) { throw new RuntimeException(e); }
                };
                if (failed.test(lo) || !failed.test(hi)) throw new IllegalArgumentException("Not a healthy/failed bracket");
                while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
                    BigInteger mid = lo.add(hi).shiftRight(1);
                    if (failed.test(mid)) hi = mid; else lo = mid;
                }
                System.out.printf("LOCAL_EDGE kind=%s axis=%s other=%d field=%d inward=%s outward=%s%n", args[0], args[1], other, field,
                        lo.multiply(BigInteger.valueOf(sign)), hi.multiply(BigInteger.valueOf(sign)));
                boolean monotonic = true;
                for (int d = -16; d <= 16; d++) {
                    BigInteger n = hi.add(BigInteger.valueOf(d));
                    if (failed.test(n) != (d >= 0)) monotonic = false;
                    if (iceEdge) ice(state, n.multiply(BigInteger.valueOf(sign)), x, other, true);
                    else if (surfaceEdge) surfaceColumn(state, n.multiply(BigInteger.valueOf(sign)), x, other, true);
                    else column(state, n.multiply(BigInteger.valueOf(sign)), x, other, true);
                }
                System.out.println("MONOTONIC_LOCAL_WINDOW=" + monotonic);
            } else throw new IllegalArgumentException("See tools/stage-investigation.md for supported operations");
        }
    }

    private static boolean ice(RandomState state, BigInteger n, boolean x, int other, boolean print) {
        var p = point(n, x, other, 0);
        double[] raw = new double[3], clamped = new double[3]; int i = 0;
        for (var key : java.util.List.of(Noises.ICEBERG_SURFACE, Noises.ICEBERG_PILLAR, Noises.ICEBERG_PILLAR_ROOF)) {
            double scale = i == 0 ? 1 : i == 1 ? 1.28 : 1.17;
            raw[i] = SurfaceSystem.sampleNoise(state.getOrCreateNoise(key), p.blockX(), scale, p.blockZ());
            clamped[i] = net.minecraft.world.level.WorldBounds.clampNoise(raw[i]); i++;
        }
        double strength = Math.min(Math.abs(clamped[0] * 8.25), clamped[1] * 15);
        double roof = Math.ceil(Math.abs(clamped[2] * 1.5) * 40) + 14;
        if (print) System.out.printf("ICE_GATE axis=%s other=%d n=%s allowed=%s strength=%s roofCap=%s raw=%s%n",
                x ? "X" : "Z", other, n, strength > 1.8, strength, roof, java.util.Arrays.toString(raw));
        return strength > 1.8;
    }

    private static int[] surfaceColumn(RandomState state, BigInteger n, boolean x, int other, boolean print) {
        var p = point(n, x, other, 0);
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes = net.minecraft.world.level.biome.MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE, biomes, settings);
        var pos = new net.minecraft.world.level.ChunkPos(new net.minecraft.core.BlockPos(p.blockX(), 0, p.blockZ()));
        var height = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
        var chunk = new net.minecraft.world.level.chunk.ProtoChunk(pos, net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                height, BuiltinRegistries.BIOME, null);
        var noiseChunk = chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, DensityFunctions.BeardifierMarker.INSTANCE,
                settings.value(), (px, py, pz) -> py < -54 ? new Aquifer.FluidStatus(-54, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()),
                net.minecraft.world.level.levelgen.blending.Blender.empty()));
        generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state, net.minecraft.world.level.levelgen.blending.Blender.empty(), null, chunk).join();
        generator.fillFromNoise(Runnable::run, net.minecraft.world.level.levelgen.blending.Blender.empty(), state, null, chunk).join();
        int noiseSolids = 0;
        for (int y = -64; y < 320; y++) {
            var block = chunk.getBlockState(new net.minecraft.core.BlockPos(p.blockX(), y, p.blockZ()));
            if (!block.isAir() && block.getFluidState().isEmpty()) noiseSolids++;
        }
        String[] surfaceBiome = {null};
        var manager = new net.minecraft.world.level.biome.BiomeManager(
                (qx, qy, qz) -> biomes.getNoiseBiome(qx, qy, qz, state.sampler()),
                net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(0)) {
            @Override
            public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getBiome(net.minecraft.core.BlockPos pos) {
                var biome = super.getBiome(pos);
                if (surfaceBiome[0] == null && pos.getX().equals(p.blockX()) && pos.getZ().equals(p.blockZ()))
                    surfaceBiome[0] = biome.unwrapKey().orElseThrow().location().toString();
                return biome;
            }
        };
        state.surfaceSystem().buildSurface(state, manager, BuiltinRegistries.BIOME, false,
                new WorldGenerationContext(generator, height), chunk, noiseChunk, settings.value().surfaceRule());
        int count = 0, top = -65; java.util.Map<String, Integer> materials = new java.util.LinkedHashMap<>();
        for (int y = -64; y < 320; y++) {
            var block = chunk.getBlockState(new net.minecraft.core.BlockPos(p.blockX(), y, p.blockZ()));
            if (!block.isAir() && block.getFluidState().isEmpty()) {
                count++; top = y; materials.merge(Registry.BLOCK.getKey(block.getBlock()).toString(), 1, Integer::sum);
            }
        }
        if (print) {
            String topBlock = count == 0 ? "none" : Registry.BLOCK.getKey(chunk.getBlockState(
                    new net.minecraft.core.BlockPos(p.blockX(), top, p.blockZ())).getBlock()).toString();
            System.out.printf("SURFACE_COLUMN axis=%s other=%d n=%s solid=%d top=%d materials=%s noiseSolids=%d surfaceBiome=%s topBlock=%s%n",
                    x ? "X" : "Z", other, n, count, top, materials, noiseSolids, surfaceBiome[0], topBlock);
            ice(state, n, x, other, true);
        }
        return new int[]{count, top};
    }
}
