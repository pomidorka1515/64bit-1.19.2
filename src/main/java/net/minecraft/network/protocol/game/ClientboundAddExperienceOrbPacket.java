package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundAddExperienceOrbPacket implements Packet<ClientGamePacketListener> {
    private final int id;
    private final SectorVec3 exactPosition;
    private final int value;

    public ClientboundAddExperienceOrbPacket(ExperienceOrb pOrbEntity) {
        this.id = pOrbEntity.getId();
        this.exactPosition = pOrbEntity.sectorPosition();
        this.value = pOrbEntity.getValue();
    }

    public ClientboundAddExperienceOrbPacket(FriendlyByteBuf pBuffer) {
        this.id = pBuffer.readVarInt();
        this.exactPosition = pBuffer.readSectorVec3();
        this.value = pBuffer.readShort();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.id);
        pBuffer.writeSectorVec3(this.exactPosition);
        pBuffer.writeShort(this.value);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleAddExperienceOrb(this);
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

    public int getValue() {
        return this.value;
    }
}
