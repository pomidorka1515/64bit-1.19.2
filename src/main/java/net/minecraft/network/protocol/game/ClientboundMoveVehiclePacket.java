package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundMoveVehiclePacket implements Packet<ClientGamePacketListener> {
    private final double x;
    private final double y;
    private final double z;
    private final SectorVec3 exactPosition;
    private final float yRot;
    private final float xRot;

    public ClientboundMoveVehiclePacket(Entity pVehicle) {
        this.exactPosition = pVehicle.sectorPosition();
        net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
        this.x = approximate.x;
        this.y = approximate.y;
        this.z = approximate.z;
        this.yRot = pVehicle.getYRot();
        this.xRot = pVehicle.getXRot();
    }

    public ClientboundMoveVehiclePacket(FriendlyByteBuf pBuffer) {
        this.exactPosition = SectorVec3.fromBlockAndFraction(pBuffer.readLong(), pBuffer.readDouble(),
                pBuffer.readDouble(), pBuffer.readLong(), pBuffer.readDouble());
        net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
        this.x = approximate.x;
        this.y = approximate.y;
        this.z = approximate.z;
        this.yRot = pBuffer.readFloat();
        this.xRot = pBuffer.readFloat();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeLong(this.exactPosition.blockX());
        pBuffer.writeDouble(this.exactPosition.subX());
        pBuffer.writeDouble(this.exactPosition.y());
        pBuffer.writeLong(this.exactPosition.blockZ());
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

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getXRot() {
        return this.xRot;
    }
}
