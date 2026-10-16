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

    protected TagsProvider(DataGenerator pGenerator, Registry<T> pRegistry) {
        this.pathProvider = pGenerator.createPathProvider(DataGenerator.Target.DATA_PACK, TagManager.getTagDir(pRegistry.key()));
        this.registry = pRegistry;
    }

    public final String getName() {
        return "Tags for " + this.registry.key().location();
    }

    protected abstract void addTags();

    public void run(CachedOutput pOutput) {
        this.builders.clear();
        this.addTags();
        this.builders.forEach((p_236449_, p_236450_) -> {
            List<TagEntry> list = p_236450_.build();
            List<TagEntry> list1 = list.stream().filter((p_236444_) -> {
                                                    return !p_236444_.verifyIfPresent(this.registry::containsKey, this.builders::containsKey);
                                                })
                                           .toList();
            if (!list1.isEmpty()) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Couldn't define tag %s as it is missing following references: %s", p_236449_, list1.stream().map(Objects::toString).collect(Collectors.joining(","))));
            } else {
                JsonElement jsonelement = TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(list, false)).getOrThrow(false, LOGGER::error);
                Path path = this.pathProvider.json(p_236449_);

                try {
                    DataProvider.saveStable(pOutput, jsonelement, path);
                } catch (IOException ioexception) {
                    LOGGER.error("Couldn't save tags to {}", path, ioexception);
                }
            }
        });
    }

    protected TagsProvider.TagAppender<T> tag(TagKey<T> pTag) {
        TagBuilder tagbuilder = this.getOrCreateRawBuilder(pTag);
        return new TagsProvider.TagAppender<>(tagbuilder, this.registry);
    }

    protected TagBuilder getOrCreateRawBuilder(TagKey<T> pTag) {
        return this.builders.computeIfAbsent(pTag.location(), (p_236442_) -> {
            return TagBuilder.create();
        });
    }

    protected static class TagAppender<T> {
        private final TagBuilder builder;
        private final Registry<T> registry;

        TagAppender(TagBuilder pBuilder, Registry<T> pRegistry) {
            this.builder = pBuilder;
            this.registry = pRegistry;
        }

        public TagsProvider.TagAppender<T> add(T pItem) {
            this.builder.addElement(this.registry.getKey(pItem));
            return this;
        }

        @SafeVarargs
        public final TagsProvider.TagAppender<T> add(ResourceKey<T>... pToAdd) {
            for (ResourceKey<T> resourcekey : pToAdd) {
                this.builder.addElement(resourcekey.location());
            }

            return this;
        }

        public TagsProvider.TagAppender<T> addOptional(ResourceLocation pLocation) {
            this.builder.addOptionalElement(pLocation);
            return this;
        }

        public TagsProvider.TagAppender<T> addTag(TagKey<T> pTag) {
            this.builder.addTag(pTag.location());
            return this;
        }

        public TagsProvider.TagAppender<T> addOptionalTag(ResourceLocation pLocation) {
            this.builder.addOptionalTag(pLocation);
            return this;
        }

        @SafeVarargs
        public final TagsProvider.TagAppender<T> add(T... pToAdd) {
            Stream.<T>of(pToAdd).map(this.registry::getKey).forEach((p_126587_) -> {
                this.builder.addElement(p_126587_);
            });
            return this;
        }
    }
}
