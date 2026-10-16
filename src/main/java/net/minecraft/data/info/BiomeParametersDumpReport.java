package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.slf4j.Logger;

public class BiomeParametersDumpReport implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path topPath;

    public BiomeParametersDumpReport(DataGenerator pGenerator) {
        this.topPath = pGenerator.getOutputFolder(DataGenerator.Target.REPORTS).resolve("biome_parameters");
    }

    public void run(CachedOutput pOutput) {
        RegistryAccess.Frozen registryaccess$frozen = RegistryAccess.BUILTIN.get();
        DynamicOps<JsonElement> dynamicops = RegistryOps.create(JsonOps.INSTANCE, registryaccess$frozen);
        Registry<Biome> registry = registryaccess$frozen.registryOrThrow(Registry.BIOME_REGISTRY);
        MultiNoiseBiomeSource.Preset.getPresets().forEach((p_236184_) -> {
            MultiNoiseBiomeSource multinoisebiomesource = p_236184_.getSecond().biomeSource(registry, false);
            dumpValue(this.createPath(p_236184_.getFirst()), pOutput, dynamicops, MultiNoiseBiomeSource.CODEC, multinoisebiomesource);
        });
    }

    private static <E> void dumpValue(Path p_236188_, CachedOutput p_236189_, DynamicOps<JsonElement> p_236190_, Encoder<E> p_236191_, E p_236192_) {
        try {
            Optional<JsonElement> optional = p_236191_.encodeStart(p_236190_, p_236192_).resultOrPartial((p_236195_) -> {
                LOGGER.error("Couldn't serialize element {}: {}", p_236188_, p_236195_);
            });
            if (optional.isPresent()) {
                DataProvider.saveStable(p_236189_, optional.get(), p_236188_);
            }
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't save element {}", p_236188_, ioexception);
        }
    }

    private Path createPath(ResourceLocation pLocation) {
        return this.topPath.resolve(pLocation.getNamespace()).resolve(pLocation.getPath() + ".json");
    }

    public String getName() {
        return "Biome Parameters";
    }
}
