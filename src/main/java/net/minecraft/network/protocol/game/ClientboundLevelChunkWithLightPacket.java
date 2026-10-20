package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.core.Int128;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    private final Int128 x;
    private final Int128 z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLevelChunkWithLightPacket(LevelChunk pLevelChunk, LevelLightEngine pLevelLightEngine, @Nullable BitSet pSkyLight, @Nullable BitSet pBlockLight, boolean pTrustEdges) {
        ChunkPos chunkpos = pLevelChunk.getPos();
        this.x = chunkpos.x();
        this.z = chunkpos.z();
        this.chunkData = new ClientboundLevelChunkPacketData(pLevelChunk);
        this.lightData = new ClientboundLightUpdatePacketData(chunkpos, pLevelLightEngine, pSkyLight, pBlockLight, pTrustEdges);
    }

    public ClientboundLevelChunkWithLightPacket(FriendlyByteBuf pBuffer) {
        ChunkPos chunkpos = pBuffer.readChunkPos();
        this.x = chunkpos.x();
        this.z = chunkpos.z();
        this.chunkData = new ClientboundLevelChunkPacketData(pBuffer, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(pBuffer, this.x, this.z);
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeChunkPos(new ChunkPos(this.x, this.z));
        this.chunkData.write(pBuffer);
        this.lightData.write(pBuffer);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleLevelChunkWithLight(this);
    }

    public Int128 getX() {
        return this.x;
    }

    public Int128 getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
