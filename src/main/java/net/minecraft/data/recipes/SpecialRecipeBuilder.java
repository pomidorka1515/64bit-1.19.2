package net.minecraft.data.recipes;

import com.google.gson.JsonObject;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;

public class SpecialRecipeBuilder {
    final SimpleRecipeSerializer<?> serializer;

    public SpecialRecipeBuilder(SimpleRecipeSerializer<?> pSerializer) {
        this.serializer = pSerializer;
    }

    public static SpecialRecipeBuilder special(SimpleRecipeSerializer<?> pSerializer) {
        return new SpecialRecipeBuilder(pSerializer);
    }

    public void save(Consumer<FinishedRecipe> pFinishedRecipeConsumer, final String pId) {
        pFinishedRecipeConsumer.accept(new FinishedRecipe() {
            public void serializeRecipeData(JsonObject p_126370_) {
            }

            public RecipeSerializer<?> getType() {
                return SpecialRecipeBuilder.this.serializer;
            }

            public ResourceLocation getId() {
                return new ResourceLocation(pId);
            }

            @Nullable
            public JsonObject serializeAdvancement() {
                return null;
            }

            public ResourceLocation getAdvancementId() {
                return new ResourceLocation("");
            }
        });
    }
}
