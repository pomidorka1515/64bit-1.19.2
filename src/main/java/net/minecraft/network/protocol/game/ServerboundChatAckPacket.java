package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.Packet;

public record ServerboundChatAckPacket(LastSeenMessages.Update lastSeenMessages) implements Packet<ServerGamePacketListener> {
    public ServerboundChatAckPacket(FriendlyByteBuf pBuffer) {
        this(new LastSeenMessages.Update(pBuffer));
    }

    public void write(FriendlyByteBuf pBuffer) {
        this.lastSeenMessages.write(pBuffer);
    }

    public void handle(ServerGamePacketListener pHandler) {
        pHandler.handleChatAck(this);
    }
}
