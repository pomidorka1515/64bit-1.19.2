package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundTeleportEntityPacket implements Packet<ClientGamePacketListener> {
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final SectorVec3 exactPosition;
    private final byte yRot;
    private final byte xRot;
    private final boolean onGround;

    public ClientboundTeleportEntityPacket(Entity pEntity) {
        this.id = pEntity.getId();
        this.exactPosition = pEntity.sectorPosition();
        Vec3 vec3 = this.exactPosition.toApproximateVec3();
        this.x = vec3.x;
        this.y = vec3.y;
        this.z = vec3.z;
        this.yRot = (byte) ((int) (pEntity.getYRot() * 256.0F / 360.0F));
        this.xRot = (byte) ((int) (pEntity.getXRot() * 256.0F / 360.0F));
        this.onGround = pEntity.isOnGround();
    }

    public ClientboundTeleportEntityPacket(FriendlyByteBuf pBuffer) {
        this.id = pBuffer.readVarInt();
        this.exactPosition = SectorVec3.fromBlockAndFraction(pBuffer.readLong(), pBuffer.readDouble(),
                pBuffer.readDouble(), pBuffer.readLong(), pBuffer.readDouble());
        Vec3 approximate = this.exactPosition.toApproximateVec3();
        this.x = approximate.x;
        this.y = approximate.y;
        this.z = approximate.z;
        this.yRot = pBuffer.readByte();
        this.xRot = pBuffer.readByte();
        this.onGround = pBuffer.readBoolean();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.id);
        SectorVec3 position = this.exactPosition;
        pBuffer.writeLong(position.blockX());
        pBuffer.writeDouble(position.subX());
        pBuffer.writeDouble(position.y());
        pBuffer.writeLong(position.blockZ());
        pBuffer.writeDouble(position.subZ());
        pBuffer.writeByte(this.yRot);
        pBuffer.writeByte(this.xRot);
        pBuffer.writeBoolean(this.onGround);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleTeleportEntity(this);
    }

    public int getId() {
        return this.id;
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

    public byte getyRot() {
        return this.yRot;
    }

    public byte getxRot() {
        return this.xRot;
    }

    public boolean isOnGround() {
        return this.onGround;
    }
}
