package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    private final long x;
    private final long z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLevelChunkWithLightPacket(LevelChunk pLevelChunk, LevelLightEngine pLevelLightEngine, @Nullable BitSet pSkyLight, @Nullable BitSet pBlockLight, boolean pTrustEdges) {
        ChunkPos chunkpos = pLevelChunk.getPos();
        this.x = chunkpos.x;
        this.z = chunkpos.z;
        this.chunkData = new ClientboundLevelChunkPacketData(pLevelChunk);
        this.lightData = new ClientboundLightUpdatePacketData(chunkpos, pLevelLightEngine, pSkyLight, pBlockLight, pTrustEdges);
    }

    public ClientboundLevelChunkWithLightPacket(FriendlyByteBuf pBuffer) {
        this.x = pBuffer.readLong();
        this.z = pBuffer.readLong();
        this.chunkData = new ClientboundLevelChunkPacketData(pBuffer, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(pBuffer, this.x, this.z);
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeLong(this.x);
        pBuffer.writeLong(this.z);
        this.chunkData.write(pBuffer);
        this.lightData.write(pBuffer);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleLevelChunkWithLight(this);
    }

    public long getX() {
        return this.x;
    }

    public long getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
