package net.minecraft.network.protocol.game;

import net.minecraft.core.Int128;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;

public class ClientboundForgetLevelChunkPacket implements Packet<ClientGamePacketListener> {
    private final Int128 x;
    private final Int128 z;

    public ClientboundForgetLevelChunkPacket(long pX, long pZ) {
        this(Int128.fromLong(pX), Int128.fromLong(pZ));
    }

    public ClientboundForgetLevelChunkPacket(Int128 pX, Int128 pZ) {
        ChunkPos chunkpos = new ChunkPos(pX, pZ);
        this.x = chunkpos.x();
        this.z = chunkpos.z();
    }

    public ClientboundForgetLevelChunkPacket(FriendlyByteBuf pBuffer) {
        ChunkPos chunkpos = pBuffer.readChunkPos();
        this.x = chunkpos.x();
        this.z = chunkpos.z();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeChunkPos(new ChunkPos(this.x, this.z));
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleForgetLevelChunk(this);
    }

    public Int128 getX() {
        return this.x;
    }

    public Int128 getZ() {
        return this.z;
    }
}
