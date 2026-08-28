package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;

public final class FarlandsCommand {
   private static final Map<Requester, Boolean> PENDING_CHANGES = new ConcurrentHashMap<>();

   private FarlandsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("farlands").requires((source) -> {
         return source.hasPermission(3);
      }).executes((context) -> {
         boolean enabled = FarlandsMode.isEnabled();
         context.getSource().sendSuccess(Component.literal("Far Lands are currently in " + (enabled ? "32-bit" : "64-bit") + " mode."), false);
         return 1;
      }).then(Commands.literal("32bit").executes((context) -> {
         return requestChange(context.getSource(), true);
      })).then(Commands.literal("64bit").executes((context) -> {
         return requestChange(context.getSource(), false);
      })).then(Commands.literal("confirm").executes((context) -> {
         return confirm(context.getSource());
      })));
   }

   private static int requestChange(CommandSourceStack source, boolean enabled) {
      PENDING_CHANGES.put(Requester.of(source), enabled);
      source.sendSuccess(Component.literal("Far Lands mode change into " + (enabled ? "32-bit" : "64-bit") + " requested. Run /farlands confirm to confirm."), false);
      return 1;
   }

   private static int confirm(CommandSourceStack source) {
      Requester requester = Requester.of(source);
      Boolean enabled = PENDING_CHANGES.remove(requester);
      if (enabled == null) {
         source.sendFailure(Component.literal("There is no Far Lands change waiting for confirmation."));
         return 0;
      }

      FarlandsMode.setEnabled(enabled);
      source.sendSuccess(Component.literal("Far Lands mode has been changed to " + (enabled ? "32-bit" : "64-bit") + "."), true);
      return 1;
   }

   private record Requester(MinecraftServer server, @Nullable UUID player, String name) {
      private static Requester of(CommandSourceStack source) {
         ServerPlayer player = source.getPlayer();
         return new Requester(source.getServer(), player == null ? null : player.getUUID(), player == null ? source.getTextName() : "");
      }
   }
}
