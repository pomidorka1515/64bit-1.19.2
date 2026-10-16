package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ClientboundForgetLevelChunkPacket implements Packet<ClientGamePacketListener> {
    private final long x;
    private final long z;

    public ClientboundForgetLevelChunkPacket(long pX, long pZ) {
        this.x = pX;
        this.z = pZ;
    }

    public ClientboundForgetLevelChunkPacket(FriendlyByteBuf pBuffer) {
        this.x = pBuffer.readLong();
        this.z = pBuffer.readLong();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeLong(this.x);
        pBuffer.writeLong(this.z);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleForgetLevelChunk(this);
    }

    public long getX() {
        return this.x;
    }

    public long getZ() {
        return this.z;
    }
}
