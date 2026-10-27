package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLightUpdatePacket implements Packet<ClientGamePacketListener> {
    private final Int1088 x;
    private final Int1088 z;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLightUpdatePacket(ChunkPos pChunkPos, LevelLightEngine pLightEngine, @Nullable BitSet pChangedSkySections, @Nullable BitSet pChangedBlockSections, boolean pTrustEdges) {
        this.x = pChunkPos.x();
        this.z = pChunkPos.z();
        this.lightData = new ClientboundLightUpdatePacketData(pChunkPos, pLightEngine, pChangedSkySections, pChangedBlockSections, pTrustEdges);
    }

    public ClientboundLightUpdatePacket(FriendlyByteBuf pBuffer) {
        ChunkPos chunkpos = pBuffer.readChunkPos();
        this.x = chunkpos.x();
        this.z = chunkpos.z();
        this.lightData = new ClientboundLightUpdatePacketData(pBuffer, this.x, this.z);
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeChunkPos(new ChunkPos(this.x, this.z));
        this.lightData.write(pBuffer);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleLightUpdatePacket(this);
    }

    public Int1088 getX() {
        return this.x;
    }

    public Int1088 getZ() {
        return this.z;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
