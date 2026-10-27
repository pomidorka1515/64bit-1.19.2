package net.minecraft.network.protocol.game;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundPlayerPositionPacket implements Packet<ClientGamePacketListener> {
    private final float yRot;
    private final float xRot;
    private final Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments;
    private final int id;
    private final boolean dismountVehicle;
    /** Split-coordinate target carried by both local and network packets. */
    private final SectorVec3 exactPosition;

    public ClientboundPlayerPositionPacket(SectorVec3 position, float yRot, float xRot,
            Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments,
            int id, boolean dismountVehicle) {
        this.yRot = yRot;
        this.xRot = xRot;
        this.relativeArguments = relativeArguments;
        this.id = id;
        this.dismountVehicle = dismountVehicle;
        this.exactPosition = position;
    }

    public ClientboundPlayerPositionPacket(FriendlyByteBuf buf) {
        SectorVec3 position = buf.readSectorVec3();
        this.exactPosition = position;
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
        this.relativeArguments = ClientboundPlayerPositionPacket.RelativeArgument.unpack(buf.readUnsignedByte());
        this.id = buf.readVarInt();
        this.dismountVehicle = buf.readBoolean();
    }

    public SectorVec3 getExactPosition() {
        return this.exactPosition;
    }

    public void write(FriendlyByteBuf buf) {
        SectorVec3 position = this.exactPosition;
        buf.writeSectorVec3(position);
        buf.writeFloat(this.yRot);
        buf.writeFloat(this.xRot);
        buf.writeByte(ClientboundPlayerPositionPacket.RelativeArgument.pack(this.relativeArguments));
        buf.writeVarInt(this.id);
        buf.writeBoolean(this.dismountVehicle);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleMovePlayer(this);
    }

    public double getY() {
        return this.exactPosition.y();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getXRot() {
        return this.xRot;
    }

    public int getId() {
        return this.id;
    }

    public boolean requestDismountVehicle() {
        return this.dismountVehicle;
    }

    public Set<ClientboundPlayerPositionPacket.RelativeArgument> getRelativeArguments() {
        return this.relativeArguments;
    }

    public static enum RelativeArgument {
        X(0),
        Y(1),
        Z(2),
        Y_ROT(3),
        X_ROT(4);

        private final int bit;

        private RelativeArgument(int p_132838_) {
            this.bit = p_132838_;
        }

        private int getMask() {
            return 1 << this.bit;
        }

        private boolean isSet(int pFlags) {
            return (pFlags & this.getMask()) == this.getMask();
        }

        public static Set<ClientboundPlayerPositionPacket.RelativeArgument> unpack(int pFlags) {
            Set<ClientboundPlayerPositionPacket.RelativeArgument> set = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);

            for (ClientboundPlayerPositionPacket.RelativeArgument clientboundplayerpositionpacket$relativeargument : values()) {
                if (clientboundplayerpositionpacket$relativeargument.isSet(pFlags)) {
                    set.add(clientboundplayerpositionpacket$relativeargument);
                }
            }

            return set;
        }

        public static int pack(Set<ClientboundPlayerPositionPacket.RelativeArgument> pFlags) {
            int i = 0;

            for (ClientboundPlayerPositionPacket.RelativeArgument clientboundplayerpositionpacket$relativeargument : pFlags) {
                i |= clientboundplayerpositionpacket$relativeargument.getMask();
            }

            return i;
        }
    }
}
