package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundSetBorderCenterPacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 exactCenter;

    public ClientboundSetBorderCenterPacket(WorldBorder pWorldBorder) {
        this.exactCenter = pWorldBorder.getExactCenter();
    }

    public ClientboundSetBorderCenterPacket(FriendlyByteBuf pBuffer) {
        this.exactCenter = SectorVec3.fromBlockAndFraction(pBuffer.readInt128(), pBuffer.readDouble(),
                0.0D, pBuffer.readInt128(), pBuffer.readDouble());
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt128(this.exactCenter.blockX());
        pBuffer.writeDouble(this.exactCenter.subX());
        pBuffer.writeInt128(this.exactCenter.blockZ());
        pBuffer.writeDouble(this.exactCenter.subZ());
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleSetBorderCenter(this);
    }

    public SectorVec3 getExactCenter() {
        return this.exactCenter;
    }

}
