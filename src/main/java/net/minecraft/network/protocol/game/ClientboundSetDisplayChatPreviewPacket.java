package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public record ClientboundSetDisplayChatPreviewPacket(boolean enabled) implements Packet<ClientGamePacketListener> {
    public ClientboundSetDisplayChatPreviewPacket(FriendlyByteBuf pBuffer) {
        this(pBuffer.readBoolean());
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeBoolean(this.enabled);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleSetDisplayChatPreview(this);
    }
}
