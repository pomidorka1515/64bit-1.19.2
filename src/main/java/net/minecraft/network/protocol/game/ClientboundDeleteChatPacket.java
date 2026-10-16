package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;

public record ClientboundDeleteChatPacket(MessageSignature messageSignature) implements Packet<ClientGamePacketListener> {
    public ClientboundDeleteChatPacket(FriendlyByteBuf pBuffer) {
        this(new MessageSignature(pBuffer));
    }

    public void write(FriendlyByteBuf pBuffer) {
        this.messageSignature.write(pBuffer);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleDeleteChat(this);
    }
}
