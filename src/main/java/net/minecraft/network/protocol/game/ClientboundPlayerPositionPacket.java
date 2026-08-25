package net.minecraft.network.protocol.game;

import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundPlayerPositionPacket implements Packet<ClientGamePacketListener> {
   private final double x;
   private final double y;
   private final double z;
   private final float yRot;
   private final float xRot;
   private final Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments;
   private final int id;
   private final boolean dismountVehicle;
   /** Absolute exact target, present only for the sector-aware integrated-server path. */
   @Nullable
   private final SectorVec3 exactPosition;

   public ClientboundPlayerPositionPacket(double x, double y, double z, float yRot, float xRot,
                                          Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments,
                                          int id, boolean dismountVehicle) {
      this(x, y, z, yRot, xRot, relativeArguments, id, dismountVehicle, null);
   }

   public ClientboundPlayerPositionPacket(SectorVec3 position, float yRot, float xRot,
                                          Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments,
                                          int id, boolean dismountVehicle) {
      this(position.toApproximateVec3().x, position.y(), position.toApproximateVec3().z,
            yRot, xRot, relativeArguments, id, dismountVehicle, position);
   }

   private ClientboundPlayerPositionPacket(double x, double y, double z, float yRot, float xRot,
                                           Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeArguments,
                                           int id, boolean dismountVehicle, @Nullable SectorVec3 exactPosition) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.yRot = yRot;
      this.xRot = xRot;
      this.relativeArguments = relativeArguments;
      this.id = id;
      this.dismountVehicle = dismountVehicle;
      this.exactPosition = exactPosition;
   }

   public ClientboundPlayerPositionPacket(FriendlyByteBuf buf) {
      long blockX = buf.readLong();
      double subX = buf.readDouble();
      double y = buf.readDouble();
      long blockZ = buf.readLong();
      double subZ = buf.readDouble();
      // Compatibility mirrors only; exactPosition is authoritative for sector players.
      this.x = (double)blockX + subX;
      this.y = y;
      this.z = (double)blockZ + subZ;
      this.exactPosition = SectorVec3.fromBlockAndFraction(blockX, subX, y, blockZ, subZ);
      this.yRot = buf.readFloat();
      this.xRot = buf.readFloat();
      this.relativeArguments = ClientboundPlayerPositionPacket.RelativeArgument.unpack(buf.readUnsignedByte());
      this.id = buf.readVarInt();
      this.dismountVehicle = buf.readBoolean();
   }

   @Nullable
   public SectorVec3 getExactPosition() {
      return this.exactPosition;
   }

   public void write(FriendlyByteBuf buf) {
      SectorVec3 position = this.exactPosition;
      buf.writeLong(position == null ? (long)Math.floor(this.x) : position.blockX());
      buf.writeDouble(position == null ? this.x - Math.floor(this.x) : position.subX());
      buf.writeDouble(this.y);
      buf.writeLong(position == null ? (long)Math.floor(this.z) : position.blockZ());
      buf.writeDouble(position == null ? this.z - Math.floor(this.z) : position.subZ());
      buf.writeFloat(this.yRot);
      buf.writeFloat(this.xRot);
      buf.writeByte(ClientboundPlayerPositionPacket.RelativeArgument.pack(this.relativeArguments));
      buf.writeVarInt(this.id);
      buf.writeBoolean(this.dismountVehicle);
   }

   public void handle(ClientGamePacketListener p_132817_) {
      p_132817_.handleMovePlayer(this);
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

      private boolean isSet(int p_132845_) {
         return (p_132845_ & this.getMask()) == this.getMask();
      }

      public static Set<ClientboundPlayerPositionPacket.RelativeArgument> unpack(int p_132841_) {
         Set<ClientboundPlayerPositionPacket.RelativeArgument> set = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);

         for(ClientboundPlayerPositionPacket.RelativeArgument clientboundplayerpositionpacket$relativeargument : values()) {
            if (clientboundplayerpositionpacket$relativeargument.isSet(p_132841_)) {
               set.add(clientboundplayerpositionpacket$relativeargument);
            }
         }

         return set;
      }

      public static int pack(Set<ClientboundPlayerPositionPacket.RelativeArgument> p_132843_) {
         int i = 0;

         for(ClientboundPlayerPositionPacket.RelativeArgument clientboundplayerpositionpacket$relativeargument : p_132843_) {
            i |= clientboundplayerpositionpacket$relativeargument.getMask();
         }

         return i;
      }
   }
}