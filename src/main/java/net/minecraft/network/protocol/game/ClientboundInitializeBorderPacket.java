package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Int1088;
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
        Int1088 x = pBuffer.readInt1088();
        double subX = pBuffer.readDouble();
        Int1088 z = pBuffer.readInt1088();
        double subZ = pBuffer.readDouble();
        if (!Double.isFinite(subX) || subX < 0.0D || subX >= 1.0D
                || !Double.isFinite(subZ) || subZ < 0.0D || subZ >= 1.0D) {
            throw new DecoderException("Invalid border center fraction");
        }
        this.exactCenter = SectorVec3.fromBlockAndFraction(x, subX, 0.0D, z, subZ);
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
        pBuffer.writeInt1088(this.exactCenter.blockX());
        pBuffer.writeDouble(this.exactCenter.subX());
        pBuffer.writeInt1088(this.exactCenter.blockZ());
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
