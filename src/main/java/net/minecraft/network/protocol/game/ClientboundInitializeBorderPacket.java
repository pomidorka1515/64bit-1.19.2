package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundInitializeBorderPacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 exactCenter;
    private final double oldSize;
    private final double newSize;
    private final long lerpTime;
    private final int newAbsoluteMaxSize;
    private final int warningBlocks;
    private final int warningTime;

    public ClientboundInitializeBorderPacket(FriendlyByteBuf pBuffer) {
        this.exactCenter = SectorVec3.fromBlockAndFraction(pBuffer.readInt128(), pBuffer.readDouble(),
                0.0D, pBuffer.readInt128(), pBuffer.readDouble());
        this.oldSize = pBuffer.readDouble();
        this.newSize = pBuffer.readDouble();
        this.lerpTime = pBuffer.readVarLong();
        this.newAbsoluteMaxSize = pBuffer.readVarInt();
        this.warningBlocks = pBuffer.readVarInt();
        this.warningTime = pBuffer.readVarInt();
    }

    public ClientboundInitializeBorderPacket(WorldBorder pWorldBorder) {
        this.exactCenter = pWorldBorder.getExactCenter();
        this.oldSize = pWorldBorder.getSize();
        this.newSize = pWorldBorder.getLerpTarget();
        this.lerpTime = pWorldBorder.getLerpRemainingTime();
        this.newAbsoluteMaxSize = pWorldBorder.getAbsoluteMaxSize();
        this.warningBlocks = pWorldBorder.getWarningBlocks();
        this.warningTime = pWorldBorder.getWarningTime();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt128(this.exactCenter.blockX());
        pBuffer.writeDouble(this.exactCenter.subX());
        pBuffer.writeInt128(this.exactCenter.blockZ());
        pBuffer.writeDouble(this.exactCenter.subZ());
        pBuffer.writeDouble(this.oldSize);
        pBuffer.writeDouble(this.newSize);
        pBuffer.writeVarLong(this.lerpTime);
        pBuffer.writeVarInt(this.newAbsoluteMaxSize);
        pBuffer.writeVarInt(this.warningBlocks);
        pBuffer.writeVarInt(this.warningTime);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleInitializeBorder(this);
    }

    public SectorVec3 getExactCenter() {
        return this.exactCenter;
    }

    /** Lossy mirror for legacy display callers. */

    /** Lossy mirror for legacy display callers. */

    public double getNewSize() {
        return this.newSize;
    }

    public double getOldSize() {
        return this.oldSize;
    }

    public long getLerpTime() {
        return this.lerpTime;
    }

    public int getNewAbsoluteMaxSize() {
        return this.newAbsoluteMaxSize;
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }
}
