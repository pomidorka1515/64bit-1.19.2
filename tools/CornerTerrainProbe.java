package net.minecraft.world.level.levelgen;

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

/** Read-only seed-0 reproduction of the reported corner transects. */
public final class CornerTerrainProbe {
    private static void traceRoughness(RandomState s, Int1088 x, Int1088 z) {
        try {
            var secondField = NormalNoise.class.getDeclaredField("second");
            secondField.setAccessible(true);
            var second = (PerlinNoise)secondField.get(s.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS));
            var noise = second.getOctaveNoise(0);
            var route = NoiseSampling.class.getDeclaredMethod("route", PreciseNoiseCoordinate.Split.class, double.class);
            route.setAccessible(true);
            for (int axis = 0; axis < 2; axis++) {
                var split = new PreciseNoiseCoordinate.Split();
                WorldgenCoordinate.scale(axis == 0 ? x : z, 1.0181268882175227D, -5, split);
                route.invoke(null, split, axis == 0 ? noise.xo : noise.zo);
                System.out.printf("ROUGH_SECOND axis=%s lattice=%d fraction=%g fade=%g%n",
                        axis == 0 ? "X" : "Z", split.lattice, split.fraction, net.minecraft.util.Mth.smoothstep(split.fraction));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static DensityFunction node(RandomState s, String name) {
        return s.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation(name)));
    }

    private static void profile(RandomState s, String xs, String zs) {
        var x = Int1088.parseDecimal(xs);
        var z = Int1088.parseDecimal(zs);
        System.out.println("POSITION " + xs + " " + zs);
        traceRoughness(s,x,z);
        var caves = s.debugCaves(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation("overworld/sloped_cheese")));
        for (int y : new int[]{-56, 0, 56, 61, 64, 80, 88, 96, 128, 248}) {
            var p = new DensityFunction.SinglePointContext(x, y, z);
            System.out.printf("POINT y=%d base=%g slope=%g rough=%g mod=%g roughFn=%g entrance=%g candidate=%g pillars=%g selected=%g final=%g%n",
                    y, node(s,"overworld/base_3d_noise").compute(p), node(s,"overworld/sloped_cheese").compute(p),
                    s.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS).getValueScaled(x,1,y,1,z),
                    s.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS_MODULATOR).getValueScaled(x,1,y,1,z),
                    node(s,"overworld/caves/spaghetti_roughness_function").compute(p),
                    node(s,"overworld/caves/entrances").compute(p), caves.get("candidate").compute(p),
                    caves.get("gated_pillars").compute(p), caves.get("selected").compute(p), s.router().finalDensity().compute(p));
        }
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var ox = Int1088.parseDecimal(new java.math.BigInteger(xs).shiftRight(4).shiftLeft(4).toString());
        var oz = Int1088.parseDecimal(new java.math.BigInteger(zs).shiftRight(4).shiftLeft(4).toString());
        var chunk = new NoiseChunk(4, s, ox, oz, settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE,
                settings, (px, py, pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var density = s.router().finalDensity().mapAll(chunk::wrap);
        int lx = (int)x.lowLimb() & 15, lz = (int)z.lowLimb() & 15;
        chunk.initializeForFirstCellX();
        for (int cx = 0; cx <= lx / 4; cx++) {
            chunk.advanceCellX(cx);
            if (cx < lx / 4) chunk.swapSlices();
        }
        String last = "";
        int top = 320;
        for (int cy = 47; cy >= 0; cy--) {
            chunk.selectCellYZ(cy, lz / 4);
            for (int dy = 7; dy >= 0; dy--) {
                int y = -64 + cy * 8 + dy;
                chunk.updateForY(y, dy / 8.0);
                chunk.updateForX(x, (lx % 4) / 4.0);
                chunk.updateForZ(z, (lz % 4) / 4.0);
                var b = chunk.getInterpolatedState();
                if (b == null) b = settings.defaultBlock();
                String current = b.toString();
                if (!current.equals(last)) {
                    if (!last.isEmpty()) System.out.printf("FILL y=%d..%d %s%n", y+1, top, last);
                    last = current; top = y;
                }
                if (y == 88 || y == 80 || y == 61) System.out.printf("INTERPOLATED y=%d density=%g%n", y, density.compute(chunk));
            }
        }
        System.out.printf("FILL y=-64..%d %s%n", top, last);
        chunk.stopInterpolation();
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.BIT_64_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
            if (args.length == 2) profile(state,args[0],args[1]);
            else {
                for (String x : new String[]{"289893046333429650128", "-289893046333429650123"})
                    for (String z : new String[]{"289893046333429650128", "-289893046333429650123"}) profile(state,x,z);
                profile(state,"289893046333429650107","-289893046333429650145");
                profile(state,"289893046333429650107","-289893046333429650123");
                profile(state,"289893046333429650117","289893046333429650117");
                profile(state,"-289893046333429650118","-289893046333429650129");
            }
        }
    }
}
