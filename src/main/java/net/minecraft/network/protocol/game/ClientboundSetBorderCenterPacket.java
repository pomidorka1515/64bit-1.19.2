package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Int1088;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundSetBorderCenterPacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 exactCenter;

    public ClientboundSetBorderCenterPacket(WorldBorder pWorldBorder) {
        this.exactCenter = pWorldBorder.getExactCenter();
    }

    public ClientboundSetBorderCenterPacket(FriendlyByteBuf pBuffer) {
        Int1088 x = pBuffer.readInt1088();
        double subX = pBuffer.readDouble();
        Int1088 z = pBuffer.readInt1088();
        double subZ = pBuffer.readDouble();
        if (!Double.isFinite(subX) || subX < 0.0D || subX >= 1.0D
                || !Double.isFinite(subZ) || subZ < 0.0D || subZ >= 1.0D) {
            throw new DecoderException("Invalid border center fraction");
        }
        this.exactCenter = SectorVec3.fromBlockAndFraction(x, subX, 0.0D, z, subZ);
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt1088(this.exactCenter.blockX());
        pBuffer.writeDouble(this.exactCenter.subX());
        pBuffer.writeInt1088(this.exactCenter.blockZ());
        pBuffer.writeDouble(this.exactCenter.subZ());
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleSetBorderCenter(this);
    }

    public SectorVec3 getExactCenter() {
        return this.exactCenter;
    }

}
