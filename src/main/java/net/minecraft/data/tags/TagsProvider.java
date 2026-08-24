package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.slf4j.Logger;

public abstract class TagsProvider<T> implements DataProvider {
   private static final Logger LOGGER = LogUtils.getLogger();
   protected final DataGenerator.PathProvider pathProvider;
   protected final Registry<T> registry;
   private final Map<ResourceLocation, TagBuilder> builders = Maps.newLinkedHashMap();

   protected TagsProvider(DataGenerator p_126546_, Registry<T> p_126547_) {
      this.pathProvider = p_126546_.createPathProvider(DataGenerator.Target.DATA_PACK, TagManager.getTagDir(p_126547_.key()));
      this.registry = p_126547_;
   }

   public final String getName() {
      return "Tags for " + this.registry.key().location();
   }

   protected abstract void addTags();

   public void run(CachedOutput p_236446_) {
      this.builders.clear();
      this.addTags();
      this.builders.forEach((p_236449_, p_236450_) -> {
         List<TagEntry> list = p_236450_.build();
         List<TagEntry> list1 = list.stream().filter((p_236444_) -> {
            return !p_236444_.verifyIfPresent(this.registry::containsKey, this.builders::containsKey);
         }).toList();
         if (!list1.isEmpty()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Couldn't define tag %s as it is missing following references: %s", p_236449_, list1.stream().map(Objects::toString).collect(Collectors.joining(","))));
         } else {
            JsonElement jsonelement = TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(list, false)).getOrThrow(false, LOGGER::error);
            Path path = this.pathProvider.json(p_236449_);

            try {
               DataProvider.saveStable(p_236446_, jsonelement, path);
            } catch (IOException ioexception) {
               LOGGER.error("Couldn't save tags to {}", path, ioexception);
            }

         }
      });
   }

   protected TagsProvider.TagAppender<T> tag(TagKey<T> p_206425_) {
      TagBuilder tagbuilder = this.getOrCreateRawBuilder(p_206425_);
      return new TagsProvider.TagAppender<>(tagbuilder, this.registry);
   }

   protected TagBuilder getOrCreateRawBuilder(TagKey<T> p_236452_) {
      return this.builders.computeIfAbsent(p_236452_.location(), (p_236442_) -> {
         return TagBuilder.create();
      });
   }

   protected static class TagAppender<T> {
      private final TagBuilder builder;
      private final Registry<T> registry;

      TagAppender(TagBuilder p_236454_, Registry<T> p_236455_) {
         this.builder = p_236454_;
         this.registry = p_236455_;
      }

      public TagsProvider.TagAppender<T> add(T p_126583_) {
         this.builder.addElement(this.registry.getKey(p_126583_));
         return this;
      }

      @SafeVarargs
      public final TagsProvider.TagAppender<T> add(ResourceKey<T>... p_211102_) {
         for(ResourceKey<T> resourcekey : p_211102_) {
            this.builder.addElement(resourcekey.location());
         }

         return this;
      }

      public TagsProvider.TagAppender<T> addOptional(ResourceLocation p_176840_) {
         this.builder.addOptionalElement(p_176840_);
         return this;
      }

      public TagsProvider.TagAppender<T> addTag(TagKey<T> p_206429_) {
         this.builder.addTag(p_206429_.location());
         return this;
      }

      public TagsProvider.TagAppender<T> addOptionalTag(ResourceLocation p_176842_) {
         this.builder.addOptionalTag(p_176842_);
         return this;
      }

      @SafeVarargs
      public final TagsProvider.TagAppender<T> add(T... p_126585_) {
         Stream.<T>of(p_126585_).map(this.registry::getKey).forEach((p_126587_) -> {
            this.builder.addElement(p_126587_);
         });
         return this;
      }
   }
}