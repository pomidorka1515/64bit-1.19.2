package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLightUpdatePacket implements Packet<ClientGamePacketListener> {
    private final long x;
    private final long z;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLightUpdatePacket(ChunkPos pChunkPos, LevelLightEngine pLightEngine, @Nullable BitSet pChangedSkySections, @Nullable BitSet pChangedBlockSections, boolean pTrustEdges) {
        this.x = pChunkPos.x;
        this.z = pChunkPos.z;
        this.lightData = new ClientboundLightUpdatePacketData(pChunkPos, pLightEngine, pChangedSkySections, pChangedBlockSections, pTrustEdges);
    }

    public ClientboundLightUpdatePacket(FriendlyByteBuf pBuffer) {
        this.x = pBuffer.readVarLong();
        this.z = pBuffer.readVarLong();
        this.lightData = new ClientboundLightUpdatePacketData(pBuffer, this.x, this.z);
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarLong(this.x);
        pBuffer.writeVarLong(this.z);
        this.lightData.write(pBuffer);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleLightUpdatePacket(this);
    }

    public long getX() {
        return this.x;
    }

    public long getZ() {
        return this.z;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
