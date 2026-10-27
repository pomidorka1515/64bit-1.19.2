package net.minecraft.world.level.levelgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Display-only probes. Never creates a NoiseChunk or requests/generates a chunk. */
final class WorldgenDiagnostics {
    private final List<String> lines;
    private final RandomState random;
    private final DensityFunction.SinglePointContext context;

    private WorldgenDiagnostics(List<String> lines, RandomState random, BlockPos pos) {
        this.lines = lines;
        this.random = random;
        this.context = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
    }

    static void append(List<String> lines, RandomState random, BlockPos pos, Holder<NoiseGeneratorSettings> settings) {
        if (settings.is(NoiseGeneratorSettings.END)) return;
        var debug = new WorldgenDiagnostics(lines, random, pos);
        if (settings.is(NoiseGeneratorSettings.NETHER)) {
            // Legacy Nether routing has no shifts, Overworld caves, aquifers or ore veins.
            debug.surfaces(true);
            return;
        }
        var shift = debug.holder(Noises.SHIFT);
        debug.line("Shift", "X", settings.value().useLegacyRandomSource() ? 0.0D : new DensityFunctions.ShiftA(shift).compute(debug.context),
                "Z", settings.value().useLegacyRandomSource() ? 0.0D : new DensityFunctions.ShiftB(shift).compute(debug.context));
        boolean overworld = settings.is(NoiseGeneratorSettings.OVERWORLD)
                || settings.is(NoiseGeneratorSettings.LARGE_BIOMES) || settings.is(NoiseGeneratorSettings.AMPLIFIED);
        if (overworld) {
            String prefix = settings.is(NoiseGeneratorSettings.LARGE_BIOMES) ? "overworld_large_biomes/"
                    : settings.is(NoiseGeneratorSettings.AMPLIFIED) ? "overworld_amplified/" : "overworld/";
            debug.terrain(prefix);
            debug.caves(prefix);
        } else {
            lines.add("Cave probes inactive: generator does not use Overworld cave routing");
        }
        debug.aquifersAndOres();
        debug.surfaces(settings.is(NoiseGeneratorSettings.NETHER));
    }

    private void terrain(String prefix) {
        line("Terrain", "offset", function(prefix + "offset"), "factor", function(prefix + "factor"),
                "sloped_cheese", function(prefix + "sloped_cheese"));
        line("", "jaggedness", function(prefix + "jaggedness"), "jagged", noise(Noises.JAGGED, 1500.0D, 0.0D));
    }

    private void caves(String prefix) {
        var nodes = random.debugCaves(key(prefix + "sloped_cheese"));
        double rough = noise(Noises.SPAGHETTI_ROUGHNESS);
        double roughMod = noise(Noises.SPAGHETTI_ROUGHNESS_MODULATOR);
        line("Roughness", "", rough, "modulator", roughMod,
                "result", function("overworld/caves/spaghetti_roughness_function"));

        double mod2 = noise(Noises.SPAGHETTI_2D_MODULATOR, 2.0D, 1.0D);
        double rarity2 = DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2.mapper.get(mod2);
        line("Spag2D", "modulator", mod2, "rarity", rarity2,
                "sample", weird(Noises.SPAGHETTI_2D, mod2, DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2));
        line("", "elevation", noise(Noises.SPAGHETTI_2D_ELEVATION),
                "thickness", noise(Noises.SPAGHETTI_2D_THICKNESS, 2.0D, 1.0D),
                "result", function("overworld/caves/spaghetti_2d"));

        double mod3 = noise(Noises.SPAGHETTI_3D_RARITY, 2.0D, 1.0D);
        line("Spag3D", "modulator", mod3, "rarity", DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1.mapper.get(mod3),
                "thickness", noise(Noises.SPAGHETTI_3D_THICKNESS),
                "a", weird(Noises.SPAGHETTI_3D_1, mod3, DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1),
                "b", weird(Noises.SPAGHETTI_3D_2, mod3, DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1));
        line("Cave", "entrance", noise(Noises.CAVE_ENTRANCE, 0.75D, 0.5D),
                "result", function("overworld/caves/entrances"),
                "cheese", noise(Noises.CAVE_CHEESE, 1.0D, 2.0D / 3.0D),
                "layer", noise(Noises.CAVE_LAYER, 1.0D, 8.0D));
        double pillars = function("overworld/caves/pillars");
        line("Pillars", "", noise(Noises.PILLAR, 25.0D, 0.3D),
                "combined", pillars, "gated", nodes.get("gated_pillars").compute(context));
        line("Caves", "layer", nodes.get("cheese_layer").compute(context), "candidate", nodes.get("candidate").compute(context),
                "underground", nodes.get("underground").compute(context), "selected", nodes.get("selected").compute(context));
        line("Noodles", "toggle", noise(Noises.NOODLE), "thickness", noise(Noises.NOODLE_THICKNESS),
                "result", function("overworld/caves/noodle"));
        int last = lines.size() - 1;
        lines.set(last, lines.get(last) + " ridges: [" + BlendedNoise.formatDebugValue(noise(Noises.NOODLE_RIDGE_A, 8.0D / 3.0D, 8.0D / 3.0D))
                + ", " + BlendedNoise.formatDebugValue(noise(Noises.NOODLE_RIDGE_B, 8.0D / 3.0D, 8.0D / 3.0D)) + "]");
    }

    private void aquifersAndOres() {
        var router = random.router();
        line("Aquifers", "lava", router.lavaNoise().compute(context),
                "barrier", router.barrierNoise().compute(context),
                "floodedness", router.fluidLevelFloodednessNoise().compute(context),
                "spread", router.fluidLevelSpreadNoise().compute(context));
        line("Ores", "veininess", noise(Noises.ORE_VEININESS, 1.5D, 1.5D), "gap", noise(Noises.ORE_GAP),
                "toggle", router.veinToggle().compute(context), "ridged", router.veinRidged().compute(context));
    }

    private void surfaces(boolean nether) {
        // Surface and cave values remain display-only probes, not necessarily applied rules.
        double primary = surface(Noises.SURFACE, 1.0D);
        double secondary = surface(Noises.SURFACE_SECONDARY, 1.0D);
        line("Surface", "primary", primary, "secondary", secondary);
        if (nether) {
            surfaceLine("Nether", Noises.SOUL_SAND_LAYER, Noises.GRAVEL_LAYER, Noises.PATCH);
            surfaceLine("Nether", Noises.NETHERRACK, Noises.NETHER_WART, Noises.NETHER_STATE_SELECTOR);
        } else {
            line("Badlands", "surface", surface(Noises.BADLANDS_SURFACE, 1.0D),
                    "pillar", surface(Noises.BADLANDS_PILLAR, 0.2D), "roof", surface(Noises.BADLANDS_PILLAR_ROOF, 0.75D));
            line("Iceberg", "surface", surface(Noises.ICEBERG_SURFACE, 1.0D),
                    "pillar", surface(Noises.ICEBERG_PILLAR, 1.28D), "roof", surface(Noises.ICEBERG_PILLAR_ROOF, 1.17D));
            surfaceLine("Materials", Noises.SWAMP, Noises.CALCITE, Noises.GRAVEL);
            surfaceLine("Snow/Ice", Noises.PACKED_ICE, Noises.POWDER_SNOW, Noises.ICE);
        }
        // Retain the historical final sample order for ImprovedNoise's capture,
        // including WorldgenDebug's replay at Y=255 for players at/above 256.
        surface(Noises.SURFACE, 1.0D);
        surface(Noises.SURFACE_SECONDARY, 1.0D);
    }

    @SafeVarargs
    private final void surfaceLine(String group, ResourceKey<NormalNoise.NoiseParameters>... keys) {
        Object[] fields = new Object[keys.length * 2];
        for (int i = 0; i < keys.length; i++) {
            fields[2 * i] = switch (keys[i].location().getPath()) {
                case "surface_swamp" -> "swamp";
                case "packed_ice" -> "packed";
                case "powder_snow" -> "powder";
                default -> keys[i].location().getPath();
            };
            fields[2 * i + 1] = surface(keys[i], 1.0D);
        }
        line(group, fields);
    }

    private double surface(ResourceKey<NormalNoise.NoiseParameters> key, double scale) {
        return SurfaceSystem.sampleNoise(random.getOrCreateNoise(key), context.blockX(), scale, context.blockZ());
    }

    private double weird(ResourceKey<NormalNoise.NoiseParameters> key, double modulator,
                         DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper) {
        return new DensityFunctions.WeirdScaledSampler(DensityFunctions.constant(0), holder(key), mapper).transform(context, modulator);
    }

    private double noise(ResourceKey<NormalNoise.NoiseParameters> key) {
        return noise(key, 1.0D, 1.0D);
    }

    private double noise(ResourceKey<NormalNoise.NoiseParameters> key, double xzScale, double yScale) {
        return new DensityFunctions.Noise(holder(key), xzScale, yScale).compute(context);
    }

    private DensityFunction.NoiseHolder holder(ResourceKey<NormalNoise.NoiseParameters> key) {
        NormalNoise noise = random.getOrCreateNoise(key);
        return new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise);
    }

    private double function(String name) {
        return random.debugFunction(key(name)).compute(context);
    }

    private static ResourceKey<DensityFunction> key(String name) {
        return ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation(name));
    }

    private void line(String group, Object... fields) {
        StringBuilder line = new StringBuilder(group);
        for (int i = 0; i < fields.length; i += 2) {
            line.append(' ');
            if (!fields[i].equals("")) line.append(fields[i]).append(": ");
            line.append(BlendedNoise.formatDebugValue((Double) fields[i + 1]));
        }
        lines.add(line.toString());
    }
}
