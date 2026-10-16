package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;

public record ClientboundChatPreviewPacket(int queryId, @Nullable Component preview) implements Packet<ClientGamePacketListener> {
    public ClientboundChatPreviewPacket(FriendlyByteBuf pBuffer) {
        this(pBuffer.readInt(), pBuffer.readNullable(FriendlyByteBuf::readComponent));
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt(this.queryId);
        pBuffer.writeNullable(this.preview, FriendlyByteBuf::writeComponent);
    }

    public boolean isSkippable() {
        return true;
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleChatPreview(this);
    }
}
