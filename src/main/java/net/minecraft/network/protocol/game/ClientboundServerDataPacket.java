package net.minecraft.network.protocol.game;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public class ClientboundServerDataPacket implements Packet<ClientGamePacketListener> {
    private final Optional<Component> motd;
    private final Optional<String> iconBase64;
    private final boolean previewsChat;

    public ClientboundServerDataPacket(@Nullable Component pMotd, @Nullable String pIconBase64, boolean pPreviewsChat) {
        this.motd = Optional.ofNullable(pMotd);
        this.iconBase64 = Optional.ofNullable(pIconBase64);
        this.previewsChat = pPreviewsChat;
    }

    public ClientboundServerDataPacket(FriendlyByteBuf pBuffer) {
        this.motd = pBuffer.readOptional(FriendlyByteBuf::readComponent);
        this.iconBase64 = pBuffer.readOptional(FriendlyByteBuf::readUtf);
        this.previewsChat = pBuffer.readBoolean();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeOptional(this.motd, FriendlyByteBuf::writeComponent);
        pBuffer.writeOptional(this.iconBase64, FriendlyByteBuf::writeUtf);
        pBuffer.writeBoolean(this.previewsChat);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleServerData(this);
    }

    public Optional<Component> getMotd() {
        return this.motd;
    }

    public Optional<String> getIconBase64() {
        return this.iconBase64;
    }

    public boolean previewsChat() {
        return this.previewsChat;
    }

}
