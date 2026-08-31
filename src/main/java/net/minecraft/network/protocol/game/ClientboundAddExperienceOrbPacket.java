package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundAddExperienceOrbPacket implements Packet<ClientGamePacketListener> {
   private final int id;
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final int value;

   public ClientboundAddExperienceOrbPacket(ExperienceOrb p_131517_) {
      this.id = p_131517_.getId();
      this.exactPosition = p_131517_.sectorPosition();
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.value = p_131517_.getValue();
   }

   public ClientboundAddExperienceOrbPacket(FriendlyByteBuf p_178564_) {
      this.id = p_178564_.readVarInt();
      this.exactPosition = SectorVec3.fromBlockAndFraction(p_178564_.readLong(), p_178564_.readDouble(),
            p_178564_.readDouble(), p_178564_.readLong(), p_178564_.readDouble());
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.value = p_178564_.readShort();
   }

   public void write(FriendlyByteBuf p_131526_) {
      p_131526_.writeVarInt(this.id);
      p_131526_.writeLong(this.exactPosition.blockX());
      p_131526_.writeDouble(this.exactPosition.subX());
      p_131526_.writeDouble(this.exactPosition.y());
      p_131526_.writeLong(this.exactPosition.blockZ());
      p_131526_.writeDouble(this.exactPosition.subZ());
      p_131526_.writeShort(this.value);
   }

   public void handle(ClientGamePacketListener p_131523_) {
      p_131523_.handleAddExperienceOrb(this);
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

   public int getValue() {
      return this.value;
   }
}