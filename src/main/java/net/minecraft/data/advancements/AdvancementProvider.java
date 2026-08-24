package net.minecraft.data.advancements;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AdvancementProvider implements DataProvider {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final DataGenerator.PathProvider pathProvider;
   private final List<Consumer<Consumer<Advancement>>> tabs = ImmutableList.of(new TheEndAdvancements(), new HusbandryAdvancements(), new AdventureAdvancements(), new NetherAdvancements(), new StoryAdvancements());

   public AdvancementProvider(DataGenerator p_123966_) {
      this.pathProvider = p_123966_.createPathProvider(DataGenerator.Target.DATA_PACK, "advancements");
   }

   public void run(CachedOutput p_236158_) {
      Set<ResourceLocation> set = Sets.newHashSet();
      Consumer<Advancement> consumer = (p_236162_) -> {
         if (!set.add(p_236162_.getId())) {
            throw new IllegalStateException("Duplicate advancement " + p_236162_.getId());
         } else {
            Path path = this.pathProvider.json(p_236162_.getId());

            try {
               DataProvider.saveStable(p_236158_, p_236162_.deconstruct().serializeToJson(), path);
            } catch (IOException ioexception) {
               LOGGER.error("Couldn't save advancement {}", path, ioexception);
            }

         }
      };

      for(Consumer<Consumer<Advancement>> consumer1 : this.tabs) {
         consumer1.accept(consumer);
      }

   }

   public String getName() {
      return "Advancements";
   }
}