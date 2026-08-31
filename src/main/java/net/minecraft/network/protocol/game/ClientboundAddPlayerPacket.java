package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundAddPlayerPacket implements Packet<ClientGamePacketListener> {
   private final int entityId;
   private final UUID playerId;
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final byte yRot;
   private final byte xRot;

   public ClientboundAddPlayerPacket(Player p_131596_) {
      this.entityId = p_131596_.getId();
      this.playerId = p_131596_.getGameProfile().getId();
      this.exactPosition = p_131596_.sectorPosition();
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = (byte)((int)(p_131596_.getYRot() * 256.0F / 360.0F));
      this.xRot = (byte)((int)(p_131596_.getXRot() * 256.0F / 360.0F));
   }

   public ClientboundAddPlayerPacket(FriendlyByteBuf p_178570_) {
      this.entityId = p_178570_.readVarInt();
      this.playerId = p_178570_.readUUID();
      this.exactPosition = SectorVec3.fromBlockAndFraction(p_178570_.readLong(), p_178570_.readDouble(),
            p_178570_.readDouble(), p_178570_.readLong(), p_178570_.readDouble());
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_178570_.readByte();
      this.xRot = p_178570_.readByte();
   }

   public void write(FriendlyByteBuf p_131605_) {
      p_131605_.writeVarInt(this.entityId);
      p_131605_.writeUUID(this.playerId);
      p_131605_.writeLong(this.exactPosition.blockX());
      p_131605_.writeDouble(this.exactPosition.subX());
      p_131605_.writeDouble(this.exactPosition.y());
      p_131605_.writeLong(this.exactPosition.blockZ());
      p_131605_.writeDouble(this.exactPosition.subZ());
      p_131605_.writeByte(this.yRot);
      p_131605_.writeByte(this.xRot);
   }

   public void handle(ClientGamePacketListener p_131602_) {
      p_131602_.handleAddPlayer(this);
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