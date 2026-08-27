package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

public final class FrustumCommand {
   private FrustumCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("frustum").requires((source) -> {
         return source.hasPermission(0);
      }).executes((context) -> toggle(context.getSource())));
   }

   private static int toggle(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
      player.connection.send(new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.FRUSTUM_TOGGLE, data));
      source.sendSuccess(Component.literal("Frustum culling toggled."), false);
      return 1;
   }
}
