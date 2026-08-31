package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundTeleportEntityPacket implements Packet<ClientGamePacketListener> {
   private final int id;
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final byte yRot;
   private final byte xRot;
   private final boolean onGround;

   public ClientboundTeleportEntityPacket(Entity p_133538_) {
      this.id = p_133538_.getId();
      this.exactPosition = p_133538_.sectorPosition();
      Vec3 vec3 = this.exactPosition.toApproximateVec3();
      this.x = vec3.x;
      this.y = vec3.y;
      this.z = vec3.z;
      this.yRot = (byte)((int)(p_133538_.getYRot() * 256.0F / 360.0F));
      this.xRot = (byte)((int)(p_133538_.getXRot() * 256.0F / 360.0F));
      this.onGround = p_133538_.isOnGround();
   }

   public ClientboundTeleportEntityPacket(FriendlyByteBuf p_179437_) {
      this.id = p_179437_.readVarInt();
      this.exactPosition = SectorVec3.fromBlockAndFraction(p_179437_.readLong(), p_179437_.readDouble(),
            p_179437_.readDouble(), p_179437_.readLong(), p_179437_.readDouble());
      Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_179437_.readByte();
      this.xRot = p_179437_.readByte();
      this.onGround = p_179437_.readBoolean();
   }

   public void write(FriendlyByteBuf p_133547_) {
      p_133547_.writeVarInt(this.id);
      SectorVec3 position = this.exactPosition;
      p_133547_.writeLong(position.blockX());
      p_133547_.writeDouble(position.subX());
      p_133547_.writeDouble(position.y());
      p_133547_.writeLong(position.blockZ());
      p_133547_.writeDouble(position.subZ());
      p_133547_.writeByte(this.yRot);
      p_133547_.writeByte(this.xRot);
      p_133547_.writeBoolean(this.onGround);
   }

   public void handle(ClientGamePacketListener p_133544_) {
      p_133544_.handleTeleportEntity(this);
   }

   public int getId() {
      return this.id;
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

   public boolean isOnGround() {
      return this.onGround;
   }
}