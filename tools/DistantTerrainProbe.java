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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.*;

/** Seed-0 full-width diagnostics; noise fill only, never a claim of a global last block.
 * MODE point X Y Z | columns AXIS OTHER N... | survey AXIS OTHER MIN_EXP MAX_EXP STEPS
 * | edge AXIS OTHER SOLID_N EMPTY_N (local bracket, then exhaustive +/-16 verification)
 * | nan-edge AXIS OTHER PARTIAL_N ALL_NAN_N (same, for all 384 interpolated heights)
 * | chunk X Z (real biome/noise/surface stages; no structures, carvers or features)
 * | chunks AXIS OTHER MIN_EXP MAX_EXP STEPS (AXIS can be X, Z, -X, -Z)
 * | verify-collapse (stable-shifts climate infinity and surface-only ice regression).
 */
public final class DistantTerrainProbe {
    private static Int1088 wide(BigInteger n) { return Int1088.parseDecimal(n.toString()); }

    private static int[] column(RandomState state, BigInteger n, boolean alongX, BigInteger other, boolean print) {
        BigInteger x = alongX ? n : other, z = alongX ? other : n;
        BigInteger ox = x.shiftRight(4).shiftLeft(4), oz = z.shiftRight(4).shiftLeft(4);
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var chunk = new NoiseChunk(4, state, wide(ox), wide(oz), settings.noiseSettings(),
                DensityFunctions.BeardifierMarker.INSTANCE, settings,
                (px, py, pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var density = state.router().finalDensity().mapAll(chunk::wrap);
        int lx = x.subtract(ox).intValueExact(), lz = z.subtract(oz).intValueExact();
        chunk.initializeForFirstCellX();
        for (int cx = 0; cx <= lx / 4; cx++) {
            chunk.advanceCellX(cx);
            if (cx < lx / 4) chunk.swapSlices();
        }
        int solid = 0, nan = 0, top = -65, directNan = 0;
        StringBuilder heights = new StringBuilder();
        for (int cy = 47; cy >= 0; cy--) {
            chunk.selectCellYZ(cy, lz / 4);
            for (int dy = 7; dy >= 0; dy--) {
                int y = -64 + cy * 8 + dy;
                chunk.updateForY(y, dy / 8.0);
                chunk.updateForX(wide(x), (lx % 4) / 4.0);
                chunk.updateForZ(wide(z), (lz % 4) / 4.0);
                if (Double.isNaN(density.compute(chunk))) nan++;
                if (Double.isNaN(state.router().finalDensity().compute(new DensityFunction.SinglePointContext(wide(x), y, wide(z))))) directNan++;
                var block = chunk.getInterpolatedState();
                if (block == null) block = settings.defaultBlock();
                if (!block.isAir() && block.getFluidState().isEmpty()) {
                    solid++; top = Math.max(top, y); heights.append(y).append(',');
                }
            }
        }
        chunk.stopInterpolation();
        if (print) System.out.printf("COLUMN axis=%s other=%s n=%s solid=%d top=%d nan=%d directNaN=%d heights=%s%n",
                alongX ? "X" : "Z", other, n, solid, top, nan, directNan, heights);
        return new int[]{solid, top, nan, directNan};
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.valueOf(args[0]), CoordinatePrecision.PRECISE))) {
             var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
             if (args[1].equals("verify-collapse")) {
                 verifyCollapse(state);
                 return;
             }
            if (args[1].equals("verify")) {
                if (!args[0].equals("BIT_64_STABLE_SHIFTS")) throw new IllegalArgumentException("Verification requires BIT_64_STABLE_SHIFTS");
                verify(state);
                return;
            }
            if (args[1].equals("chunk")) {
                chunk(state, new BigInteger(args[2]), new BigInteger(args[3]));
                return;
            }
             if (args[1].equals("point")) {
                 var p = new DensityFunction.SinglePointContext(wide(new BigInteger(args[2])), Integer.parseInt(args[3]), wide(new BigInteger(args[4])));
                 // Compare the actual shifted climate call with an unshifted call at
                 // the SAME scale. Unit-scale raw-noise surveys are not comparable.
                 for (var key : java.util.List.of(Noises.CONTINENTALNESS, Noises.EROSION, Noises.TEMPERATURE)) {
                     var noise = state.getOrCreateNoise(key);
                     var sample = NoiseSampling.sampleWithExtrapolation(() -> noise.getValueScaled(
                             p.blockX(), 0.25D, 0L, 0.0D, p.blockZ()));
                     System.out.printf("ZERO_SHIFT %s scale=0.25 value=%s extrapolated=%s%n",
                             key.location(), sample.value(), sample.extrapolated());
                 }
                Map<String, DensityFunction> nodes = new LinkedHashMap<>();
                for (var key : BuiltinRegistries.DENSITY_FUNCTION.keySet())
                    if (key.getPath().startsWith("overworld/") || key.getPath().startsWith("shift_"))
                        nodes.put(key.getPath(), state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, key)));
                state.debugCaves(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation("overworld/sloped_cheese")))
                        .forEach((k, v) -> nodes.put("caves/" + k, v));
                var r = state.router();
                DensityFunction[] fields = {r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                        r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                        r.initialDensityWithoutJaggedness(), r.finalDensity(), r.veinToggle(), r.veinRidged(), r.veinGap()};
                 String[] names = {"barrier", "floodedness", "spread", "lava", "temperature", "vegetation",
                         "continentalness", "erosion", "depth", "ridges", "initial", "final", "vein_toggle", "vein_ridged", "vein_gap"};
                 int nan = 0;
                for (int i = 0; i < fields.length; i++) {
                    double v = fields[i].compute(p); if (Double.isNaN(v)) nan++;
                     System.out.printf("ROUTER %s=%s%n", names[i], v);
                }
                 System.out.println("ROUTER_NAN=" + nan + "/15");
                 nodes.forEach((k,v) -> System.out.println(k + "=" + v.compute(p)));
                 for (var key : java.util.List.of(Noises.ICEBERG_SURFACE, Noises.ICEBERG_PILLAR, Noises.ICEBERG_PILLAR_ROOF)) {
                     double scale = key.equals(Noises.ICEBERG_PILLAR) ? 1.28D
                             : key.equals(Noises.ICEBERG_PILLAR_ROOF) ? 1.17D : 1.0D;
                     double raw = SurfaceSystem.sampleNoise(state.getOrCreateNoise(key), p.blockX(), scale, p.blockZ());
                     System.out.printf("SURFACE %s raw=%s clamped=%s%n", key.location(), raw,
                             net.minecraft.world.level.WorldBounds.clampNoise(raw));
                 }
                 return;
            }
            boolean alongX = args[2].endsWith("X");
            int direction = args[2].startsWith("-") ? -1 : 1;
            BigInteger other = new BigInteger(args[3]);
            if (args[1].equals("columns")) {
                for (int i = 4; i < args.length; i++) column(state, new BigInteger(args[i]), alongX, other, true);
            } else if (args[1].equals("survey") || args[1].equals("chunks")) {
                int steps = Integer.parseInt(args[6]);
                if (steps < 1 || Integer.parseInt(args[4]) < 0 || Integer.parseInt(args[5]) > 1086)
                    throw new IllegalArgumentException("Expected positive steps and exponents in 0..1086");
                for (int exp = Integer.parseInt(args[4]); exp <= Integer.parseInt(args[5]); exp++)
                    for (int s = 0; s < steps; s++) {
                        BigInteger n = BigInteger.ONE.shiftLeft(exp).multiply(BigInteger.valueOf((long)steps+s)).divide(BigInteger.valueOf(steps))
                                .multiply(BigInteger.valueOf(direction));
                        if (args[1].equals("chunks")) chunk(state, alongX ? n : other, alongX ? other : n);
                        else column(state, n, alongX, other, true);
                    }
            } else if (args[1].equals("edge") || args[1].equals("nan-edge")) {
                boolean nanEdge = args[1].equals("nan-edge");
                BigInteger lo = new BigInteger(args[4]), hi = new BigInteger(args[5]);
                if (failed(column(state, lo, alongX, other, false), nanEdge)
                        || !failed(column(state, hi, alongX, other, false), nanEdge))
                    throw new IllegalArgumentException("Expected healthy/failed local bracket");
                while (hi.subtract(lo).abs().compareTo(BigInteger.ONE) > 0) {
                    BigInteger mid = lo.add(hi).shiftRight(1);
                    if (failed(column(state, mid, alongX, other, false), nanEdge)) hi = mid; else lo = mid;
                }
                int sign = hi.compareTo(lo);
                System.out.printf("LOCAL_EDGE kind=%s inward=%s outward=%s (not a global first or proof of monotonicity)%n", args[1], lo, hi);
                boolean monotonic = true;
                for (int d = -16; d <= 16; d++) {
                    int[] result = column(state, hi.add(BigInteger.valueOf((long)d * sign)), alongX, other, true);
                    if (failed(result, nanEdge) != (d >= 0)) monotonic = false;
                }
                System.out.println("MONOTONIC_LOCAL_WINDOW=" + monotonic);
            } else throw new IllegalArgumentException("Unknown operation");
        }
    }

    private static boolean failed(int[] column, boolean nanEdge) {
        return nanEdge ? column[2] == 384 : column[0] == 0;
    }

    private static int[][] chunk(RandomState state, BigInteger x, BigInteger z) {
        System.out.printf("CHUNK_REQUEST blockX=%s blockZ=%s%n", x, z);
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes = net.minecraft.world.level.biome.MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE, biomes, settings);
        var pos = new net.minecraft.world.level.ChunkPos(new net.minecraft.core.BlockPos(wide(x), 0, wide(z)));
        var height = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
        var chunk = new net.minecraft.world.level.chunk.ProtoChunk(pos, net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                height, BuiltinRegistries.BIOME, null);
        var noiseChunk = chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, state, DensityFunctions.BeardifierMarker.INSTANCE,
                settings.value(), (px, py, pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty()));
        generator.createBiomes(BuiltinRegistries.BIOME, Runnable::run, state, Blender.empty(), null, chunk).join();
        generator.fillFromNoise(Runnable::run, Blender.empty(), state, null, chunk).join();
        int[] noiseCounts = chunkCounts(chunk, "NOISE");
         Map<String, Integer> surfaceBiomes = new LinkedHashMap<>();
         boolean[] recordedColumn = new boolean[256];
         var manager = new net.minecraft.world.level.biome.BiomeManager(
                 (qx, qy, qz) -> biomes.getNoiseBiome(qx, qy, qz, state.sampler()),
                 net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(0)) {
             @Override
             public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getBiome(net.minecraft.core.BlockPos p) {
                 var biome = super.getBiome(p);
                 // Observe the first query per column (the extension's biome),
                 // never issue extra searches: overflowed biome search is cached
                 // and an additional diagnostic query could change its next result.
                 int index = ((int)p.getX().lowLimb() & 15) * 16 + ((int)p.getZ().lowLimb() & 15);
                 if (!recordedColumn[index]) {
                     recordedColumn[index] = true;
                     surfaceBiomes.merge(biome.unwrapKey().orElseThrow().location().toString(), 1, Integer::sum);
                 }
                 return biome;
             }
         };
         state.surfaceSystem().buildSurface(state, manager, BuiltinRegistries.BIOME, false,
                 new WorldGenerationContext(generator, height), chunk, noiseChunk, settings.value().surfaceRule());
         System.out.println("SURFACE_BIOMES columns=" + surfaceBiomes);
        return new int[][]{noiseCounts, chunkCounts(chunk, "SURFACE")};
    }

    private static int[] chunkCounts(net.minecraft.world.level.chunk.ProtoChunk chunk, String stage) {
        int solid = 0, top = -65;
        int lastX = -1, lastY = -65, lastZ = -1;
        Map<String, Integer> materials = new LinkedHashMap<>();
        var p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 320; y++) {
            var block = chunk.getBlockState(p.set(chunk.getPos().getBlockX(x), y, chunk.getPos().getBlockZ(z)));
            if (!block.isAir() && block.getFluidState().isEmpty()) {
                solid++; top = Math.max(top, y);
                if (z > lastZ || z == lastZ && (x > lastX || x == lastX && y > lastY)) {
                    lastX = x; lastY = y; lastZ = z;
                }
                materials.merge(Registry.BLOCK.getKey(block.getBlock()).toString(), 1, Integer::sum);
            }
        }
        System.out.printf("CHUNK stage=%s x=%s z=%s solid=%d top=%d materials=%s%n", stage,
                chunk.getPos().x(), chunk.getPos().z(), solid, top, materials);
        if (solid > 0) System.out.printf("LAST_IN_CHUNK stage=%s blockX=%s y=%d blockZ=%s (lexicographic Z,X,Y; not global)%n",
                stage, chunk.getPos().getBlockX(lastX), lastY, chunk.getPos().getBlockZ(lastZ));
        return new int[]{solid, top, materials.getOrDefault("minecraft:packed_ice", 0), materials.getOrDefault("minecraft:snow_block", 0)};
    }

    private static void verifyCollapse(RandomState state) {
        if (GenerationContext.current().mode() != GeneratorMode.BIT_64_STABLE_SHIFTS)
            throw new IllegalArgumentException("Verification requires BIT_64_STABLE_SHIFTS");
        int[] interpolated = column(state, BigInteger.ONE.shiftLeft(160), false, BigInteger.valueOf(131), true);
        if (!java.util.Arrays.equals(interpolated, new int[]{1, -64, 280, 0}))
            throw new AssertionError("Finite direct density must not conceal interpolated NaN");
        for (int exponent : new int[]{170, 174}) {
            var z = wide(BigInteger.ONE.shiftLeft(exponent));
            var p = new DensityFunction.SinglePointContext(Int1088.fromLong(131), 136, z);
            double zeroShift = state.getOrCreateNoise(Noises.CONTINENTALNESS)
                    .getValueScaled(p.blockX(), 0.25D, 0L, 0.0D, z);
            double actual = state.router().continents().compute(p);
            if (exponent == 170 ? !Double.isFinite(zeroShift) || !Double.isFinite(actual)
                    : zeroShift != Double.POSITIVE_INFINITY || actual != Double.POSITIVE_INFINITY)
                throw new AssertionError("Continentalness classification at 2^" + exponent);
            for (String name : new String[]{"shift_x", "shift_z"}) {
                double shift = state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY,
                        new ResourceLocation(name))).compute(p);
                if (!Double.isFinite(shift) || Math.abs(shift) > 4)
                    throw new AssertionError("Stable shift was not bounded");
            }
            if (!Double.isFinite(state.router().temperature().compute(p))
                    || !Double.isNaN(state.router().finalDensity().compute(p)))
                throw new AssertionError("Climate/density contrast");
            int[][] counts = chunk(state, BigInteger.valueOf(131), BigInteger.ONE.shiftLeft(exponent));
            if (counts[0][0] != 0) throw new AssertionError("Noise stage should contain no solids");
            if (exponent == 170 ? counts[1][0] == 0 || counts[1][1] != 134
                    || counts[1][0] != counts[1][2] + counts[1][3] : counts[1][0] != 0)
                throw new AssertionError("Surface-only ice/empty contrast");
        }
        System.out.println("PASS: direct/interpolated contrast, bounded shifts, finite/infinite same-scale continentalness, NaN density, surface-only ice and empty chunk");
    }

    private static void verify(RandomState state) {
        int[] normal = column(state, BigInteger.ZERO, true, BigInteger.ZERO, false);
        if (normal[0] == 0 || normal[2] != 0) throw new AssertionError("Ordinary column sanity check");
        BigInteger z = new BigInteger("111414603535684224740921180149999999999999999999999");
        var p = new DensityFunction.SinglePointContext(Int1088.fromLong(51), 136, wide(z));
        if (state.router().finalDensity().compute(p) != 0.4583333333333333) throw new AssertionError("Screenshot direct density");
        var first = new DensityFunction.SinglePointContext(Int1088.fromLong(48), 136, wide(z.add(BigInteger.valueOf(3))));
        if (!Double.isNaN(state.router().finalDensity().compute(first))
                || !Double.isFinite(state.router().depth().compute(first)))
            throw new AssertionError("First screenshot local NaN contrast");
        int[] observed = column(state, z, false, BigInteger.valueOf(51), true);
        if (!java.util.Arrays.equals(observed, new int[]{0, -65, 320, 295})) throw new AssertionError("Screenshot interpolated column");
        int[][] counts = chunk(state, BigInteger.valueOf(51), z);
        if (counts[0][0] != 0 || !java.util.Arrays.equals(counts[1], new int[]{22259, 134, 21689, 570}))
            throw new AssertionError("Screenshot surface-only ice");
        BigInteger edge = new BigInteger("61190897294192142547271579610383892756375251976164");
        for (int delta : new int[]{-1, 0, 16}) {
            int[] c = column(state, edge.add(BigInteger.valueOf(delta)), false, BigInteger.valueOf(51), true);
            if (c[0] != (delta == 0 ? 0 : 8)) throw new AssertionError("Nonmonotonic solid recurrence");
        }
        BigInteger reference = BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE.shiftLeft(970))
                .add(BigInteger.valueOf(1499)).divide(BigInteger.valueOf(1500));
        var far = new DensityFunction.SinglePointContext(Int1088.ZERO, 64, wide(reference));
        if (!Double.isNaN(state.router().finalDensity().compute(far))
                || state.router().barrierNoise().compute(far) != Double.NEGATIVE_INFINITY
                || state.router().veinToggle().compute(far) != 0)
            throw new AssertionError("Reference router failure is not literally all fields NaN");
        int[][] empty = chunk(state, BigInteger.ZERO, reference);
        if (empty[0][0] != 0 || empty[1][0] != 0) throw new AssertionError("Reference chunk not empty of solids");
        System.out.println("PASS: ordinary column, screenshot direct/interpolated density, actual surface-only ice, solid recurrence, reference router and empty chunk");
    }
}
