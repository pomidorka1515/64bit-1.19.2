package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.EndRingsMode;

public final class EndRingsCommand {
   private static final Set<Requester> PENDING_DISABLES = ConcurrentHashMap.newKeySet();

   private EndRingsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("endrings").requires((source) -> {
         return source.hasPermission(3);
      }).then(Commands.literal("enable").executes((context) -> {
         return enable(context.getSource());
      })).then(Commands.literal("disable").executes((context) -> {
         return requestDisable(context.getSource());
      })).then(Commands.literal("confirm").executes((context) -> {
         return confirmDisable(context.getSource());
      })));
   }

   private static int enable(CommandSourceStack source) {
      PENDING_DISABLES.remove(Requester.of(source));
      EndRingsMode.setEnabled(true);
      source.getServer().saveEverything(false, false, true);
      source.sendSuccess(Component.literal("End rings are enabled."), true);
      return 1;
   }

   private static int requestDisable(CommandSourceStack source) {
      PENDING_DISABLES.add(Requester.of(source));
      source.sendSuccess(Component.literal("Disabling end rings has been requested. Run /endrings confirm to confirm."), false);
      return 1;
   }

   private static int confirmDisable(CommandSourceStack source) {
      if (!PENDING_DISABLES.remove(Requester.of(source))) {
         source.sendFailure(Component.literal("There is no end rings change waiting for confirmation."));
         return 0;
      }

      EndRingsMode.setEnabled(false);
      source.getServer().saveEverything(false, false, true);
      source.sendSuccess(Component.literal("End rings are disabled."), true);
      return 1;
   }

   private record Requester(MinecraftServer server, @Nullable UUID player, String name) {
      private static Requester of(CommandSourceStack source) {
         ServerPlayer player = source.getPlayer();
         return new Requester(source.getServer(), player == null ? null : player.getUUID(), player == null ? source.getTextName() : "");
      }
   }
}
