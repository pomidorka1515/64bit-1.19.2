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
   private static final Map<Requester, FarlandsMode.Mode> PENDING_CHANGES = new ConcurrentHashMap<>();

   private FarlandsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("farlands").requires((source) -> {
         return source.hasPermission(3);
      }).executes((context) -> {
         FarlandsMode.Mode mode = FarlandsMode.getMode();
         context.getSource().sendSuccess(Component.literal("Far Lands are currently in " + mode.generatorDescription() + " mode."), false);
         return 1;
      }).then(Commands.literal("32bit").executes((context) -> {
         return requestChange(context.getSource(), FarlandsMode.Mode.BIT_32);
      })).then(Commands.literal("32bit-hybrid").executes((context) -> {
         return requestChange(context.getSource(), FarlandsMode.Mode.BIT_32_HYBRID);
      })).then(Commands.literal("64bit").executes((context) -> {
         return requestChange(context.getSource(), FarlandsMode.Mode.BIT_64);
      })).then(Commands.literal("off").executes((context) -> {
         return requestChange(context.getSource(), FarlandsMode.Mode.OFF);
      })).then(Commands.literal("help").executes((context) -> {
         context.getSource().sendSuccess(Component.literal("Generator modes:\n32-bit: Original behaviour of mckuhei's mod, farlands at 12550824.\n32-bit-hybrid: classic 12mil farlands, but normal router, cave, aquifer, and ore noise use 64-bit arithmetic.\n64-bit: uses a new 64-bit generator, with farlands at ~53.9 quadrillion blocks.\noff: uses a 64-bit generator, but with farlands fully patched."), false);
         return 1;
      })).then(Commands.literal("confirm").executes((context) -> {
         return confirm(context.getSource());
      })));
   }

   private static int requestChange(CommandSourceStack source, FarlandsMode.Mode mode) {
      PENDING_CHANGES.put(Requester.of(source), mode);
      source.sendSuccess(Component.literal("Far Lands mode change into " + mode.generatorDescription() + " requested. Run /farlands confirm to confirm."), false);
      return 1;
   }

   private static int confirm(CommandSourceStack source) {
      Requester requester = Requester.of(source);
      FarlandsMode.Mode mode = PENDING_CHANGES.remove(requester);
      if (mode == null) {
         source.sendFailure(Component.literal("There is no Far Lands change waiting for confirmation."));
         return 0;
      }

      FarlandsMode.setMode(mode);
      source.getServer().saveEverything(false, false, true);
      source.sendSuccess(Component.literal("Far Lands mode has been changed to " + mode.generatorDescription() + "."), true);
      return 1;
   }

   private record Requester(MinecraftServer server, @Nullable UUID player, String name) {
      private static Requester of(CommandSourceStack source) {
         ServerPlayer player = source.getPlayer();
         return new Requester(source.getServer(), player == null ? null : player.getUUID(), player == null ? source.getTextName() : "");
      }
   }
}
