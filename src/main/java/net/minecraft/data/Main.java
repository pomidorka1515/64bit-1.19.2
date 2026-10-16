package net.minecraft.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.info.BiomeParametersDumpReport;
import net.minecraft.data.info.BlockListReport;
import net.minecraft.data.info.CommandsReport;
import net.minecraft.data.info.RegistryDumpReport;
import net.minecraft.data.info.WorldgenRegistryDumpReport;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.models.ModelProvider;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.data.structures.SnbtToNbt;
import net.minecraft.data.structures.StructureUpdater;
import net.minecraft.data.tags.BannerPatternTagsProvider;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraft.data.tags.CatVariantTagsProvider;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.FlatLevelGeneratorPresetTagsProvider;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.data.tags.GameEventTagsProvider;
import net.minecraft.data.tags.InstrumentTagsProvider;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.PaintingVariantTagsProvider;
import net.minecraft.data.tags.PoiTypeTagsProvider;
import net.minecraft.data.tags.StructureTagsProvider;
import net.minecraft.data.tags.WorldPresetTagsProvider;
import net.minecraft.obfuscate.DontObfuscate;

public class Main {
    @DontObfuscate
    public static void main(String[] pArgs) throws IOException {
        SharedConstants.tryDetectVersion();
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("help", "Show the help menu").forHelp();
        OptionSpec<Void> optionspec1 = optionparser.accepts("server", "Include server generators");
        OptionSpec<Void> optionspec2 = optionparser.accepts("client", "Include client generators");
        OptionSpec<Void> optionspec3 = optionparser.accepts("dev", "Include development tools");
        OptionSpec<Void> optionspec4 = optionparser.accepts("reports", "Include data reports");
        OptionSpec<Void> optionspec5 = optionparser.accepts("validate", "Validate inputs");
        OptionSpec<Void> optionspec6 = optionparser.accepts("all", "Include all generators");
        OptionSpec<String> optionspec7 = optionparser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
        OptionSpec<String> optionspec8 = optionparser.accepts("input", "Input folder").withRequiredArg();
        OptionSet optionset = optionparser.parse(pArgs);
        if (!optionset.has(optionspec) && optionset.hasOptions()) {
            Path path = Paths.get(optionspec7.value(optionset));
            boolean flag = optionset.has(optionspec6);
            boolean flag1 = flag || optionset.has(optionspec2);
            boolean flag2 = flag || optionset.has(optionspec1);
            boolean flag3 = flag || optionset.has(optionspec3);
            boolean flag4 = flag || optionset.has(optionspec4);
            boolean flag5 = flag || optionset.has(optionspec5);
            DataGenerator datagenerator = createStandardGenerator(path, optionset.valuesOf(optionspec8).stream().map((p_129659_) -> {
                                                                                                                    return Paths.get(p_129659_);
                                                                                                                })
                                                                                .collect(Collectors.toList()),
                    flag1, flag2, flag3, flag4, flag5, SharedConstants.getCurrentVersion(), true);
            datagenerator.run();
        } else {
            optionparser.printHelpOn(System.out);
        }
    }

    public static DataGenerator createStandardGenerator(Path pOutputFolder, Collection<Path> pInputFolders, boolean pClient, boolean pServer, boolean pDev, boolean pReports, boolean pValidate, WorldVersion pVersion, boolean pAlwaysGenerate) {
        DataGenerator datagenerator = new DataGenerator(pOutputFolder, pInputFolders, pVersion, pAlwaysGenerate);
        datagenerator.addProvider(pClient || pServer, (new SnbtToNbt(datagenerator)).addFilter(new StructureUpdater()));
        datagenerator.addProvider(pClient, new ModelProvider(datagenerator));
        datagenerator.addProvider(pServer, new AdvancementProvider(datagenerator));
        datagenerator.addProvider(pServer, new LootTableProvider(datagenerator));
        datagenerator.addProvider(pServer, new RecipeProvider(datagenerator));
        BlockTagsProvider blocktagsprovider = new BlockTagsProvider(datagenerator);
        datagenerator.addProvider(pServer, blocktagsprovider);
        datagenerator.addProvider(pServer, new ItemTagsProvider(datagenerator, blocktagsprovider));
        datagenerator.addProvider(pServer, new BannerPatternTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new BiomeTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new CatVariantTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new EntityTypeTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new FlatLevelGeneratorPresetTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new FluidTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new GameEventTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new InstrumentTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new PaintingVariantTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new PoiTypeTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new StructureTagsProvider(datagenerator));
        datagenerator.addProvider(pServer, new WorldPresetTagsProvider(datagenerator));
        datagenerator.addProvider(pDev, new NbtToSnbt(datagenerator));
        datagenerator.addProvider(pReports, new BiomeParametersDumpReport(datagenerator));
        datagenerator.addProvider(pReports, new BlockListReport(datagenerator));
        datagenerator.addProvider(pReports, new CommandsReport(datagenerator));
        datagenerator.addProvider(pReports, new RegistryDumpReport(datagenerator));
        datagenerator.addProvider(pReports, new WorldgenRegistryDumpReport(datagenerator));
        return datagenerator;
    }
}
