package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundPlayerLookAtPacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 position;
    private final int entity;
    private final EntityAnchorArgument.Anchor fromAnchor;
    private final EntityAnchorArgument.Anchor toAnchor;
    private final boolean atEntity;

    public ClientboundPlayerLookAtPacket(EntityAnchorArgument.Anchor pFromAnchor, double pX, double pY, double pZ) {
        this(pFromAnchor, SectorVec3.fromApproximate(pX, pY, pZ));
    }

    public ClientboundPlayerLookAtPacket(EntityAnchorArgument.Anchor pFromAnchor, SectorVec3 position) {
        this.fromAnchor = pFromAnchor;
        this.position = position;
        this.entity = 0;
        this.atEntity = false;
        this.toAnchor = null;
    }

    public ClientboundPlayerLookAtPacket(EntityAnchorArgument.Anchor pFromAnchor, Entity pEntity, EntityAnchorArgument.Anchor pToAnchor) {
        this.fromAnchor = pFromAnchor;
        this.entity = pEntity.getId();
        this.toAnchor = pToAnchor;
        this.position = pToAnchor.applyExact(pEntity);
        this.atEntity = true;
    }

    public ClientboundPlayerLookAtPacket(FriendlyByteBuf pBuffer) {
        this.fromAnchor = pBuffer.readEnum(EntityAnchorArgument.Anchor.class);
        this.position = SectorVec3.fromBlockAndFraction(pBuffer.readInt128(), pBuffer.readDouble(),
                pBuffer.readDouble(), pBuffer.readInt128(), pBuffer.readDouble());
        this.atEntity = pBuffer.readBoolean();
        if (this.atEntity) {
            this.entity = pBuffer.readVarInt();
            this.toAnchor = pBuffer.readEnum(EntityAnchorArgument.Anchor.class);
        } else {
            this.entity = 0;
            this.toAnchor = null;
        }
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeEnum(this.fromAnchor);
        pBuffer.writeInt128(this.position.blockX());
        pBuffer.writeDouble(this.position.subX());
        pBuffer.writeDouble(this.position.y());
        pBuffer.writeInt128(this.position.blockZ());
        pBuffer.writeDouble(this.position.subZ());
        pBuffer.writeBoolean(this.atEntity);
        if (this.atEntity) {
            pBuffer.writeVarInt(this.entity);
            pBuffer.writeEnum(this.toAnchor);
        }
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleLookAt(this);
    }

    public EntityAnchorArgument.Anchor getFromAnchor() {
        return this.fromAnchor;
    }

    @Nullable
    public Vec3 getPosition(Level pLevel) {
        return this.getExactPosition(pLevel).toExactVec3();
    }

    public SectorVec3 getExactPosition(Level pLevel) {
        if (this.atEntity) {
            Entity entity = pLevel.getEntity(this.entity);
            return entity == null ? this.position : this.toAnchor.applyExact(entity);
        } else {
            return this.position;
        }
    }

    /** Exact fallback target carried on the wire, before resolving an entity anchor. */
    public SectorVec3 getExactPosition() {
        return this.position;
    }
}
