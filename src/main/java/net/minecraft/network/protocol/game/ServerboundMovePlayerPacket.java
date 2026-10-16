package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.SectorVec3;
import javax.annotation.Nullable;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {
    protected final double x;
    protected final double y;
    protected final double z;
    protected final float yRot;
    protected final float xRot;
    protected final boolean onGround;
    protected final boolean hasPos;
    protected final boolean hasRot;
    @Nullable
    private final Long exactBlockX;
    private final double exactSubX;
    @Nullable
    private final Long exactBlockZ;
    private final double exactSubZ;

    protected ServerboundMovePlayerPacket(double pX, double pY, double pZ, float pYRot, float pXRot, boolean pOnGround, boolean pHasPos, boolean pHasRot) {
        this(pX, pY, pZ, pYRot, pXRot, pOnGround, pHasPos, pHasRot, null);
    }

    protected ServerboundMovePlayerPacket(double x, double y, double z, float yRot, float xRot, boolean onGround,
            boolean hasPos, boolean hasRot, @Nullable SectorVec3 exactPosition) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.hasPos = hasPos;
        this.hasRot = hasRot;
        this.exactBlockX = exactPosition == null ? null : exactPosition.blockX();
        this.exactSubX = exactPosition == null ? 0.0D : exactPosition.subX();
        this.exactBlockZ = exactPosition == null ? null : exactPosition.blockZ();
        this.exactSubZ = exactPosition == null ? 0.0D : exactPosition.subZ();
    }

    /** Exact sector X/Z payload, when this packet was created by the local player. */
    @Nullable
    public SectorVec3 getExactPosition(double fallbackY) {
        return this.exactBlockX == null ? null : SectorVec3.fromBlockAndFraction(this.exactBlockX, this.exactSubX, fallbackY, this.exactBlockZ, this.exactSubZ);
    }

    public void handle(ServerGamePacketListener pHandler) {
        pHandler.handleMovePlayer(this);
    }

    public double getX(double pDefaultValue) {
        return this.hasPos ? this.x : pDefaultValue;
    }

    public double getY(double pDefaultValue) {
        return this.hasPos ? this.y : pDefaultValue;
    }

    public double getZ(double pDefaultValue) {
        return this.hasPos ? this.z : pDefaultValue;
    }

    public boolean hasExactPosition() {
        return this.exactBlockX != null;
    }

    public long getExactBlockX() {
        return this.exactBlockX;
    }
    public double getExactSubX() {
        return this.exactSubX;
    }
    public long getExactBlockZ() {
        return this.exactBlockZ;
    }
    public double getExactSubZ() {
        return this.exactSubZ;
    }

    public float getYRot(float pDefaultValue) {
        return this.hasRot ? this.yRot : pDefaultValue;
    }

    public float getXRot(float pDefaultValue) {
        return this.hasRot ? this.xRot : pDefaultValue;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public static class Pos extends ServerboundMovePlayerPacket {
        public Pos(double x, double y, double z, boolean onGround) {
            super(x, y, z, 0.0F, 0.0F, onGround, true, false);
        }

        public Pos(SectorVec3 position, boolean onGround) {
            super(position.toApproximateVec3().x, position.y(), position.toApproximateVec3().z,
                    0.0F, 0.0F, onGround, true, false, position);
        }

        public static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf buf) {
            long blockX = buf.readLong();
            double subX = buf.readDouble();
            double y = buf.readDouble();
            long blockZ = buf.readLong();
            double subZ = buf.readDouble();
            boolean onGround = buf.readUnsignedByte() != 0;
            return new Pos(SectorVec3.fromBlockAndFraction(blockX, subX, y, blockZ, subZ), onGround);
        }

        public void write(FriendlyByteBuf buf) {
            SectorVec3 position = this.getExactPosition(this.y);
            buf.writeLong(position == null ? (long) Math.floor(this.x) : position.blockX());
            buf.writeDouble(position == null ? this.x - Math.floor(this.x) : position.subX());
            buf.writeDouble(this.y);
            buf.writeLong(position == null ? (long) Math.floor(this.z) : position.blockZ());
            buf.writeDouble(position == null ? this.z - Math.floor(this.z) : position.subZ());
            buf.writeByte(this.onGround ? 1 : 0);
        }
    }

    public static class PosRot extends ServerboundMovePlayerPacket {
        public PosRot(double x, double y, double z, float yRot, float xRot, boolean onGround) {
            super(x, y, z, yRot, xRot, onGround, true, true);
        }

        public PosRot(SectorVec3 position, float yRot, float xRot, boolean onGround) {
            super(position.toApproximateVec3().x, position.y(), position.toApproximateVec3().z,
                    yRot, xRot, onGround, true, true, position);
        }

        public static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf buf) {
            long blockX = buf.readLong();
            double subX = buf.readDouble();
            double y = buf.readDouble();
            long blockZ = buf.readLong();
            double subZ = buf.readDouble();
            float yRot = buf.readFloat();
            float xRot = buf.readFloat();
            boolean onGround = buf.readUnsignedByte() != 0;
            return new PosRot(SectorVec3.fromBlockAndFraction(blockX, subX, y, blockZ, subZ), yRot, xRot, onGround);
        }

        public void write(FriendlyByteBuf buf) {
            SectorVec3 position = this.getExactPosition(this.y);
            buf.writeLong(position == null ? (long) Math.floor(this.x) : position.blockX());
            buf.writeDouble(position == null ? this.x - Math.floor(this.x) : position.subX());
            buf.writeDouble(this.y);
            buf.writeLong(position == null ? (long) Math.floor(this.z) : position.blockZ());
            buf.writeDouble(position == null ? this.z - Math.floor(this.z) : position.subZ());
            buf.writeFloat(this.yRot);
            buf.writeFloat(this.xRot);
            buf.writeByte(this.onGround ? 1 : 0);
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {
        public Rot(float pYRot, float pXRot, boolean pOnGround) {
            super(0.0D, 0.0D, 0.0D, pYRot, pXRot, pOnGround, false, true);
        }

        public static ServerboundMovePlayerPacket.Rot read(FriendlyByteBuf pBuffer) {
            float f = pBuffer.readFloat();
            float f1 = pBuffer.readFloat();
            boolean flag = pBuffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Rot(f, f1, flag);
        }

        public void write(FriendlyByteBuf pBuffer) {
            pBuffer.writeFloat(this.yRot);
            pBuffer.writeFloat(this.xRot);
            pBuffer.writeByte(this.onGround ? 1 : 0);
        }
    }

    public static class StatusOnly extends ServerboundMovePlayerPacket {
        public StatusOnly(boolean pOnGround) {
            super(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, pOnGround, false, false);
        }

        public static ServerboundMovePlayerPacket.StatusOnly read(FriendlyByteBuf pBuffer) {
            boolean flag = pBuffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.StatusOnly(flag);
        }

        public void write(FriendlyByteBuf pBuffer) {
            pBuffer.writeByte(this.onGround ? 1 : 0);
        }
    }
}
