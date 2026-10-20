package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundAddPlayerPacket implements Packet<ClientGamePacketListener> {
    private final int entityId;
    private final UUID playerId;
    /** Lossy compatibility mirrors; exactPosition is authoritative. */
    private final double x;
    private final double y;
    private final double z;
    private final SectorVec3 exactPosition;
    private final byte yRot;
    private final byte xRot;

    public ClientboundAddPlayerPacket(Player pPlayer) {
        this.entityId = pPlayer.getId();
        this.playerId = pPlayer.getGameProfile().getId();
        this.exactPosition = pPlayer.sectorPosition();
        net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
        this.x = approximate.x;
        this.y = approximate.y;
        this.z = approximate.z;
        this.yRot = (byte) ((int) (pPlayer.getYRot() * 256.0F / 360.0F));
        this.xRot = (byte) ((int) (pPlayer.getXRot() * 256.0F / 360.0F));
    }

    public ClientboundAddPlayerPacket(FriendlyByteBuf pBuffer) {
        this.entityId = pBuffer.readVarInt();
        this.playerId = pBuffer.readUUID();
        this.exactPosition = SectorVec3.fromBlockAndFraction(pBuffer.readInt128(), pBuffer.readDouble(),
                pBuffer.readDouble(), pBuffer.readInt128(), pBuffer.readDouble());
        net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
        this.x = approximate.x;
        this.y = approximate.y;
        this.z = approximate.z;
        this.yRot = pBuffer.readByte();
        this.xRot = pBuffer.readByte();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.entityId);
        pBuffer.writeUUID(this.playerId);
        pBuffer.writeInt128(this.exactPosition.blockX());
        pBuffer.writeDouble(this.exactPosition.subX());
        pBuffer.writeDouble(this.exactPosition.y());
        pBuffer.writeInt128(this.exactPosition.blockZ());
        pBuffer.writeDouble(this.exactPosition.subZ());
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
}
