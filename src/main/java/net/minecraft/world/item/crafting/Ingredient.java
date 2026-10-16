package net.minecraft.world.item.crafting;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements Predicate<ItemStack> {
    public static final Ingredient EMPTY = new Ingredient(Stream.empty());
    private final Ingredient.Value[] values;
    @Nullable
    private ItemStack[] itemStacks;
    @Nullable
    private IntList stackingIds;

    private Ingredient(Stream<? extends Ingredient.Value> pValues) {
        this.values = pValues.toArray((p_43933_) -> {
            return new Ingredient.Value[p_43933_];
        });
    }

    public ItemStack[] getItems() {
        this.dissolve();
        return this.itemStacks;
    }

    private void dissolve() {
        if (this.itemStacks == null) {
            this.itemStacks = Arrays.stream(this.values).flatMap((p_43916_) -> {
                                                            return p_43916_.getItems().stream();
                                                        })
                                      .distinct()
                                      .toArray((p_43910_) -> {
                                          return new ItemStack[p_43910_];
                                      });
        }
    }

    public boolean test(@Nullable ItemStack pStack) {
        if (pStack == null) {
            return false;
        } else {
            this.dissolve();
            if (this.itemStacks.length == 0) {
                return pStack.isEmpty();
            } else {
                for (ItemStack itemstack : this.itemStacks) {
                    if (itemstack.is(pStack.getItem())) {
                        return true;
                    }
                }

                return false;
            }
        }
    }

    public IntList getStackingIds() {
        if (this.stackingIds == null) {
            this.dissolve();
            this.stackingIds = new IntArrayList(this.itemStacks.length);

            for (ItemStack itemstack : this.itemStacks) {
                this.stackingIds.add(StackedContents.getStackingIndex(itemstack));
            }

            this.stackingIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.stackingIds;
    }

    public void toNetwork(FriendlyByteBuf pBuffer) {
        this.dissolve();
        pBuffer.writeCollection(Arrays.asList(this.itemStacks), FriendlyByteBuf::writeItem);
    }

    public JsonElement toJson() {
        if (this.values.length == 1) {
            return this.values[0].serialize();
        } else {
            JsonArray jsonarray = new JsonArray();

            for (Ingredient.Value ingredient$value : this.values) {
                jsonarray.add(ingredient$value.serialize());
            }

            return jsonarray;
        }
    }

    public boolean isEmpty() {
        return this.values.length == 0 && (this.itemStacks == null || this.itemStacks.length == 0) && (this.stackingIds == null || this.stackingIds.isEmpty());
    }

    private static Ingredient fromValues(Stream<? extends Ingredient.Value> pStream) {
        Ingredient ingredient = new Ingredient(pStream);
        return ingredient.values.length == 0 ? EMPTY : ingredient;
    }

    public static Ingredient of() {
        return EMPTY;
    }

    public static Ingredient of(ItemLike... pItems) {
        return of(Arrays.stream(pItems).map(ItemStack::new));
    }

    public static Ingredient of(ItemStack... pStacks) {
        return of(Arrays.stream(pStacks));
    }

    public static Ingredient of(Stream<ItemStack> pStacks) {
        return fromValues(pStacks.filter((p_43944_) -> {
                                      return !p_43944_.isEmpty();
                                  })
                        .map(Ingredient.ItemValue::new));
    }

    public static Ingredient of(TagKey<Item> pTag) {
        return fromValues(Stream.of(new Ingredient.TagValue(pTag)));
    }

    public static Ingredient fromNetwork(FriendlyByteBuf pBuffer) {
        return fromValues(pBuffer.<ItemStack>readList(FriendlyByteBuf::readItem).stream().map(Ingredient.ItemValue::new));
    }

    public static Ingredient fromJson(@Nullable JsonElement pJson) {
        if (pJson != null && !pJson.isJsonNull()) {
            if (pJson.isJsonObject()) {
                return fromValues(Stream.of(valueFromJson(pJson.getAsJsonObject())));
            } else if (pJson.isJsonArray()) {
                JsonArray jsonarray = pJson.getAsJsonArray();
                if (jsonarray.size() == 0) {
                    throw new JsonSyntaxException("Item array cannot be empty, at least one item must be defined");
                } else {
                    return fromValues(StreamSupport.stream(jsonarray.spliterator(), false).map((p_151264_) -> {
                        return valueFromJson(GsonHelper.convertToJsonObject(p_151264_, "item"));
                    }));
                }
            } else {
                throw new JsonSyntaxException("Expected item to be object or array of objects");
            }
        } else {
            throw new JsonSyntaxException("Item cannot be null");
        }
    }

    private static Ingredient.Value valueFromJson(JsonObject pJson) {
        if (pJson.has("item") && pJson.has("tag")) {
            throw new JsonParseException("An ingredient entry is either a tag or an item, not both");
        } else if (pJson.has("item")) {
            Item item = ShapedRecipe.itemFromJson(pJson);
            return new Ingredient.ItemValue(new ItemStack(item));
        } else if (pJson.has("tag")) {
            ResourceLocation resourcelocation = new ResourceLocation(GsonHelper.getAsString(pJson, "tag"));
            TagKey<Item> tagkey = TagKey.create(Registry.ITEM_REGISTRY, resourcelocation);
            return new Ingredient.TagValue(tagkey);
        } else {
            throw new JsonParseException("An ingredient entry needs either a tag or an item");
        }
    }

    static class ItemValue implements Ingredient.Value {
        private final ItemStack item;

        ItemValue(ItemStack pItem) {
            this.item = pItem;
        }

        public Collection<ItemStack> getItems() {
            return Collections.singleton(this.item);
        }

        public JsonObject serialize() {
            JsonObject jsonobject = new JsonObject();
            jsonobject.addProperty("item", Registry.ITEM.getKey(this.item.getItem()).toString());
            return jsonobject;
        }
    }

    static class TagValue implements Ingredient.Value {
        private final TagKey<Item> tag;

        TagValue(TagKey<Item> pTag) {
            this.tag = pTag;
        }

        public Collection<ItemStack> getItems() {
            List<ItemStack> list = Lists.newArrayList();

            for (Holder<Item> holder : Registry.ITEM.getTagOrEmpty(this.tag)) {
                list.add(new ItemStack(holder));
            }

            return list;
        }

        public JsonObject serialize() {
            JsonObject jsonobject = new JsonObject();
            jsonobject.addProperty("tag", this.tag.location().toString());
            return jsonobject;
        }
    }

    interface Value {
        Collection<ItemStack> getItems();

        JsonObject serialize();
    }
}
