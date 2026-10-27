package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundAddPlayerPacket implements Packet<ClientGamePacketListener> {
    private final int entityId;
    private final UUID playerId;
    private final SectorVec3 exactPosition;
    private final byte yRot;
    private final byte xRot;

    public ClientboundAddPlayerPacket(Player pPlayer) {
        this.entityId = pPlayer.getId();
        this.playerId = pPlayer.getGameProfile().getId();
        this.exactPosition = pPlayer.sectorPosition();
        this.yRot = (byte) ((int) (pPlayer.getYRot() * 256.0F / 360.0F));
        this.xRot = (byte) ((int) (pPlayer.getXRot() * 256.0F / 360.0F));
    }

    public ClientboundAddPlayerPacket(FriendlyByteBuf pBuffer) {
        this.entityId = pBuffer.readVarInt();
        this.playerId = pBuffer.readUUID();
        this.exactPosition = pBuffer.readSectorVec3();
        this.yRot = pBuffer.readByte();
        this.xRot = pBuffer.readByte();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.entityId);
        pBuffer.writeUUID(this.playerId);
        pBuffer.writeSectorVec3(this.exactPosition);
        pBuffer.writeByte(this.yRot);
        pBuffer.writeByte(this.xRot);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleAddPlayer(this);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public UUID getPlayerId() {
        return this.playerId;
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
}
