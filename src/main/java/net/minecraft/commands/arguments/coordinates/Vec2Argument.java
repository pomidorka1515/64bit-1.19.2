package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;

public class Vec2Argument implements ArgumentType<Coordinates> {
   private static final Collection<String> EXAMPLES = Arrays.asList("0 0", "~ ~", "0.1 -0.5", "~1 ~-2");
   public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.translatable("argument.pos2d.incomplete"));
   private final boolean centerCorrect;

   public Vec2Argument(boolean p_120821_) {
      this.centerCorrect = p_120821_;
   }

   public static Vec2Argument vec2() {
      return new Vec2Argument(true);
   }

   public static Vec2Argument vec2(boolean p_174955_) {
      return new Vec2Argument(p_174955_);
   }

   public static Vec2 getVec2(CommandContext<CommandSourceStack> context, String name) {
      SectorVec3 position = context.getArgument(name, Coordinates.class).getExactPosition(context.getSource());
      return new Vec2((float)position.toApproximateVec3().x, (float)position.toApproximateVec3().z);
   }

   public static SectorVec3 getExactVec2(CommandContext<CommandSourceStack> context, String name) {
      return context.getArgument(name, Coordinates.class).getExactPosition(context.getSource());
   }

   public Coordinates parse(StringReader p_120824_) throws CommandSyntaxException {
      int i = p_120824_.getCursor();
      if (!p_120824_.canRead()) {
         throw ERROR_NOT_COMPLETE.createWithContext(p_120824_);
      } else {
         WorldCoordinate worldcoordinate = WorldCoordinate.parseDouble(p_120824_, this.centerCorrect);
         if (p_120824_.canRead() && p_120824_.peek() == ' ') {
            p_120824_.skip();
            WorldCoordinate worldcoordinate1 = WorldCoordinate.parseDouble(p_120824_, this.centerCorrect);
            return new WorldCoordinates(worldcoordinate, new WorldCoordinate(true, 0.0D), worldcoordinate1);
         } else {
            p_120824_.setCursor(i);
            throw ERROR_NOT_COMPLETE.createWithContext(p_120824_);
         }
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> p_120830_, SuggestionsBuilder p_120831_) {
      if (!(p_120830_.getSource() instanceof SharedSuggestionProvider)) {
         return Suggestions.empty();
      } else {
         String s = p_120831_.getRemaining();
         Collection<SharedSuggestionProvider.TextCoordinates> collection;
         if (!s.isEmpty() && s.charAt(0) == '^') {
            collection = Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_LOCAL);
         } else {
            collection = ((SharedSuggestionProvider)p_120830_.getSource()).getAbsoluteCoordinates();
         }

         return SharedSuggestionProvider.suggest2DCoordinates(s, collection, p_120831_, Commands.createValidator(this::parse));
      }
   }

   public Collection<String> getExamples() {
      return EXAMPLES;
   }
}