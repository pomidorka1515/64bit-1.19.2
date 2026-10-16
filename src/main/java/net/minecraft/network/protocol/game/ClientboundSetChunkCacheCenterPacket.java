package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ClientboundSetChunkCacheCenterPacket implements Packet<ClientGamePacketListener> {
    private final long x;
    private final long z;

    public ClientboundSetChunkCacheCenterPacket(long pX, long pZ) {
        this.x = pX;
        this.z = pZ;
    }

    public ClientboundSetChunkCacheCenterPacket(FriendlyByteBuf pBuffer) {
        this.x = pBuffer.readVarLong();
        this.z = pBuffer.readVarLong();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarLong(this.x);
        pBuffer.writeVarLong(this.z);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleSetChunkCacheCenter(this);
    }

    public long getX() {
        return this.x;
    }

    public long getZ() {
        return this.z;
    }
}
