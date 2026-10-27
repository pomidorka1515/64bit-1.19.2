package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.SectorVec3;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {
    protected final double y;
    protected final float yRot;
    protected final float xRot;
    protected final boolean onGround;
    protected final boolean hasPos;
    protected final boolean hasRot;
    @Nullable
    private final Int1088 exactBlockX;
    private final double exactSubX;
    @Nullable
    private final Int1088 exactBlockZ;
    private final double exactSubZ;

    protected ServerboundMovePlayerPacket(double y, float yRot, float xRot, boolean onGround,
            boolean hasPos, boolean hasRot, @Nullable SectorVec3 exactPosition) {
        this.y = exactPosition == null ? y : exactPosition.y();
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

    /** Exact sector X/Z payload for every position-bearing packet. */
    @Nullable
    public SectorVec3 getExactPosition(double fallbackY) {
        return this.exactBlockX == null ? null : SectorVec3.fromBlockAndFraction(this.exactBlockX, this.exactSubX, fallbackY, this.exactBlockZ, this.exactSubZ);
    }

    /** Positionless updates retain the server's exact position, including its fractions. */
    @Nullable
    public SectorVec3 getExactPosition(SectorVec3 currentPosition) {
        return this.hasPos ? this.getExactPosition(this.getY(currentPosition.y())) : currentPosition;
    }

    public void handle(ServerGamePacketListener pHandler) {
        pHandler.handleMovePlayer(this);
    }

    public double getY(double pDefaultValue) {
        return this.hasPos ? this.y : pDefaultValue;
    }

    public boolean hasExactPosition() {
        return this.exactBlockX != null;
    }

    public Int1088 getExactBlockX() {
        return this.exactBlockX;
    }
    public double getExactSubX() {
        return this.exactSubX;
    }
    public Int1088 getExactBlockZ() {
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
        public Pos(SectorVec3 position, boolean onGround) {
            super(position.y(),
                    0.0F, 0.0F, onGround, true, false, position);
        }

        public static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf buf) {
            SectorVec3 position = buf.readSectorVec3();
            boolean onGround = buf.readUnsignedByte() != 0;
            return new Pos(position, onGround);
        }

        public void write(FriendlyByteBuf buf) {
            SectorVec3 position = this.getExactPosition(this.y);
            buf.writeSectorVec3(position);
            buf.writeByte(this.onGround ? 1 : 0);
        }
    }

    public static class PosRot extends ServerboundMovePlayerPacket {
        /** Passenger input uses local velocity components, not world coordinates. */
        public static PosRot vehicleInput(double velocityX, double velocityZ, float yRot, float xRot, boolean onGround) {
            if (!Double.isFinite(velocityX) || !Double.isFinite(velocityZ)) {
                throw new IllegalArgumentException("Non-finite vehicle input");
            }
            double floorX = Math.floor(velocityX);
            double floorZ = Math.floor(velocityZ);
            SectorVec3 input = SectorVec3.fromBlockAndFraction(Int1088.fromDoubleFloorSaturated(velocityX),
                    velocityX - floorX, -999.0D, Int1088.fromDoubleFloorSaturated(velocityZ),
                    velocityZ - floorZ);
            return new PosRot(input, yRot, xRot, onGround);
        }

        public PosRot(SectorVec3 position, float yRot, float xRot, boolean onGround) {
            super(position.y(),
                    yRot, xRot, onGround, true, true, position);
        }

        public static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf buf) {
            SectorVec3 position = buf.readSectorVec3();
            float yRot = buf.readFloat();
            float xRot = buf.readFloat();
            boolean onGround = buf.readUnsignedByte() != 0;
            return new PosRot(position, yRot, xRot, onGround);
        }

        public void write(FriendlyByteBuf buf) {
            SectorVec3 position = this.getExactPosition(this.y);
            buf.writeSectorVec3(position);
            buf.writeFloat(this.yRot);
            buf.writeFloat(this.xRot);
            buf.writeByte(this.onGround ? 1 : 0);
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {
        public Rot(float pYRot, float pXRot, boolean pOnGround) {
            super(0.0D, pYRot, pXRot, pOnGround, false, true, null);
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
            super(0.0D, 0.0F, 0.0F, pOnGround, false, false, null);
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
