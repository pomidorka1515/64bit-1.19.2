package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundMoveVehiclePacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 exactPosition;
    private final float yRot;
    private final float xRot;

    public ClientboundMoveVehiclePacket(Entity pVehicle) {
        this.exactPosition = pVehicle.sectorPosition();
        this.yRot = pVehicle.getYRot();
        this.xRot = pVehicle.getXRot();
    }

    public ClientboundMoveVehiclePacket(FriendlyByteBuf pBuffer) {
        this.exactPosition = SectorVec3.fromBlockAndFraction(pBuffer.readInt128(), pBuffer.readDouble(),
                pBuffer.readDouble(), pBuffer.readInt128(), pBuffer.readDouble());
        this.yRot = pBuffer.readFloat();
        this.xRot = pBuffer.readFloat();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt128(this.exactPosition.blockX());
        pBuffer.writeDouble(this.exactPosition.subX());
        pBuffer.writeDouble(this.exactPosition.y());
        pBuffer.writeInt128(this.exactPosition.blockZ());
        pBuffer.writeDouble(this.exactPosition.subZ());
        pBuffer.writeFloat(this.yRot);
        pBuffer.writeFloat(this.xRot);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleMoveVehicle(this);
    }

    public SectorVec3 getExactPosition() {
        return this.exactPosition;
    }

    public double getY() {
        return this.exactPosition.y();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getXRot() {
        return this.xRot;
    }
}
