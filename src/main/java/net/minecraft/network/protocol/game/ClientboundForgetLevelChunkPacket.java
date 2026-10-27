package net.minecraft.network.protocol.game;

import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;

public class ClientboundForgetLevelChunkPacket implements Packet<ClientGamePacketListener> {
    private final Int1088 x;
    private final Int1088 z;

    public ClientboundForgetLevelChunkPacket(long pX, long pZ) {
        this(Int1088.fromLong(pX), Int1088.fromLong(pZ));
    }

    public ClientboundForgetLevelChunkPacket(Int1088 pX, Int1088 pZ) {
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

    public Int1088 getX() {
        return this.x;
    }

    public Int1088 getZ() {
        return this.z;
    }
}
