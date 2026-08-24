package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public class WrapRouterCommand {
   public static void register(CommandDispatcher<CommandSourceStack> p_245866_) {
      p_245866_.register(Commands.literal("wraprouter").requires((p_245868_) -> {
         return p_245868_.hasPermission(2);
      }).executes((p_245864_) -> {
         boolean flag = NoiseBasedChunkGenerator.toggleWrapRouterDebugInfo();
         p_245864_.getSource().sendSuccess(Component.literal("Wrapping noiserouter: " + (flag ? "enabled" : "disabled")), false);
         return 1;
      }));
   }
}
