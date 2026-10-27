package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundTeleportEntityPacket implements Packet<ClientGamePacketListener> {
    private final int id;
    private final SectorVec3 exactPosition;
    private final byte yRot;
    private final byte xRot;
    private final boolean onGround;

    public ClientboundTeleportEntityPacket(Entity pEntity) {
        this.id = pEntity.getId();
        this.exactPosition = pEntity.sectorPosition();
        this.yRot = (byte) ((int) (pEntity.getYRot() * 256.0F / 360.0F));
        this.xRot = (byte) ((int) (pEntity.getXRot() * 256.0F / 360.0F));
        this.onGround = pEntity.isOnGround();
    }

    public ClientboundTeleportEntityPacket(FriendlyByteBuf pBuffer) {
        this.id = pBuffer.readVarInt();
        this.exactPosition = pBuffer.readSectorVec3();
        this.yRot = pBuffer.readByte();
        this.xRot = pBuffer.readByte();
        this.onGround = pBuffer.readBoolean();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.id);
        SectorVec3 position = this.exactPosition;
        pBuffer.writeSectorVec3(position);
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

    public double getY() {
        return this.exactPosition.y();
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
