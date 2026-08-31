package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;

public class ClientboundMoveVehiclePacket implements Packet<ClientGamePacketListener> {
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final float yRot;
   private final float xRot;

   public ClientboundMoveVehiclePacket(Entity p_132584_) {
      this.exactPosition = p_132584_.sectorPosition();
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_132584_.getYRot();
      this.xRot = p_132584_.getXRot();
   }

   public ClientboundMoveVehiclePacket(FriendlyByteBuf p_179007_) {
      this.exactPosition = SectorVec3.fromBlockAndFraction(p_179007_.readLong(), p_179007_.readDouble(),
            p_179007_.readDouble(), p_179007_.readLong(), p_179007_.readDouble());
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_179007_.readFloat();
      this.xRot = p_179007_.readFloat();
   }

   public void write(FriendlyByteBuf p_132593_) {
      p_132593_.writeLong(this.exactPosition.blockX());
      p_132593_.writeDouble(this.exactPosition.subX());
      p_132593_.writeDouble(this.exactPosition.y());
      p_132593_.writeLong(this.exactPosition.blockZ());
      p_132593_.writeDouble(this.exactPosition.subZ());
      p_132593_.writeFloat(this.yRot);
      p_132593_.writeFloat(this.xRot);
   }

   public void handle(ClientGamePacketListener p_132590_) {
      p_132590_.handleMoveVehicle(this);
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

   public float getYRot() {
      return this.yRot;
   }

   public float getXRot() {
      return this.xRot;
   }
}